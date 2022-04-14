package fr.nourry.mynewkomik

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.nourry.mynewkomik.utils.deleteFile
import fr.nourry.mynewkomik.utils.getComicsFromDir
import kotlinx.coroutines.*
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
    private var comics = mutableListOf<Comic>()         // List of comics in the current directory
    private var filesToDelete = mutableListOf<File>()   // List of files that should not appear in 'comics' (it's a list of files that was asked to be delete)
    private var deletionJob: Any? = null                // Job to delete all the files in 'filesToDelete'

    private val state = MutableLiveData<BrowserViewModelState>()
    fun getState(): LiveData<BrowserViewModelState> = state
    fun isInitialize(): Boolean = if (state.value != null) state.value!!.isInit else false

    val TIME_BEFORE_DELETION = 4000 // in milliseconds

    fun errorPermissionDenied() {
        Timber.d("errorPermissionDenied")
        state.value = BrowserViewModelState.Error(
            "Permission denied: cannot read directory!",
            isInit = isInitialize()
        )
    }

    fun init() {
        Timber.d("init")
        state.value = BrowserViewModelState.Init()
    }

    fun loadComics(dir: File) {
        Timber.d("----- loadComics(" + dir.absolutePath + ") -----")
        Timber.v("  filesToDelete = $filesToDelete")
        state.value = BrowserViewModelState.ComicLoading(dir)

        val files = getComicsFromDir(dir)
        comics.clear()
        for (file in files) {
            // Add this file if it's not in the 'filesToDelete' (the files should be deleted...)
            if (filesToDelete.indexOf(file)>= 0) {
                Timber.v("   => ${file.name} SKIPPED")
            } else {
                Timber.v("   => ${file.name} ADDED")
                comics.add(Comic(file))
            }
        }

        state.value = BrowserViewModelState.ComicReady(dir, comics)
    }

    // Prepare to delete files (or directory) and start a timer that will really delete those files
    fun prepareDeleteFiles(deleteList: List<File>) {
        Timber.d("prepareDeleteFiles($deleteList)")
        // Clear the old 'deleteList' if not still empty
        if (filesToDelete.size > 0) {
            deleteFiles()
            filesToDelete.clear()
        }

        // Retreive the list
        for (file in deleteList) {
            filesToDelete.add(file)
        }

        // Start a timer to effectively delete those files
        deletionJob = GlobalScope.launch(Dispatchers.Default) {
            delay(TIME_BEFORE_DELETION.toLong())
            deleteFiles()
        }

        // Refresh view
        loadComics(App.currentDir!!)

    }

    // Stop the timer that should delete the files in 'filesToDelete'
    fun undoDeleteFiles():Boolean {
        Timber.d("undoDeleteFiles !!")
        if(deletionJob != null) {
            (deletionJob as Job).cancel()
            deletionJob = null

            if (filesToDelete.size>0) {
                Timber.d("undoDeleteFiles :: filesToDelete.size>0")
                filesToDelete.clear()
                return true
            }
        }
        return false
    }

    // Delete the files in 'filesToDelete' (should be called by the timer 'deletionJob' or in 'prepareDeleteFiles()')
    private fun deleteFiles() {
        Timber.d("deleteFiles :: filesToDelete= $filesToDelete)")
        for (file in filesToDelete) {
            // TODO delete all traces (thumbnails, datebase entry, etc...)

            deleteFile(file)
        }
        filesToDelete.clear()
    }
}