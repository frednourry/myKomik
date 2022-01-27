package fr.nourry.mynewkomik.loader

import android.content.Context
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.*
import com.bumptech.glide.Glide
import fr.nourry.mynewkomik.Comic
import fr.nourry.mynewkomik.utils.FileSignature
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

enum class ComicLoadingResult {
    OK,
    ERROR
}

enum class ComicLoadingType {
    FIRST_IMAGE,
    ALL_IN_DIR
}

class ComicLoading(val comic: Comic, val type: ComicLoadingType, val callback: (result: String?) -> Unit, val imageView: ImageView?=null) {
}

class ComicLoadingManager private constructor() {
    private var list: MutableList<ComicLoading> = ArrayList()
    private var isLoading: Boolean = false
    private var currentComicLoading: ComicLoading? = null
    private var currentWorkID: UUID? = null

    private lateinit var workManager:WorkManager
    private lateinit var context: Context
    private lateinit var lifecycleOwner: LifecycleOwner

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
    }

    fun setLivecycleOwner(lo:LifecycleOwner) {
        lifecycleOwner = lo

        // Clean the WorkManager ?
    }

    // Find in the comic archive the first image and put it in the given ImageView
    fun loadComicInImageView(comic:Comic, imageView:ImageView, callback: (result: String?) -> Unit) {
        list.add(ComicLoading(comic, ComicLoadingType.FIRST_IMAGE, callback, imageView))
        loadNext();
    }

    // Uncompress all the images of a comic in a directory
    fun uncompressComic(comic:Comic, callback: (result: String?) -> Unit): File? {
        val dir = File(cachePathDir+"/current/")
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
        list.add(ComicLoading(comic, ComicLoadingType.ALL_IN_DIR, callback))
        loadNext();

        return dir
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
                    val workData = workDataOf(UnzipFirstImageWorker.Companion.KEY_ZIP_PATH to comic.file.absolutePath,
                                                UnzipFirstImageWorker.Companion.KEY_IMAGE_DESTINATION_PATH to cacheFilePath)

                    // Image not in cache, so un zip the comic
                    OneTimeWorkRequestBuilder<UnzipFirstImageWorker>()
                        .setInputData(workData)
                        .build()
                }
            }
            ComicLoadingType.ALL_IN_DIR -> {
                val workData = workDataOf(UnzipAllComicWorker.Companion.KEY_ZIP_PATH to comic.file.absolutePath,
                                                UnzipAllComicWorker.Companion.KEY_DESTINATION_DIRECTORY_PATH to getCacheFilePath(FileSignature("current/")))
                OneTimeWorkRequestBuilder<UnzipAllComicWorker>()
                    .setInputData(workData)
                    .build()
            }
        }

        if (work != null) {
            currentWorkID = work.id
            workManager.enqueue(work)
            Timber.d("   WORK ID = ${work.id}")
            workManager.getWorkInfoByIdLiveData(work.id)
                .observe(lifecycleOwner, Observer { workInfo ->
                    Timber.d("                                observe(${workInfo.id})")
                    if (workInfo != null) {
                        if (!workInfo.state.isFinished) {
                            Timber.d("================== startLoadingZip :: not yet finished $workInfo.state")
                        } else if (workInfo.state.isFinished) {
                            Timber.d("================== startLoadingZip :: finished")

                            val outputData = workInfo.outputData
                            val imagePath = outputData.getString(UnzipFirstImageWorker.Companion.KEY_IMAGE_DESTINATION_PATH)
                            if (imagePath != null) {
                                comicLoading.imageView?.let {
                                    Glide.with(it.context)
                                        .load(imagePath)
                                        .into(comicLoading.imageView)
                                }
                            }

                            comicLoading.callback.invoke(imagePath)
                            isLoading = false
                            currentComicLoading = null
                            currentWorkID = null
                            loadNext()
                        }
                    }
                })
        } else {
            // Nothing
            isLoading = false
            currentComicLoading = null
            currentWorkID = null
            comicLoading.callback.invoke(callbackResponse)
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