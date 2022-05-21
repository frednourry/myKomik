package fr.nourry.mynewkomik.pictureslider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.nourry.mynewkomik.Comic
import fr.nourry.mynewkomik.ComicPicture
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import fr.nourry.mynewkomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mynewkomik.preference.SharedPref
import fr.nourry.mynewkomik.utils.getImageFilesFromDir
import timber.log.Timber
import java.io.File


sealed class  PictureSliderViewModelState(
    val isInitialized: Boolean = false
) {
    class Init() : PictureSliderViewModelState(
        isInitialized = false
    )
    data class Loading(/*val dir:File, */val currentItem:Int, val nbItem:Int) : PictureSliderViewModelState(
        isInitialized = false
    )

    data class Ready(val pictures: List<ComicPicture>, val currentPage: Int) : PictureSliderViewModelState(
        isInitialized = true
    )
    class Cleaned() : PictureSliderViewModelState(
        isInitialized = false
    )
    class Error(val errorMessage:String) : PictureSliderViewModelState(
    )
}

class PictureSliderViewModel : ViewModel(), ComicLoadingProgressListener {
    private var pictures = mutableListOf<ComicPicture>()

    private val state = MutableLiveData<PictureSliderViewModelState>()
    private var currentComic : Comic? = null
    private var currentPage = 0
    fun getState(): LiveData<PictureSliderViewModelState> = state

    fun initialize(comic: Comic, page: Int, shouldUncompress:Boolean) {
        Timber.d("initialize(${comic.file.name}) page=$page shouldUncompress=$shouldUncompress")

        currentComic = comic
        currentPage = page

        // Uncompress the comic
        if (shouldUncompress) {
            state.value = PictureSliderViewModelState.Loading(0, 0)
            ComicLoadingManager.getInstance().uncompressComic(comic, this)
        } else {
            loadPictures(ComicLoadingManager.getInstance().getPathUncompressedComic())
        }

//        state.value = dir?.let { PictureSliderViewModelState.Loading(it, 0, 0) }
        Timber.d("initialize:: waiting....")
    }

    fun setCurrentPage(n:Int) {
        currentPage = n
        SharedPref.set(PREF_CURRENT_PAGE_LAST_COMIC, n.toString())
    }

    private fun loadPictures(dir: File) {

        val files = getImageFilesFromDir(dir)
        Timber.d("loadPictures(${dir.absolutePath})::$files")
        pictures.clear()
        for (file in files) {
            pictures.add(ComicPicture(file))
        }
        state.value = PictureSliderViewModelState.Ready(pictures, currentPage)
    }


    fun clean() {
        Timber.d("clean")

        pictures.clear()

        // Delete files
        // ?

        // Clean ComicLoadingManager waiting list
        if (currentComic != null) {
            ComicLoadingManager.getInstance().stopUncompressComic(currentComic!!)
            Thread.sleep(100)
        }

        currentComic = null
        ComicLoadingManager.getInstance().clearComicDir()

        state.value = PictureSliderViewModelState.Cleaned()
    }

    override fun onProgress(currentIndex: Int, size: Int) {
        Timber.d("onProgress currentIndex=$currentIndex")
        state.value = PictureSliderViewModelState.Loading( currentIndex, size)
    }

    override fun onFinished(result: ComicLoadingResult, target:Any?, comic:Comic, path: File?) {
        Timber.d("onFinished result=$result comic=${comic.file.lastModified()} dir=$path")
        if (result == ComicLoadingResult.SUCCESS && path!=null && path.path!="") {
            loadPictures(path)
        } else {
            state.value = PictureSliderViewModelState.Error("Error loading directory")
        }
    }
}