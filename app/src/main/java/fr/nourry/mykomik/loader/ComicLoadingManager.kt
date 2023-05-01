package fr.nourry.mykomik.loader

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.utils.*
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

enum class ComicLoadingResult {
    SUCCESS,
    ERROR
}

interface ComicLoadingProgressListener {
    fun onRetrieved(comic:ComicEntry, currentIndex:Int, size:Int, path:String)
}
interface ComicLoadingFinishedListener {
    fun onFinished(result:ComicLoadingResult, comic:ComicEntry)
}

data class TuplePageListenerList (val numPage:Int, var listeners:MutableList<ComicLoadingProgressListener>) {
    override fun toString():String {
        return "TuplePageListenerList(numPage=$numPage, nb listeners=%s )".format(listeners.size)
    }
}

abstract class ComicEntryLoading {
    abstract val comic:ComicEntry
}
data class ComicEntryLoadingCover(override val comic: ComicEntry, val listener:ComicLoadingProgressListener?, val fileList:List<ComicEntry>?=null):ComicEntryLoading() {
    override fun toString(): String {
        return comic.name
    }

}
data class ComicEntryLoadingPages(override val comic: ComicEntry, var pages: MutableList<TuplePageListenerList>, val finishListener:ComicLoadingFinishedListener?=null):ComicEntryLoading()


class ComicLoadingManager private constructor() {
    // Priority
    private var currentComicLoadingPages:ComicEntryLoadingPages? = null    //  The ComicLoadingManager will firstly process this action
    private var waitingCoversList: MutableList<ComicEntryLoadingCover> = ArrayList() // If there is currently no ComicEntryLoadingPages, the ComicLoadingManager will process this list

    private var isLoading: Boolean = false
    private var currentComicLoadingCover: ComicEntryLoadingCover? = null
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
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var mInstance: ComicLoadingManager? = null

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

    fun initialize(appContext: Context, thumbnailDir:File, pageCacheDir: File) {
        Timber.v("initialize")
        context = appContext

        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork()

        cachePathDir = thumbnailDir.absolutePath
        dirUncompressedComic = pageCacheDir
        createDirectory(dirUncompressedComic.absolutePath)
    }

    fun setLivecycleOwner(lo:LifecycleOwner) {
        Timber.v("setLivecycleOwner")

        // Clean the WorkManager
        clean()

        lifecycleOwner = lo
    }

    // Add a ComicEntryLoadingCover if not already present in waitingCoversList
    private fun addInWaitingCoverList(comicEntryLoading: ComicEntryLoadingCover) {

        val pathToFind = comicEntryLoading.comic.path
        var cpt=0
        while (cpt <waitingCoversList.size) {
            val entry = waitingCoversList[cpt]
            if (pathToFind == entry.comic.path) {
                Timber.v("addInWaitingList:: file already in list, so replace it ! $pathToFind")    // NOTE : Should replace it because the old listener may not be still valid...

                waitingCoversList[cpt] = comicEntryLoading
                return
            }
            cpt++
        }

        waitingCoversList.add(comicEntryLoading)
    }


    fun loadComicEntryCoverInImageView(comic:ComicEntry, listener:ComicLoadingProgressListener) {
        if (comic.isDirectory) {
            loadComicDirectoryCoverInImageView(comic, listener)
        } else {
            loadComicCoverInImageView(comic, listener)
        }
    }

    // Find in the comic archive the first image and put it in the given ImageView
    private fun loadComicCoverInImageView(comic:ComicEntry, listener:ComicLoadingProgressListener) {
        addInWaitingCoverList(ComicEntryLoadingCover(comic, listener))
        loadNext()
    }

    // Generate a cover with some of the first comic covers in the directory
    private fun loadComicDirectoryCoverInImageView(dirComic: ComicEntry, listener:ComicLoadingProgressListener) {
        Timber.i("loadComicDirectoryCoverInImageView(${dirComic.path})")
        if (dirComic.isDirectory) {
            val cacheFilePath = getComicEntryThumbnailFilePath(dirComic)
            val cacheFile = File(cacheFilePath)

            // Check if an image is in cache
            if (cacheFile.exists()) {
                // Use cache
                addInWaitingCoverList(ComicEntryLoadingCover(dirComic, listener, null))
                loadNext()
            } else {
                // Add some comics in the cache
                val nbComicsInThumbnail = GetImageDirWorker.MAX_COVER_IN_THUMBNAIL  // Get some comics to build the thumbnail
                val comicsList = getComicEntriesFromUri(context, dirComic.uri)
                val fileList = comicsList.subList(0, Math.min(comicsList.size, nbComicsInThumbnail))
                for (i in fileList.indices) {
                    val f = fileList[i]
                    if (!f.isDirectory) {
                        addInWaitingCoverList(ComicEntryLoadingCover(f, null, null))
                    }
                }
//                    Timber.i("loadComicDirectoryCoverInImageView :: $fileList")
                if (fileList.isNotEmpty()) {
                    // Be sure to add this directory AFTER its comics to make sure there will be some images of this dir in the cache
                    addInWaitingCoverList(ComicEntryLoadingCover(dirComic, listener, fileList))
                    loadNext()
                }
            }
        }
    }

    fun loadComicPages(comic:ComicEntry, listener: ComicLoadingProgressListener, numPage:Int, offset:Int, finishedListener:ComicLoadingFinishedListener?=null) {
        Timber.d("loadComicPages:: numPage=$numPage offset=$offset")
        if (currentComicLoadingPages!= null && currentComicLoadingPages!!.comic.hashkey == comic.hashkey) {
            // Same comic, so add/update the given pages
            Timber.d("loadComicPages:: same comic, so update")
            val pageList:MutableList<Int> = ArrayList() // List of the wanted pages
            for (num in 0 until offset) {
                pageList.add(numPage+num)
            }

            // Search in currentComicLoadingPages.pages[] if a couple (number, listener) in pageList is in
            //  if yes, do nothing
            //  if no, add it
            // NOTE: listener can only be present 1 time, so we have to delete all its precedents apparitions
            var found = false
            for (page in currentComicLoadingPages!!.pages) {
                if (page.numPage == numPage) {
                    // Add 'listener' if not already present
                    if (!page.listeners.contains(listener)) {
                        page.listeners.add(listener)
                    }
                    found = true
                } else {
                    // Check if listener is in page.listeners
                    if (page.listeners.contains(listener)) {
                        page.listeners.remove(listener)
                    }
                }
            }
            if (!found) {
                // Add this page
                currentComicLoadingPages!!.pages.add(TuplePageListenerList(numPage, mutableListOf(listener)))
            }
            Timber.d("loadComicPages::  ${currentComicLoadingPages!!.pages}")
        } else {
            // New comic, so forget the last one
            Timber.d("loadComicPages:: new comic")
            val pageList:MutableList<TuplePageListenerList> = ArrayList()
            for (num in 0 until offset) {
                pageList.add(TuplePageListenerList(numPage+num, mutableListOf(listener)))
            }
            Timber.d("loadComicPages:: targetList = $pageList")
            currentComicLoadingPages = ComicEntryLoadingPages(comic, pageList, finishedListener)
        }
        loadNext()
    }

    // Delete all the files in the directory where a comic is uncompressed
    fun clearComicDir() {
        clearFilesInDir(dirUncompressedComic)
    }

    // Stop all loading and clear the waiting list
    fun clean() {
        Timber.d("clean")

        // Stop currentJob
        workManager.cancelAllWork()

        // Clean waitingCoversList ?
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
                val comicLoading = currentComicLoadingPages!!
                currentComicLoadingPages = null
                startLoadingPages(comicLoading)
            }
            else if (waitingCoversList.size > 0) {
                Timber.i("waitingCoversList.size = "+waitingCoversList.size+ " $waitingCoversList")
                isLoading = true
                val comicLoading = waitingCoversList.removeAt(0)
                Timber.d("loadNext() loading ${comicLoading.comic.path}")
                startLoadingCover(comicLoading)
            } else {
                Timber.i("waitingCoversList.size = $waitingCoversList.size")
                isLoading = false

                // TODO if nothing left to load, check if we can load the rest of the comics !
            }
        }
    }

    private fun startLoadingPages(comicLoading:ComicEntryLoadingPages) {
        Timber.d("startLoadingPages(${comicLoading.comic.path})")

        val comic = comicLoading.comic
        val pagesList = comicLoading.pages
        val newPagesList:MutableList<TuplePageListenerList> = ArrayList()
        val pagesNumberList:MutableList<Int> = ArrayList()

        // Check if all asked pages really need to be uncompress
        for (pt in pagesList) {
            val cacheFilePath = getComicEntryPageFilePath(comic, pt.numPage)
            val cacheFile = File(cacheFilePath)
            if (cacheFile.exists()) {
                // The cache file already exists, so no need to proceed
                Timber.i("    File already exists, so skip it ! (but inform listeners)")
                pt.listeners.forEach{ it.onRetrieved(comic, pt.numPage, 0, cacheFilePath) }          // Send a onProgress to the listeners

            } else {
                // We need to unarchive this page.
                // Before adding this PageTarget to 'newPagesList', check if this target is already used for another page (only one target/view can want this image)
/*                for (pt2 in newPagesList) {
                    if (pt2.target == pt.target) {
                        // Erase pt2 target !
                        pt2.target = null
                        break
                    }
                }*/
                newPagesList.add(pt)
                pagesNumberList.add(pt.numPage)
            }
        }

        if (newPagesList.isNotEmpty()) {
            // Image not in cache, so uncompress the comic
            pagesNumberList.sort()
            val workData = workDataOf(
                GetPagesWorker.KEY_ARCHIVE_URI to comic.path,
                GetPagesWorker.KEY_PAGES_LIST to pagesNumberList.joinToString(","),
                GetPagesWorker.KEY_PAGES_DESTINATION_PATH to getComicEntryPageFilePath(comic, 999)
            )

            val work: WorkRequest = OneTimeWorkRequestBuilder<GetPagesWorker>()
                .setInputData(workData)
                .build()

            currentWorkID = work.id
//            currentComicLoadingPages = null
            workManager.enqueue(work)
            Timber.d("   WORK ID = ${work.id}")

            workManager.getWorkInfoByIdLiveData(work.id)
                .observe(lifecycleOwner) { workInfo ->
                    Timber.d("    observe(${workInfo.id} state=${workInfo.state}  progress=${workInfo.progress})")

                    if (workInfo != null) {
                        if (workInfo.state == WorkInfo.State.RUNNING) {
                            Timber.i("    RUNNING !!!")
                            val currentIndex = workInfo.progress.getInt(GetPagesWorker.KEY_CURRENT_INDEX, -1)
                            val nbPages = workInfo.progress.getInt(GetPagesWorker.KEY_NB_PAGES, -1)
                            val path = workInfo.progress.getString(GetPagesWorker.KEY_CURRENT_PATH)?:""

                            if (currentIndex>=0) {
                                Timber.i("    RUNNING !!! currentIndex = $currentIndex nbPages = $nbPages")
                                Timber.i("    RUNNING !!! path = $path")
                                if (path!= "") {
                                    for (page in newPagesList) {
                                        if (currentIndex == page.numPage) {
                                            // Notify all the listeners
                                            page.listeners.forEach { it.onRetrieved(comic, currentIndex, nbPages, path) }

//                                            Timber.w("    REMOVING PAGE")
//                                            Timber.w("        size=${newPagesList.size} before")
                                            newPagesList.remove(page)
//                                            Timber.w("        size=${newPagesList.size} after")
                                            break
                                        }
                                    }
                                }
                            }
                        } else if (workInfo.state.isFinished) {
                            Timber.d(" loading :: completed SUCCEEDED="+(workInfo.state == WorkInfo.State.SUCCEEDED))
                            val outputData = workInfo.outputData
                            val nbPages = outputData.getInt(GetPagesWorker.KEY_NB_PAGES, 0)
                            Timber.d("   nbPages=$nbPages")
                            if (nbPages>0) {
                                comic.nbPages = nbPages
                            }

                            isLoading = false
                            currentWorkID = null

                            // Before sending the onFinished event, send all remaining onProgress event (could have one last onProgress not sent, due to Worker mechanisms...)
                            for (pt in newPagesList) {
                                val pagePath = getComicEntryPageFilePath(comic, pt.numPage)
                                if (isFileExists(pagePath)) {
                                    pt.listeners.forEach {
                                        it.onRetrieved(comic, pt.numPage, nbPages, pagePath)
                                    }
                                }
                            }

                            // Send the onFinished event
                            comicLoading.finishListener?.onFinished(
                                if (workInfo.state == WorkInfo.State.SUCCEEDED) ComicLoadingResult.SUCCESS else ComicLoadingResult.ERROR,
                                comic
                            )

                            loadNext()
                        }
                    }
                }
        } else {
//            comicLoading.listener.onFinished(ComicLoadingResult.SUCCESS, comic, dirUncompressedComic)
            currentComicLoadingPages = null
            isLoading = false
            loadNext()
        }
    }

    private fun startLoadingCover(comicLoading: ComicEntryLoadingCover) {
        val comic = comicLoading.comic
        currentComicLoadingCover = comicLoading
        var callbackResponse = ""

        Timber.d("startLoadingCover(${comicLoading.comic.path})")

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
                    GetCoverWorker.KEY_ARCHIVE_URI to comic.path,
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
/*                            val currentIndex = workInfo.progress.getInt("currentIndex", 0)
                            val size = workInfo.progress.getInt("size", 0)
                            if (size != 0) {
                                Timber.i(" startLoadingCover loading :: $currentIndex/$size")
                                if (comicLoading.listener != null) {
//                                    comicLoading.listener.onRetreived(comic, currentIndex, size, "")
                                }
                            }*/
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
                                comicLoading.listener.onRetrieved(comic, 0, nbPages, if (workInfo.state == WorkInfo.State.SUCCEEDED) imagePath else "")
                            }

                            loadNext()
                        }
                    }
                }
        } else {
            // Nothing to do, just return the file path
            comicLoading.listener?.onRetrieved(comic, 0, currentComicLoadingCover!!.comic.nbPages, callbackResponse)
            isLoading = false
            currentComicLoadingCover = null
            currentWorkID = null
            loadNext()
        }
    }

    private fun getComicEntryThumbnailFilePath(comic:ComicEntry): String {
        return concatPath(cachePathDir,comic.hashkey) + ".png"
    }

    fun getComicEntryPageFilePath(comic:ComicEntry, pageNumber:Int):String {
        return concatPath(dirUncompressedComic.absolutePath, "${comic.hashkey}.%03d.jpg".format(pageNumber))
    }
}
