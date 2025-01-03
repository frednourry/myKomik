package fr.nourry.mykomik.loader

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.utils.*
import android.util.Log
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
    fun onFinished(result:ComicLoadingResult, comic:ComicEntry, message:String)
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
    private var cachePathCoverDir:String = ""
    private var cachePathPagesDir:String = ""

    init {
        isLoading = false
    }

    companion object {
        const val TAG = "ComicLoadingManager"

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

        val comicExtensionList = listOf("cbr", "cbz", "pdf", "rar", "zip", "cb7", "7z")
        val acceptedImageExtensionList = listOf("jpg", "gif", "png", "jpeg", "webp", "bmp")
        var imageExtensionFilterList:List<String> = emptyList()

        // Return true if and only if the extension is in 'acceptedImageExtensionList'
        fun isImageExtension(extension:String) : Boolean {
            return acceptedImageExtensionList.contains(extension)
        }

        // Return true if the given file path is from an image
        fun isFilePathAnImage(filename:String) : Boolean {
            val ext = File(filename).extension.lowercase()
            return isImageExtension(ext)
        }

        fun deleteComicEntryInCache(comic:ComicEntry) {
            val filePath = getInstance().getComicEntryThumbnailFilePath(comic)
            val f = File(filePath)
            if (filePath != "" && f.exists()) {
                deleteFile(f)
            }
        }

        init {
            // Built a list to filter the images by their extensions with 'acceptedImageExtensionList' => listOf("*.jpg", "*.webp")
            val mutList =  mutableListOf<String>()
            for (ext in acceptedImageExtensionList) {
                mutList.add("*.$ext")
            }
            imageExtensionFilterList = mutList
        }
    }

    fun initialize(appContext: Context, thumbnailDir:File, pageCacheDir: File) {
        Log.v(TAG,"initialize")
        context = appContext

        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork()

        cachePathDir = thumbnailDir.absolutePath
        cachePathCoverDir = cachePathDir+File.separator+"tempCover"
        cachePathPagesDir = cachePathDir+File.separator+"tempPages"
        dirUncompressedComic = pageCacheDir
        createDirectory(dirUncompressedComic.absolutePath)
        createDirectory(cachePathCoverDir)
        createDirectory(cachePathPagesDir)
    }

    fun setLivecycleOwner(lo:LifecycleOwner) {
        Log.v(TAG,"setLivecycleOwner")

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
                Log.v(TAG,"addInWaitingList:: file already in list, so replace it ! $pathToFind")    // NOTE : Should replace it because the old listener may not be still valid...

                waitingCoversList[cpt] = comicEntryLoading
                return
            }
            cpt++
        }

        waitingCoversList.add(comicEntryLoading)
    }


    fun loadComicEntryCover(comic:ComicEntry, listener:ComicLoadingProgressListener) {
        if (comic.isDirectory) {
            loadComicDirectoryCover(comic, listener)
        } else {
            loadComicCover(comic, listener)
        }
    }

    // Find in the comic archive the first image
    private fun loadComicCover(comic:ComicEntry, listener:ComicLoadingProgressListener) {
        addInWaitingCoverList(ComicEntryLoadingCover(comic, listener))
        loadNext()
    }

    // Generate a cover with some of the first comic covers in the directory
    private fun loadComicDirectoryCover(dirComic: ComicEntry, listener:ComicLoadingProgressListener) {
        Log.i(TAG,"loadComicDirectoryCover(${dirComic.path})")
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
                val comicsList = getComicEntriesFromUri(context, comicExtensionList, dirComic.uri)
                val fileList = comicsList.subList(0, Math.min(comicsList.size, nbComicsInThumbnail))
                for (i in fileList.indices) {
                    val f = fileList[i]
                    if (!f.isDirectory) {
                        addInWaitingCoverList(ComicEntryLoadingCover(f, null, null))
                    }
                }
//                    Log.i(TAG,"loadComicDirectoryCover :: $fileList")
                if (fileList.isNotEmpty()) {
                    // Be sure to add this directory AFTER its comics to make sure there will be some images of this dir in the cache
                    addInWaitingCoverList(ComicEntryLoadingCover(dirComic, listener, fileList))
                    loadNext()
                }
            }
        }
    }

    fun loadComicPages(comic:ComicEntry, listener: ComicLoadingProgressListener, numPage:Int, offset:Int, finishedListener:ComicLoadingFinishedListener?=null) {
        Log.d(TAG,"loadComicPages:: numPage=$numPage offset=$offset")
        if (currentComicLoadingPages!= null && currentComicLoadingPages!!.comic.hashkey == comic.hashkey) {
            // Same comic, so add/update the given pages
            Log.d(TAG,"loadComicPages:: same comic, so update")
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
            Log.d(TAG,"loadComicPages::  ${currentComicLoadingPages!!.pages}")
        } else {
            // New comic, so forget the last one
            Log.d(TAG,"loadComicPages:: new comic")
            val pageList:MutableList<TuplePageListenerList> = ArrayList()
            for (num in 0 until offset) {
                pageList.add(TuplePageListenerList(numPage+num, mutableListOf(listener)))
            }
            Log.d(TAG,"loadComicPages:: targetList = $pageList")
            currentComicLoadingPages = ComicEntryLoadingPages(comic, pageList, finishedListener)
        }
        loadNext()
    }

    // Delete all the files in the directory where a comic is uncompressed
    fun clearComicDir() {
        clearFilesInDir(dirUncompressedComic)
        clearFilesInDir(File(cachePathPagesDir))
    }

    // Stop all loading and clear the waiting list
    fun clean() {
        Log.d(TAG,"clean")

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
        Log.d(TAG,"loadNext() isLoading=$isLoading currentComicEntryLoadingPages=${currentComicLoadingPages?.comic?.name} list.size=${waitingCoversList.size}")
        if (!isLoading) {
            if (currentComicLoadingPages != null) {
                isLoading = true
                val comicLoading = currentComicLoadingPages!!
                currentComicLoadingPages = null
                startLoadingPages(comicLoading)
            }
            else if (waitingCoversList.size > 0) {
                Log.i(TAG,"waitingCoversList.size = "+waitingCoversList.size+ " $waitingCoversList")
                isLoading = true
                val comicLoading = waitingCoversList.removeAt(0)
                Log.d(TAG,"loadNext() loading ${comicLoading.comic.path}")
                startLoadingCover(comicLoading)
            } else {
                Log.i(TAG,"waitingCoversList.size = 0")
                isLoading = false

                // TODO if nothing left to load, check if we can load the rest of the comics !
            }
        }
    }

    private fun startLoadingPages(comicLoading:ComicEntryLoadingPages) {
        Log.d(TAG,"startLoadingPages(${comicLoading.comic.path})")

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
                Log.i(TAG,"    File already exists, so skip it ! (but inform listeners)")
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
                GetPagesWorker.KEY_PAGES_DESTINATION_PATH to getComicEntryPageFilePath(comic, 999),
                GetPagesWorker.KEY_PAGES_CONTENT_LIST_PATH to getComicEntryContentListPath(comic),
                GetPagesWorker.KEY_COMIC_EXTENSION to comic.extension
            )

            val work: WorkRequest = OneTimeWorkRequestBuilder<GetPagesWorker>()
                .setInputData(workData)
                .build()

            currentWorkID = work.id
//            currentComicLoadingPages = null
            workManager.enqueue(work)
            Log.d(TAG,"   WORK ID = ${work.id}")

            workManager.getWorkInfoByIdLiveData(work.id)
                .observe(lifecycleOwner) { workInfo ->
                    if (workInfo != null) {
                        Log.d(TAG,"  observe(${workInfo.id} state=${workInfo.state}  progress=${workInfo.progress})")
                        if (workInfo.state == WorkInfo.State.RUNNING) {
                            Log.i(TAG,"    RUNNING")
                            val currentIndex = workInfo.progress.getInt(GetPagesWorker.KEY_CURRENT_INDEX, -1)
                            val nbPages = workInfo.progress.getInt(GetPagesWorker.KEY_NB_PAGES, -1)
                            val path = workInfo.progress.getString(GetPagesWorker.KEY_CURRENT_PATH)?:""

                            if (currentIndex>=0) {
                                Log.i(TAG,"      currentIndex = $currentIndex nbPages = $nbPages")
                                Log.i(TAG,"      path = $path")
                                if (path!= "") {
                                    for (page in newPagesList) {
                                        if (currentIndex == page.numPage) {
                                            // Notify all the listeners
                                            page.listeners.forEach { it.onRetrieved(comic, currentIndex, nbPages, path) }

//                                            Log.w(TAG,"    REMOVING PAGE")
//                                            Log.w(TAG,"        size=${newPagesList.size} before")
                                            newPagesList.remove(page)
//                                            Log.w(TAG,"        size=${newPagesList.size} after")
                                            break
                                        }
                                    }
                                }
                            }
                        } else if (workInfo.state.isFinished) {
                            Log.d(TAG,"    loading :: completed SUCCEEDED="+(workInfo.state == WorkInfo.State.SUCCEEDED))
                            val outputData = workInfo.outputData
                            val nbPages = outputData.getInt(GetPagesWorker.KEY_NB_PAGES, 0)
                            val errorMessage = outputData.getString(GetPagesWorker.KEY_ERROR_MESSAGE) ?: ""
                            Log.d(TAG,"    nbPages=$nbPages")
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
                                comic,
                                errorMessage
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

        Log.d(TAG,"startLoadingCover(${comicLoading.comic.path})")

        val cacheFilePath = getComicEntryThumbnailFilePath(comic)
        val cacheFile = File(cacheFilePath)

        val work: WorkRequest? =
            if (cacheFile.exists()) {
                // Extract nothing, just return the file path
                // Don't initialize the workrequest
                Log.d(TAG,"Image in cache ! (hashkey = ${comic.hashkey})")
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
            Log.d(TAG,"   WORK ID = ${work.id}")
            workManager.getWorkInfoByIdLiveData(work.id)
                .observe(lifecycleOwner) { workInfo ->
                    if (workInfo != null) {
                        Log.d(TAG,"    observe(${workInfo.id} state=${workInfo.state}  progress=${workInfo.progress})")
                        if (workInfo.state == WorkInfo.State.RUNNING) {
/*                            val currentIndex = workInfo.progress.getInt("currentIndex", 0)
                            val size = workInfo.progress.getInt("size", 0)
                            if (size != 0) {
                                Log.i(TAG," startLoadingCover loading :: $currentIndex/$size")
                                if (comicLoading.listener != null) {
//                                    comicLoading.listener.onRetreived(comic, currentIndex, size, "")
                                }
                            }*/
                        } else if (workInfo.state.isFinished) {
                            Log.d(TAG," loading :: completed SUCCEEDED="+(workInfo.state == WorkInfo.State.SUCCEEDED))
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

    // Get the path of the temporary directory used to generate thumbnail cover (should contains zero or one file)
    fun getTempCoverDirectoryPath():String {
        return cachePathCoverDir
    }

    fun getComicEntryContentListPath(comic:ComicEntry):String {
        return concatPath(dirUncompressedComic.absolutePath, "${comic.hashkey}.list")
    }

    // Get the path of the temporary directory used to retrieve pages
    fun getTempPagesDirectoryPath():String {
        return cachePathPagesDir
    }
}
