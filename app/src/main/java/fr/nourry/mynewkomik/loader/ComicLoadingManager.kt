package fr.nourry.mynewkomik.loader

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.concatPath
import fr.nourry.mynewkomik.utils.deleteFile
import fr.nourry.mynewkomik.utils.getComicEntriesFromDir
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

enum class ComicLoadingResult {
    SUCCESS,
    ERROR
}

interface ComicLoadingProgressListener {
    fun onProgress(currentIndex:Int, size:Int, path:String, target:Any? = null)
    fun onFinished(result: ComicLoadingResult, comic:ComicEntry, path:File?, target:Any?)
}

data class PageTarget (val numPage:Int, var target:Any?) {
    override fun toString():String {
        return "PageTarget(numPage=$numPage, %s )".format(if (target==null) "null" else "...")
    }
}

abstract class ComicLoading {
    abstract val comic:ComicEntry
}
data class ComicLoadingCover(override val comic: ComicEntry, val listener:ComicLoadingProgressListener?, val target: Any?=null, val fileList:List<ComicEntry>?=null):ComicLoading()
data class ComicLoadingPages(override val comic: ComicEntry, val listener:ComicLoadingProgressListener, var pages: List<PageTarget>):ComicLoading()


class ComicLoadingManager private constructor() {
    private val PATH_COMIC_DIR = "current/"

    // Priority
    private var currentComicLoadingPages:ComicLoadingPages? = null    //  The ComicLoadingManager will firstly process this action
    private var waitingCoversList: MutableList<ComicLoadingCover> = ArrayList() // If there is currently no ComicEntryLoadingPages, the ComicLoadingManager will process this list

    private var isLoading: Boolean = false
    private var currentComicLoadingCover: ComicLoadingCover? = null
    private var currentWorkID: UUID? = null

    private lateinit var workManager:WorkManager
    private lateinit var context: Context
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var dirUncompressedComic: File

    private var cachePathDir:String = ""

    init {
        isLoading = false
    }

    companion object {
        @Volatile private var mInstance: ComicLoadingManager? = null

        fun getInstance(): ComicLoadingManager =
            mInstance ?: synchronized(this) {
                val newInstance = mInstance ?: ComicLoadingManager().also { mInstance = it }
                newInstance
            }

        private const val THUMBNAIL_WIDTH                   = 199
        private const val THUMBNAIL_HEIGHT                  = 218
        private const val THUMBNAIL_INNER_IMAGE_WIDTH       = 100
        private const val THUMBNAIL_INNER_IMAGE_HEIGHT      = 155
        private const val THUMBNAIL_FRAME_SIZE              = 5

        fun deleteComicEntryInCache(comic:ComicEntry) {
            val filePath = getInstance().getComicEntryThumbnailFilePath(comic)
            val f = File(filePath)
            if (filePath != "" && f.exists()) {
                deleteFile(f)
            }
        }
    }

    fun initialize(appContext: Context, cachePath: String) {
        context = appContext

        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork()

        cachePathDir = cachePath
        dirUncompressedComic = File(concatPath(cachePathDir,PATH_COMIC_DIR))
    }

    fun getDirUncompressedComic(): File {
        return dirUncompressedComic
    }

    fun setLivecycleOwner(lo:LifecycleOwner) {
        lifecycleOwner = lo

        // Clean the WorkManager ?
    }

    fun loadComicEntryCoverInImageView(comic:ComicEntry, listener:ComicLoadingProgressListener, target:Any) {
        if (comic.isDirectory) {
            loadComicDirectoryCoverInImageView(comic, listener, target)
        } else {
            loadComicCoverInImageView(comic, listener, target)
        }
    }

    // Find in the comic archive the first image and put it in the given ImageView
    private fun loadComicCoverInImageView(comic:ComicEntry, listener:ComicLoadingProgressListener, target:Any) {
        waitingCoversList.add(ComicLoadingCover(comic, listener, target))
        loadNext()
    }

    // Generate a cover with some of the first comic covers in the directory
    private fun loadComicDirectoryCoverInImageView(dirComic: ComicEntry, listener:ComicLoadingProgressListener, target:Any) {
        Timber.w("loadComicDirectoryCoverInImageView(${dirComic.file.absoluteFile})")
        if (dirComic.file.isDirectory) {
            val cacheFilePath = getComicEntryThumbnailFilePath(dirComic)
            val cacheFile = File(cacheFilePath)

            // Check if an image is in cache
            if (cacheFile.exists()) {
                // Use cache
                waitingCoversList.add(ComicLoadingCover(dirComic, listener, target, null))
                loadNext()
            } else {
                // Add some comics in the cache
                val nbComicsInThumbnail = GetImageDirWorker.MAX_COVER_IN_THUMBNAIL  // Get some comics to build the thumbnail
                val comicsList = getComicEntriesFromDir(dirComic.file)
                val fileList = comicsList.subList(0, Math.min(comicsList.size, nbComicsInThumbnail))
                for (i in fileList.indices) {
                    val f = fileList[i]
                    if (!f.isDirectory) {
                        waitingCoversList.add(ComicLoadingCover(f, null, null))
                    }
                }
                Timber.w("loadComicDirectoryCoverInImageView :: $fileList")

                if (fileList.isNotEmpty()) {
                    // Be sure to add this directory AFTER its comics to make sure there will be some images of this dir in the cache
                    waitingCoversList.add(ComicLoadingCover(dirComic, listener, target, fileList))
                    loadNext()
                }
            }
        }
    }

    fun loadComicPages(comic:ComicEntry, listener:ComicLoadingProgressListener, numPage:Int, offset:Int=1, target:Any? = null) {
        Timber.d("loadComicPages:: numPage=$numPage offset=$offset")
        if (currentComicLoadingPages!= null && currentComicLoadingPages!!.comic.hashkey == comic.hashkey) {
            // Same comic, so add/update the given pages
            Timber.d("loadComicPages:: same comic, so update")
            val size = currentComicLoadingPages!!.pages.size
            val pageList:MutableList<Int> = ArrayList() // List of the wanted page
            for (num in 0 until offset) {
                pageList.add(numPage+num)
            }

            for (i in 0 until size) {
                if (pageList.contains(currentComicLoadingPages!!.pages[i].numPage)) {
                    // Update target
                    currentComicLoadingPages!!.pages[i].target = target
                } else {
                    // Add this page
                    currentComicLoadingPages!!.pages = currentComicLoadingPages!!.pages.plusElement(PageTarget(numPage, target))
                }
            }
        } else {
            // New comic, so forget the last one
            Timber.d("loadComicPages:: new comic")
            val targetList:MutableList<PageTarget> = ArrayList()
            for (num in 0 until offset) {
                targetList.add(PageTarget(numPage+num, if (num==0) target else null))
            }
            Timber.d("loadComicPages:: targetList = $targetList")
            currentComicLoadingPages = ComicLoadingPages(comic, listener, targetList)
        }
        loadNext()
    }

    // Delete all the files in the directory where a comic is uncompressed
    fun clearComicDir() {
        clearFilesInDir(dirUncompressedComic)
    }

    // Stop all loading and clear the waiting list
    fun clean() {
        Timber.d("clean() !!!")

        // Stop currentJob
        workManager.cancelAllWork()

        // Clean list
        waitingCoversList.clear()
        isLoading = false
    }

    fun stopUncompressComic(comic: ComicEntry) {
        if (currentComicLoadingPages!=null &&  currentComicLoadingPages!!.comic == comic) {
            // Stop current work
            if (currentWorkID != null) {
                // Cancel current work
                workManager.cancelWorkById(currentWorkID!!)

                // Wait a little before start the next job
                Thread.sleep(100)
                isLoading = false
                currentComicLoadingCover = null
                currentWorkID = null
                loadNext()
            }
        }
    }

    private fun loadNext() {
        Timber.d("loadNext() isLoading=$isLoading currentComicEntryLoadingPages=${currentComicLoadingPages?.comic?.name} list.size=${waitingCoversList.size}")
        if (!isLoading) {
            if (currentComicLoadingPages != null) {
                isLoading = true
                startLoadingPages(currentComicLoadingPages!!)
            }
            else if (waitingCoversList.size > 0) {
                isLoading = true
                val comicLoading = waitingCoversList.removeAt(0)
                Timber.d("loadNext() loading ${comicLoading.comic.file.absoluteFile}")
                startLoadingCover(comicLoading)
            } else {
                isLoading = false
            }
        }
    }

    private fun startLoadingPages(comicLoading:ComicLoadingPages) {
        Timber.d("startLoadingPages(${comicLoading.comic.file.absoluteFile})")

        val comic = comicLoading.comic
        val pagesList = comicLoading.pages
        val newPagesList:MutableList<PageTarget> = ArrayList()
        val pagesNumberList:MutableList<Int> = ArrayList()

        // Check if all asked pages need to be uncompress
        for (pt in pagesList) {
            val cacheFilePath = getComicEntryPageFilePath(comic, pt.numPage)
            val cacheFile = File(cacheFilePath)
            if (cacheFile.exists()) {
                Timber.w("    File already exists, so skip it ! pt.target=${pt.target}")
                if (pt.target != null) {
                    comicLoading.listener.onProgress(pt.numPage, 0, cacheFilePath, pt.target)
                }
            } else {
                newPagesList.add(pt)
                pagesNumberList.add(pt.numPage)
            }
        }

        if (newPagesList.isNotEmpty()) {
            // Image not in cache, so uncompress the comic
            pagesNumberList.sort()
            val workData = workDataOf(
                GetPagesWorker.KEY_ARCHIVE_PATH to comic.file.absolutePath,
                GetPagesWorker.KEY_PAGES_LIST to pagesNumberList.joinToString(","),
                GetPagesWorker.KEY_PAGES_DESTINATION_PATH to getComicEntryPageFilePath(comic, 999)
            )

            val work: WorkRequest = OneTimeWorkRequestBuilder<GetPagesWorker>()
                .setInputData(workData)
                .build()

            currentWorkID = work.id
            currentComicLoadingPages = null
            workManager.enqueue(work)
            Timber.d("   WORK ID = ${work.id}")

            workManager.getWorkInfoByIdLiveData(work.id)
                .observe(lifecycleOwner) { workInfo ->
                    Timber.d("    observe(${workInfo.id} state=${workInfo.state}  progress=${workInfo.progress})")

                    if (workInfo != null) {
                        if (workInfo.state == WorkInfo.State.RUNNING) {
                            Timber.w("    RUNNING !!!")
                            val currentIndex = workInfo.progress.getInt(GetPagesWorker.KEY_CURRENT_INDEX, -1)
                            val nbPages = workInfo.progress.getInt(GetPagesWorker.KEY_NB_PAGES, -1)
                            val path = workInfo.progress.getString(GetPagesWorker.KEY_CURRENT_PATH)?:""
                            var target:Any? = null
                            if (currentIndex>=0) {
                                for (it in newPagesList) {
                                    if (it.numPage == currentIndex) {
                                        target = it.target
                                        break
                                    }
                                }
                                Timber.w("    RUNNING !!! currentIndex = $currentIndex")
                                Timber.w("    RUNNING !!! path = $path")
                                if (path!= "") {
                                    comicLoading.listener.onProgress(currentIndex, nbPages, path, target)
                                }
                            }
                        } else if (workInfo.state.isFinished) {
                            Timber.d(" loading :: completed SUCCEEDED="+(workInfo.state == WorkInfo.State.SUCCEEDED))
                            val outputData = workInfo.outputData
                            val imagePath = outputData.getString(GetPagesWorker.KEY_PAGES_DESTINATION_PATH)?:""
                            val nbPages = outputData.getInt(GetPagesWorker.KEY_NB_PAGES, 0)
                            Timber.d("   nbPages=$nbPages")
                            if (nbPages>0) {
                                comic.nbPages = nbPages
                            }

                            isLoading = false
                            currentWorkID = null

                            comicLoading.listener.onFinished(
                                if (workInfo.state == WorkInfo.State.SUCCEEDED) ComicLoadingResult.SUCCESS else ComicLoadingResult.ERROR,
                                comic,
                                dirUncompressedComic,
                                comicLoading.pages[0].target
                            )

                            loadNext()
                        }
                    }
                }

        } else {
//            var callbackResponse = getComicEntryPageFilePath(comic, currentComicLoadingPages!!.pages.get(0).numPage)
            comicLoading.listener.onFinished(ComicLoadingResult.SUCCESS, comic, dirUncompressedComic, comicLoading.pages[0].target)
            currentComicLoadingPages = null
            isLoading = false
            loadNext()
        }
    }

    private fun startLoadingCover(comicLoading: ComicLoadingCover) {
        val comic = comicLoading.comic
        currentComicLoadingCover = comicLoading
        var callbackResponse = ""

        Timber.d("startLoadingCover(${comicLoading.comic.file.absoluteFile})")

        val cacheFilePath = getComicEntryThumbnailFilePath(comic)
        val cacheFile = File(cacheFilePath)

        val work: WorkRequest? =
            if (cacheFile.exists()) {
                // Extract nothing, just return the file path
                // Don't initialize the workrequest
                Timber.d("Image in cache ! (hashkey = ${comic.hashkey})")
                callbackResponse = cacheFilePath
                null
            } else if (!comic.isDirectory) {
                // Check if an image is in cache
                val workData = workDataOf(
                    GetCoverWorker.KEY_ARCHIVE_PATH to comic.file.absolutePath,
                    GetCoverWorker.KEY_IMAGE_DESTINATION_PATH to cacheFilePath,
                    GetCoverWorker.KEY_THUMBNAIL_WIDTH to THUMBNAIL_WIDTH,
                    GetCoverWorker.KEY_THUMBNAIL_HEIGHT to THUMBNAIL_HEIGHT,
                    GetCoverWorker.KEY_THUMBNAIL_INNER_IMAGE_WIDTH to THUMBNAIL_INNER_IMAGE_WIDTH,
                    GetCoverWorker.KEY_THUMBNAIL_INNER_IMAGE_HEIGHT to THUMBNAIL_INNER_IMAGE_HEIGHT,
                    GetCoverWorker.KEY_THUMBNAIL_FRAME_SIZE to THUMBNAIL_FRAME_SIZE
                )

                // Image not in cache, so uncompress the comic
                OneTimeWorkRequestBuilder<GetCoverWorker>()
                    .setInputData(workData)
                    .build()
            } else {
                if (comicLoading.fileList != null) {
                    val pathList = mutableListOf("","","","","")
                    // For each file in the list
                    var cpt = 0
                    for (c in comicLoading.fileList) {
                        val tempCachePath = getComicEntryThumbnailFilePath(c)
                        val tempCacheFile = File(tempCachePath)
                        // Check if this image exists
                        if (tempCacheFile.exists()) {
                            pathList[cpt] = tempCachePath
                            cpt++
                        }
                    }

                    // TODO rewrite this when i will know how to add params in a work data...
                    val workData = workDataOf(GetImageDirWorker.KEY_DESTINATION_DIRECTORY_PATH to cacheFilePath,
                        GetImageDirWorker.KEY_THUMBNAIL_WIDTH to THUMBNAIL_WIDTH,
                        GetImageDirWorker.KEY_THUMBNAIL_HEIGHT to THUMBNAIL_HEIGHT,
                        GetImageDirWorker.KEY_COMIC_PATH_0 to pathList[0],
                        GetImageDirWorker.KEY_COMIC_PATH_1 to pathList[1],
                        GetImageDirWorker.KEY_COMIC_PATH_2 to pathList[2],
                        GetImageDirWorker.KEY_COMIC_PATH_3 to pathList[3],
                        GetImageDirWorker.KEY_COMIC_PATH_4 to pathList[4])
                    OneTimeWorkRequestBuilder<GetImageDirWorker>()
                        .setInputData(workData)
                        .build()

                } else {
                    // Don't initialize the workrequest
                    callbackResponse = cacheFilePath
                    null
                }
            }

        // Start the Worker, if any
        if (work != null) {
            currentWorkID = work.id
            workManager.enqueue(work)
            Timber.d("   WORK ID = ${work.id}")
            workManager.getWorkInfoByIdLiveData(work.id)
                .observe(lifecycleOwner) { workInfo ->
                    Timber.d("    observe(${workInfo.id} state=${workInfo.state}  progress=${workInfo.progress})")

                    if (workInfo != null) {
                        if (workInfo.state == WorkInfo.State.RUNNING) {
                            val currentIndex = workInfo.progress.getInt("currentIndex", 0)
                            val size = workInfo.progress.getInt("size", 0)
                            if (size != 0) {
                                Timber.i(" loading :: $currentIndex/$size")
                                if (comicLoading.listener != null) {
                                    comicLoading.listener.onProgress(currentIndex, size, "", null)
                                }
                            }
                        } else if (workInfo.state.isFinished) {
                            Timber.d(" loading :: completed SUCCEEDED="+(workInfo.state == WorkInfo.State.SUCCEEDED))
                            val outputData = workInfo.outputData
                            val nbPages = outputData.getInt(GetCoverWorker.KEY_NB_PAGES, 0)
                            val imagePath = outputData.getString(GetCoverWorker.KEY_IMAGE_DESTINATION_PATH)?:""
                            comicLoading.comic.nbPages = nbPages

                            isLoading = false
                            currentComicLoadingCover = null
                            currentWorkID = null

                            if (comicLoading.listener != null) {
                                comicLoading.listener.onFinished(
                                    if (workInfo.state == WorkInfo.State.SUCCEEDED) ComicLoadingResult.SUCCESS else ComicLoadingResult.ERROR,
                                    comic,
                                    File(imagePath),
                                    comicLoading.target
                                )
                            }

                            loadNext()
                        }
                    }
                }
        } else {
            // Nothing to do, just return the file path
            comicLoading.listener?.onFinished(ComicLoadingResult.SUCCESS, currentComicLoadingCover!!.comic, File(callbackResponse), comicLoading.target)
            isLoading = false
            currentComicLoadingCover = null
            currentWorkID = null
            loadNext()
        }
    }

    private fun getComicEntryThumbnailFilePath(comic:ComicEntry): String {
        return concatPath(cachePathDir,comic.hashkey) + ".png"
    }

    private fun getComicEntryPageFilePath(comic:ComicEntry, pageNumber:Int):String {
        return concatPath(dirUncompressedComic.absolutePath, "${comic.hashkey}.%03d.jpg".format(pageNumber))
    }
}
