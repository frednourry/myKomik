package fr.nourry.mynewkomik

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.nourry.mynewkomik.utils.getComicsFromDir
import timber.log.Timber
import java.io.File

sealed class BrowserViewModelState(
    val isInit: Boolean = false,
    val currentDir: File? = null

) {
    class Init : BrowserViewModelState (
        isInit = false
    )

    class ComicLoading(dir:File) : BrowserViewModelState (
        isInit = true,
        currentDir = dir
    )

    data class ComicReady(val dir:File, val comics: List<Comic>) : BrowserViewModelState(
        isInit = true,
        currentDir = dir
    )

    class Error(val errorMessage:String, isInit:Boolean): BrowserViewModelState (
        isInit = isInit
    )
}


class BrowserViewModel : ViewModel() {
    private var comics = mutableListOf<Comic>()

    private val state = MutableLiveData<BrowserViewModelState>()
    fun getState() : LiveData<BrowserViewModelState> = state
    fun isInitialize(): Boolean = if (state.value!= null) state.value!!.isInit else false

    fun errorPermissionDenied() {
        Timber.d("errorPermissionDenied")
        state.value = BrowserViewModelState.Error("Permission denied: cannot read directory!", isInit = isInitialize())
    }

    fun init() {
        Timber.d("init")
        state.value = BrowserViewModelState.Init()
    }

    fun loadComics(dir:File) {
        Timber.d("----- loadComics("+dir.absolutePath+") -----")
        state.value = BrowserViewModelState.ComicLoading(dir)

        val files = getComicsFromDir(dir)
        comics.clear()
        for (file in files) {
            comics.add(Comic(file))
        }

        state.value = BrowserViewModelState.ComicReady(dir, comics)
    }
}