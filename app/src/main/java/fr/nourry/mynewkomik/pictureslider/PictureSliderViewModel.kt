package fr.nourry.mynewkomik.pictureslider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.nourry.mynewkomik.Comic
import fr.nourry.mynewkomik.ComicPicture
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.utils.getImageFilesFromDir
import timber.log.Timber
import java.io.File


sealed class  PictureSliderViewModelState(
    val isInitialized: Boolean = false
) {
    class Init() : PictureSliderViewModelState(
        isInitialized = false
    )
    data class Loading(val dir:File) : PictureSliderViewModelState(
        isInitialized = false
    )
    data class Ready(val pictures: List<ComicPicture>) : PictureSliderViewModelState(
        isInitialized = true
    )
    class Cleaned() : PictureSliderViewModelState(
        isInitialized = false
    )
    class Error(val errorMessage:String) : PictureSliderViewModelState(
    )
}

class PictureSliderViewModel : ViewModel() {
    private var pictures = mutableListOf<ComicPicture>()

    private val state = MutableLiveData<PictureSliderViewModelState>()
    private var currentComic : Comic? = null
    fun getState(): LiveData<PictureSliderViewModelState> = state

    fun initialize(comic: Comic) {
        Timber.d("initialize($comic.file.name)")
        currentComic = comic
        // Uncompress the comic
        val dir = ComicLoadingManager.getInstance().uncompressComic(comic, ::onPictureLoaded)

        state.value = dir?.let { PictureSliderViewModelState.Loading(it) }
        Timber.d("initialize:: waiting....")
    }

    // The picture are loaded
    private fun onPictureLoaded(dirPath:String?) {
        if (dirPath != null && dirPath != "") {
            val dir = File(dirPath)
            loadPictures(dir)
        } else {
            Timber.w("initialize:: dirPath=$dirPath")
            state.value = PictureSliderViewModelState.Error("Error loading directory")
        }
    }

    private fun loadPictures(dir: File) {
        val files = getImageFilesFromDir(dir)
        Timber.d("loadPictures($dir.file.name)::$files")
        pictures.clear()
        for (file in files) {
            pictures.add(ComicPicture(file))
        }

        state.value = PictureSliderViewModelState.Ready(pictures)
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

        state.value = PictureSliderViewModelState.Cleaned()
    }
}