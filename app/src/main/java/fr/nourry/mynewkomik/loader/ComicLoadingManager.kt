package fr.nourry.mynewkomik.loader

import android.content.Context
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.*
import com.bumptech.glide.Glide
import fr.nourry.mynewkomik.Comic
import fr.nourry.mynewkomik.utils.FileSignature
import fr.nourry.mynewkomik.utils.clearFilesInDir
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
    ALL_IN_DIR
}

interface ComicLoadingProgressListener {
    fun onProgress(currentIndex:Int, size:Int)
    fun onFinished(result: ComicLoadingResult, image:ImageView?, path:File?)
}

class ComicLoading(val comic: Comic, val type: ComicLoadingType, val listener:ComicLoadingProgressListener?, val imageView: ImageView?=null) {
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
    fun loadComicInImageView(comic:Comic, imageView:ImageView, listener:ComicLoadingProgressListener) {
        list.add(ComicLoading(comic, ComicLoadingType.FIRST_IMAGE,  listener, imageView))
        loadNext()
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
        if (!isLoading) {
            if (list.size > 0) {
                isLoading = true
                val comicLoading = list.removeAt(0)
                val ext = comicLoading.comic.file.extension.lowercase()
                if (ext == "cbz" || ext == "zip") {
                    startLoadingZip(comicLoading)
                } else {
                    loadNext()  // Next entry...
                }
            } else {
                isLoading = false
            }
        }
    }

    private fun startLoadingZip(comicLoading: ComicLoading) {
        isLoading = true

        val comic = comicLoading.comic
        currentComicLoading = comicLoading
        var callbackResponse = ""

        val work: WorkRequest? = when (comicLoading.type) {
            ComicLoadingType.FIRST_IMAGE -> {
                val signature = getFileSignature(comic.file.absolutePath)
                val cacheFilePath = getCacheFilePath(signature)
                val cacheFile = File(cacheFilePath)

                // Check if an image is in cache
                if (cacheFile.exists()) {
                    // Extract nothing, just use this image with Glide
                    Timber.d("Image in cache ! (signature = $signature)")
                    comicLoading.imageView?.let {
                        Glide.with(comicLoading.imageView.context)
                            .load(cacheFile)
                            .into(it)
                    }

                    // Don't initialize the workrequest
                    callbackResponse = cacheFilePath
                    null
                } else {
                    val workData = workDataOf(UnzipFirstImageWorker.KEY_ZIP_PATH to comic.file.absolutePath,
                                                UnzipFirstImageWorker.KEY_IMAGE_DESTINATION_PATH to cacheFilePath)

                    // Image not in cache, so un zip the comic
                    OneTimeWorkRequestBuilder<UnzipFirstImageWorker>()
                        .setInputData(workData)
                        .build()
                }
            }
            ComicLoadingType.ALL_IN_DIR -> {
                if (lastComicPathUncompressed != comic.file) {
                    lastComicPathUncompressed = comic.file
                    val workData = workDataOf(UnzipAllComicWorker.KEY_ZIP_PATH to comic.file.absolutePath,
                        UnzipAllComicWorker.KEY_DESTINATION_DIRECTORY_PATH to getCacheFilePath(FileSignature(PATH_COMIC_DIR)))
                    OneTimeWorkRequestBuilder<UnzipAllComicWorker>()
                        .setInputData(workData)
                        .build()
                } else {
                    // No need to uncompress, the comic was already uncompressed
                    Timber.i("startLoadingZip:: no need to uncompressed (already done)")
                    callbackResponse = getCacheFilePath(FileSignature(PATH_COMIC_DIR))
                    null
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
//                                Toast.makeText(App.getContext(), "Load $currentIndex/$size", Toast.LENGTH_SHORT).show()
                            }
                        } else if (workInfo.state.isFinished) {
                            Timber.d(" loading :: completed")

                            val outputData = workInfo.outputData
                            val imagePath =
                                outputData.getString(UnzipFirstImageWorker.KEY_IMAGE_DESTINATION_PATH)
                            if (imagePath != null) {
                                comicLoading.imageView?.let {
                                    Glide.with(it.context)
                                        .load(imagePath)
                                        .into(comicLoading.imageView)
                                }
                            }

                            if (currentComicLoading!!.listener != null) {
                                currentComicLoading!!.listener?.onFinished(
                                    if (workInfo.state == WorkInfo.State.SUCCEEDED) ComicLoadingResult.SUCCESS else ComicLoadingResult.ERROR,
                                    currentComicLoading!!.imageView, File(imagePath!!)
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
            // Nothing
            currentComicLoading!!.listener?.onFinished(ComicLoadingResult.SUCCESS, currentComicLoading!!.imageView, File(callbackResponse))
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