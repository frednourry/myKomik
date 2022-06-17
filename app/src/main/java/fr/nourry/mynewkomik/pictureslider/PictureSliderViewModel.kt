package fr.nourry.mynewkomik.pictureslider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.nourry.mynewkomik.App
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import fr.nourry.mynewkomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mynewkomik.preference.SharedPref
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors


sealed class  PictureSliderViewModelState(
    val isInitialized: Boolean = false
) {
    class Init() : PictureSliderViewModelState(
        isInitialized = false
    )
    data class Loading(val comic:ComicEntry, val currentItem:Int, val nbItem:Int) : PictureSliderViewModelState(
        isInitialized = false
    )

    data class Ready(val comic:ComicEntry, val currentPage: Int) : PictureSliderViewModelState(
        isInitialized = true
    )
    class Cleaned() : PictureSliderViewModelState(
        isInitialized = false
    )
    class Error(val errorMessage:String) : PictureSliderViewModelState(
    )
}

class PictureSliderViewModel : ViewModel(), ComicLoadingProgressListener {
    private val state = MutableLiveData<PictureSliderViewModelState>()
    var currentComic : ComicEntry? = null
    private var currentPage = 0
    fun getState(): LiveData<PictureSliderViewModelState> = state

    fun initialize(comic: ComicEntry, pageToGo: Int/*, shouldUncompress:Boolean*/) {
        Timber.d("initialize(${comic.file.name}) pageToGo=$pageToGo"/* shouldUncompress=$shouldUncompress*/)
        Timber.d("   comic = $comic")

        ComicLoadingManager.getInstance().clearComicDir()

        currentComic = comic
        currentPage = pageToGo

        currentComic!!.currentPage = pageToGo

        // Determine the pages to ask
        val offset = 3
        var numPage = pageToGo-1
        if (numPage<0) numPage = 0
        if ((comic.nbPages>0) && (numPage>= comic.nbPages)) numPage = comic.nbPages-1


        // Uncompress the comic
        state.value = PictureSliderViewModelState.Loading(comic, 0, 0)
        ComicLoadingManager.getInstance().loadComicPages(comic, this, numPage, offset)

        Timber.d("initialize:: waiting....")
    }

    fun setCurrentPage(n:Int) {
        Timber.d("setCurrentPage($n)")
        Timber.d("   comic = $currentComic")

        SharedPref.set(PREF_CURRENT_PAGE_LAST_COMIC, n.toString())
        currentPage = n
        if (currentComic!!.currentPage != n) {
            currentComic!!.currentPage = n

            // Update DB
            Executors.newSingleThreadExecutor().execute {
                if (currentComic!!.fromDAO) {
                    Timber.d("  UPDATE in DAO")
                    App.db.comicEntryDao().updateComicEntry(currentComic!!)
                } else {
                    Timber.d("  INSERT in DAO")
                    currentComic!!.fromDAO = true
                    currentComic!!.id = App.db.comicEntryDao().insertComicEntry(currentComic!!)
                    Timber.d("  INSERT in DAO :: id = ${currentComic!!.id}")
                }
            }
        }
    }

/*    private fun loadPictures(dir: File) {
        val files = getImageFilesFromDir(dir)
        Timber.d("loadPictures(${dir.absolutePath})::$files")
        pictures.clear()
        for (file in files) {
            pictures.add(ComicPicture(file))
        }
    }
*/
    fun clean() {
        Timber.d("clean")

        // Clean ComicLoadingManager waiting list
        if (currentComic != null) {
            ComicLoadingManager.getInstance().stopUncompressComic(currentComic!!)
            Thread.sleep(100)
        }

        currentComic = null
        ComicLoadingManager.getInstance().clearComicDir()

        state.value = PictureSliderViewModelState.Cleaned()
    }

    override fun onProgress(currentIndex: Int, size: Int, path:String, target:Any?) {
        Timber.d("onProgress currentIndex=$currentIndex size=$size path=$path target=$target")
        state.value = PictureSliderViewModelState.Loading(currentComic!!, currentIndex, size)
    }

    override fun onFinished(result: ComicLoadingResult, comic: ComicEntry, file: File?, target:Any?) {
        Timber.d("onFinished result=$result comic=${comic.file} comic.nbPages=${comic.nbPages} file=$file")
        if ((result == ComicLoadingResult.SUCCESS) && (file != null) && (file.path != "")) {
            // Some images were successfully load, so let's go
            state.value = PictureSliderViewModelState.Ready(currentComic!!, currentPage)
        } else {
            state.value = PictureSliderViewModelState.Error("Error loading directory")
        }
    }
}