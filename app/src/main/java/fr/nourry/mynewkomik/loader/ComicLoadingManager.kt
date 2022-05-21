package fr.nourry.mynewkomik.loader

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import fr.nourry.mynewkomik.Comic
import fr.nourry.mynewkomik.utils.FileSignature
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.getComicsFromDir
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

enum class ComicLoadingResult {
    SUCCESS,
    ERROR
}

enum class ComicLoadingType {
    FIRST_IMAGE,
    IMAGE_DIR,
    ALL_IN_DIR
}

interface ComicLoadingProgressListener {
    fun onProgress(currentIndex:Int, size:Int)
    fun onFinished(result: ComicLoadingResult, target:Any?, comic:Comic, path:File?)
}

class ComicLoading(val comic: Comic, val type: ComicLoadingType, val listener:ComicLoadingProgressListener?, val target: Any?=null, val fileList:List<File>?=null) {
}

class ComicLoadingManager private constructor() {
    private val PATH_COMIC_DIR = "current/"

    private var list: MutableList<ComicLoading> = ArrayList()
    private var isLoading: Boolean = false
    private var currentComicLoading: ComicLoading? = null
    private var currentWorkID: UUID? = null
    private var lastComicPathUncompressed: File? = null

    private lateinit var workManager:WorkManager
    private lateinit var context: Context
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var pathUncompressedComic: File

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
    }

    fun initialize(appContext: Context, cachePath: String) {
        context = appContext

        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork()

        cachePathDir = cachePath
        pathUncompressedComic = File("$cachePathDir/$PATH_COMIC_DIR")
    }

    fun getPathUncompressedComic(): File {
        return pathUncompressedComic
    }

    fun setLivecycleOwner(lo:LifecycleOwner) {
        lifecycleOwner = lo

        // Clean the WorkManager ?
    }

    // Find in the comic archive the first image and put it in the given ImageView
    fun loadComicInImageView(comic:Comic, target:Any, listener:ComicLoadingProgressListener) {
        list.add(ComicLoading(comic, ComicLoadingType.FIRST_IMAGE,  listener, target))
        loadNext()
    }


    fun loadComicDirectoryInImageView(dirComic:Comic, target:Any, listener:ComicLoadingProgressListener) {
        Timber.w("loadComicDirectoryInImageView(${dirComic.file.absoluteFile})")
        if (dirComic.file.isDirectory) {
            val signature = getFileSignature(dirComic.file.absolutePath)
            val cacheFilePath = getCacheFilePath(signature)
            val cacheFile = File(cacheFilePath)

            // Check if an image is in cache
            if (cacheFile.exists()) {
                // Use cache
                list.add(ComicLoading(dirComic, ComicLoadingType.IMAGE_DIR,  listener, target, null))
                loadNext()
            } else {
                // Add some comics in the cache
                val nb_comics_in_thumbnail = ImageDirWorker.MAX_COVER_IN_THUMBNAIL  // Get some comics to build the thumbnail
                val comicsList = getComicsFromDir(dirComic.file)
                val fileList = comicsList.subList(0, Math.min(comicsList.size, nb_comics_in_thumbnail))
                for (i in fileList.indices) {
                    val f = fileList[i]
                    if (f.isFile) {
                        list.add(ComicLoading(Comic(f), ComicLoadingType.FIRST_IMAGE, null, null))
                    }
                }
                Timber.w("loadComicDirectoryInImageView :: $fileList")

                if (fileList.isNotEmpty()) {
                    // Be sure to add this directory AFTER its comics to make sure there will be some images of this dir in the cache
                    list.add(ComicLoading(dirComic, ComicLoadingType.IMAGE_DIR,  listener, target, fileList))
                    loadNext()
                }
            }
        }
    }


    // Uncompress all the images of a comic in a directory
    fun uncompressComic(comic:Comic, listener:ComicLoadingProgressListener): File? {
        val dir = pathUncompressedComic
        Timber.d("uncompressComic:: in directory ${dir.absolutePath}")
        if (!dir.exists()) {
            Timber.d("uncompressComic:: directory doesn't exists, so create it (${dir.absolutePath})")
            if (!dir.mkdir()) {
                Timber.w("uncompressComic:: directory not created !!")
                return null
            }
        }
        if (!comic.file.exists()) {
            Timber.w("uncompressComic:: comic file doesn't exists !!")
            return null
        }
        list.add(ComicLoading(comic, ComicLoadingType.ALL_IN_DIR, listener))
        loadNext()

        return dir
    }

    // Delete all the files in the directory where a comic is uncompressed
    fun clearComicDir() {
        lastComicPathUncompressed?.let { clearFilesInDir(pathUncompressedComic) }
        lastComicPathUncompressed = null
    }

    // Stop all loading and clear the waiting list
    fun clean() {
        Timber.d("clean() !!!")

        // Stop currentJob
        workManager.cancelAllWork()

        // Clean list
        list.clear()
        isLoading = false
    }

    fun stopUncompressComic(comic: Comic) {
        if (currentComicLoading!=null && currentComicLoading!!.type == ComicLoadingType.ALL_IN_DIR && currentComicLoading!!.comic == comic) {
            // Stop current work
            if (currentWorkID != null) {
                // Cancel current work
                workManager.cancelWorkById(currentWorkID!!)

                // Wait a little before start the next job
                Thread.sleep(100)
                isLoading = false
                currentComicLoading = null
                currentWorkID = null
                loadNext()
            }
        }
    }

    private fun loadNext() {
        Timber.d("loadNext() isLoading=$isLoading list.size=${list.size}")
        if (!isLoading) {
            if (list.size > 0) {
                isLoading = true
                val comicLoading = list.removeAt(0)
                Timber.d("loadNext() loading ${comicLoading.comic.file.absoluteFile}")
                if (comicLoading.comic.file.isFile) {
                    startLoadingArchive(comicLoading)
                } else {
                    // Directory...
                    startLoadingArchive(comicLoading)
                }
            } else {
                isLoading = false
            }
        }
    }

    private fun startLoadingArchive(comicLoading: ComicLoading) {
        isLoading = true

        val comic = comicLoading.comic
        currentComicLoading = comicLoading
        var callbackResponse = ""

        Timber.d("startLoadingArchive(${comicLoading.comic.file.absoluteFile})")

        val work: WorkRequest? = when (comicLoading.type) {
            ComicLoadingType.FIRST_IMAGE -> {
                val signature = getFileSignature(comic.file.absolutePath)
                val cacheFilePath = getCacheFilePath(signature)
                val cacheFile = File(cacheFilePath)

                // Check if an image is in cache
                if (cacheFile.exists()) {
                    // Extract nothing, just return the file path
                    // Don't initialize the workrequest
                    Timber.d("Image in cache ! (signature = $signature)")
                    callbackResponse = cacheFilePath
                    null
                } else {
                    val workData = workDataOf(UncompressFirstImageWorker.KEY_ARCHIVE_PATH to comic.file.absolutePath,
                                                UncompressFirstImageWorker.KEY_IMAGE_DESTINATION_PATH to cacheFilePath,
                                                UncompressFirstImageWorker.KEY_THUMBNAIL_WIDTH to THUMBNAIL_WIDTH,
                                                UncompressFirstImageWorker.KEY_THUMBNAIL_HEIGHT to THUMBNAIL_HEIGHT,
                                                UncompressFirstImageWorker.KEY_THUMBNAIL_INNER_IMAGE_WIDTH to THUMBNAIL_INNER_IMAGE_WIDTH,
                                                UncompressFirstImageWorker.KEY_THUMBNAIL_INNER_IMAGE_HEIGHT to THUMBNAIL_INNER_IMAGE_HEIGHT,
                                                UncompressFirstImageWorker.KEY_THUMBNAIL_FRAME_SIZE to THUMBNAIL_FRAME_SIZE)

                    // Image not in cache, so uncompress the comic
                    OneTimeWorkRequestBuilder<UncompressFirstImageWorker>()
                        .setInputData(workData)
                        .build()
                }
            }
            ComicLoadingType.ALL_IN_DIR -> {
                if (lastComicPathUncompressed != comic.file) {
                    lastComicPathUncompressed = comic.file
                    val workData = workDataOf(UncompressAllComicWorker.KEY_ARCHIVE_PATH to comic.file.absolutePath,
                        UncompressAllComicWorker.KEY_DESTINATION_DIRECTORY_PATH to getCacheDirPath(FileSignature(PATH_COMIC_DIR)))
                    OneTimeWorkRequestBuilder<UncompressAllComicWorker>()
                        .setInputData(workData)
                        .build()
                } else {
                    // No need to uncompress, the comic was already uncompressed
                    Timber.i("startLoadingArchive:: no need to uncompressed (already done)")
                    callbackResponse = getCacheFilePath(FileSignature(PATH_COMIC_DIR))
                    null
                }
            }
            ComicLoadingType.IMAGE_DIR -> {
                val signature = getFileSignature(comic.file.absolutePath)
                val cacheFilePath = getCacheFilePath(signature)
                val cacheFile = File(cacheFilePath)

                // Check if an image is in cache
                if (cacheFile.exists()) {
                    // Extract nothing, just use this image with Glide
                    // Don't initialize the workrequest
                    Timber.d("Dir image in cache ! (signature = $signature)")
                    callbackResponse = cacheFilePath
                    null
                } else {
                    if (comicLoading.fileList != null) {
                        val pathList = mutableListOf("","","","","")
                        // For each file in the list
                        var cpt = 0
                        for (f in comicLoading.fileList) {
                            val tempSign = getFileSignature(f.absolutePath)
                            val tempCachePath = if (f.isFile) getCacheFilePath(tempSign) else getCacheDirPath(tempSign)
                            val tempCacheFile = File(tempCachePath)
                            // Check if this image exists
                            if (tempCacheFile.exists()) {
                                pathList[cpt] = tempCachePath
                                cpt++
                            }
                        }

                        // TODO rewrite this when i will know how to add a work data...
                        val workData = workDataOf(ImageDirWorker.KEY_DESTINATION_DIRECTORY_PATH to cacheFilePath,
                            ImageDirWorker.KEY_THUMBNAIL_WIDTH to THUMBNAIL_WIDTH,
                            ImageDirWorker.KEY_THUMBNAIL_HEIGHT to THUMBNAIL_HEIGHT,
                            ImageDirWorker.KEY_COMIC_PATH_0 to pathList[0],
                            ImageDirWorker.KEY_COMIC_PATH_1 to pathList[1],
                            ImageDirWorker.KEY_COMIC_PATH_2 to pathList[2],
                            ImageDirWorker.KEY_COMIC_PATH_3 to pathList[3],
                            ImageDirWorker.KEY_COMIC_PATH_4 to pathList[4])
                        OneTimeWorkRequestBuilder<ImageDirWorker>()
                            .setInputData(workData)
                            .build()

                    } else {
                        // Don't initialize the workrequest
                        callbackResponse = cacheFilePath
                        null
                    }
                }
            }
        }

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
                                if (currentComicLoading!!.listener != null) {
                                    currentComicLoading!!.listener?.onProgress(currentIndex, size)
                                }
                            }
                        } else if (workInfo.state.isFinished) {
                            Timber.d(" loading :: completed SUCCEEDED="+(workInfo.state == WorkInfo.State.SUCCEEDED))
                            val outputData = workInfo.outputData
                            val nbPages = outputData.getInt(UncompressFirstImageWorker.KEY_NB_PAGES, 0)
                            val imagePath = outputData.getString(UncompressFirstImageWorker.KEY_IMAGE_DESTINATION_PATH)?:""
                            if (currentComicLoading?.listener != null) {
                                currentComicLoading?.listener?.onFinished(
                                    if (workInfo.state == WorkInfo.State.SUCCEEDED) ComicLoadingResult.SUCCESS else ComicLoadingResult.ERROR,
                                    currentComicLoading?.target,
                                    currentComicLoading!!.comic,
                                    File(imagePath)
                                )
                            }

                            isLoading = false
                            currentComicLoading = null
                            currentWorkID = null
                            loadNext()
                        }
                    }
                }
        } else {
            // Nothing to do, just return the file path
            currentComicLoading!!.listener?.onFinished(ComicLoadingResult.SUCCESS, currentComicLoading!!.target, currentComicLoading!!.comic, File(callbackResponse))
            isLoading = false
            currentComicLoading = null
            currentWorkID = null
            loadNext()
        }
    }


    // Get the signature of an image, base on its path and its page number
    private fun getFileSignature(filePath:String, num_page:Int=0):FileSignature {
        return FileSignature.createFileSignature(filePath+"_"+num_page.toString())
    }

    private fun getCacheFilePath(signature:FileSignature): String {
        return cachePathDir + "/" + signature.hashCode + ".png"
    }

    private fun getCacheDirPath(signature:FileSignature): String {
        return cachePathDir + "/" + signature.hashCode
    }
}

/*
https://stackoverflow.com/questions/46627357/unzip-a-file-in-kotlin-script-kts
import java.io.File
import java.util.zip.ZipFile

ZipFile(zipFileName).use { zip ->
    zip.entries().asSequence().forEach { entry ->
        zip.getInputStream(entry).use { input ->
            File(entry.name).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
 */