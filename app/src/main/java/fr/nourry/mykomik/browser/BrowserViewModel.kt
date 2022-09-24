package fr.nourry.mykomik.browser

import androidx.lifecycle.*
import fr.nourry.mykomik.App
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.preference.*
import fr.nourry.mykomik.utils.deleteFile
import fr.nourry.mykomik.utils.getComicEntriesFromDir
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

sealed class BrowserViewModelState(val isInit: Boolean = false, val currentDir: File? = null) {
    class Init(val directoryPath: String, val lastComicPath: String, val prefCurrentPage: String) : BrowserViewModelState (
        isInit = false
    )

    class ComicLoading(dir:File) : BrowserViewModelState (
        isInit = true,
        currentDir = dir
    )

    data class ComicReady(val dir:File, val comics: List<ComicEntry>) : BrowserViewModelState(
        isInit = true,
        currentDir = dir
    )

    class Error(val errorMessage:String, isInit:Boolean): BrowserViewModelState (
        isInit = isInit
    )
}


class BrowserViewModel() : ViewModel() {
    private var currentDir:File? = null
    private var comicEntriesToShow: MutableList<ComicEntry> = mutableListOf()
    private var comicEntriesToDelete = mutableListOf<ComicEntry>()   // List of files that should not appear in 'comics' (it's a list of files that was asked to be delete)
    private var deletionJob: Any? = null                // Job to delete all the files in 'comicEntriesToDelete'

    private var bSkipReadComic = false

    private val state = MutableLiveData<BrowserViewModelState>()
    fun getState(): LiveData<BrowserViewModelState> = state
    private fun isInitialized(): Boolean = if (state.value != null) state.value!!.isInit else false

    private var currentDirFile = MutableLiveData<File>()

    var comicEntriesFromDAO: LiveData<List<ComicEntry>> = Transformations.switchMap(currentDirFile) { file ->
        Timber.d("Transformations.switchMap(currentDirFile):: file:$file")
        App.db.comicEntryDao().getComicEntriesByDirPath(file.absolutePath)
    }/*.distinctUntilChanged() */   // Important or else the livedata will send a changed signal even if nothing change...


    val TIME_BEFORE_DELETION = 4000 // in milliseconds


    fun errorPermissionDenied() {
        Timber.d("errorPermissionDenied")
        state.value = BrowserViewModelState.Error(
            "Permission denied: cannot read directory!",
            isInit = isInitialized()
        )
    }

    fun init(skipReadComic:Boolean) {
        Timber.d("init")
        val directoryPath = SharedPref.get(PREF_ROOT_DIR, "")
        val lastComicPath = SharedPref.get(PREF_LAST_COMIC_PATH, "")
        val prefCurrentPage = SharedPref.get(PREF_CURRENT_PAGE_LAST_COMIC, "0")
        state.value = BrowserViewModelState.Init(directoryPath!!, lastComicPath!!, prefCurrentPage!!)

        bSkipReadComic = skipReadComic
    }

    // Load informations about a directory (comics and directories list)
    fun loadComics(dir: File) {
        Timber.d("----- loadComics(" + dir.absolutePath + ") -----")
        Timber.v("  comicEntriesToDelete = $comicEntriesToDelete")

        currentDirFile.value = dir

        currentDir = dir
        setAppCurrentDir(dir)

        state.value = BrowserViewModelState.ComicLoading(dir)
    }

    // Prepare to delete files (or directory) and start a timer that will really delete those files
    fun prepareDeleteComicEntries(deleteList: List<ComicEntry>) {
        Timber.d("prepareDeleteComicEntries($deleteList)")
        // Clear the old 'deleteList' if not still empty
        if (comicEntriesToDelete.size > 0) {
            deleteComicEntries()
            comicEntriesToDelete.clear()
        }

        // Retrieve the list
        for (ComicEntry in deleteList) {
            comicEntriesToDelete.add(ComicEntry)
        }

        // Start a timer to effectively delete those files
        deletionJob = GlobalScope.launch(Dispatchers.Default) {
            delay(TIME_BEFORE_DELETION.toLong())
            deleteComicEntries()
        }

        // Refresh view
        loadComics(App.currentDir!!)
    }

    // Stop the timer that should delete the files in 'comicEntriesToDelete'
    fun undoDeleteComicEntries():Boolean {
        Timber.d("undoDeleteComicEntries !!")
        if(deletionJob != null) {
            (deletionJob as Job).cancel()
            deletionJob = null

            if (comicEntriesToDelete.size>0) {
                Timber.d("undoDeleteComicEntries :: comicEntriesToDelete.size>0")
                comicEntriesToDelete.clear()
                return true
            }
        }
        return false
    }

    // Delete the files in 'comicEntriesToDelete' (should be called by the timer 'deletionJob' or in 'prepareDeleteComicEntries()')
    private fun deleteComicEntries() {
        Timber.d("deleteComicEntries :: comicEntriesToDelete= $comicEntriesToDelete)")
        for (comicEntry in comicEntriesToDelete) {
            if (comicEntry.fromDAO) {
                Executors.newSingleThreadExecutor().execute {
                    Timber.d("  DELETE IN DATABASE...")
                    App.db.comicEntryDao().deleteComicEntry(comicEntry)
                    Timber.d("  END DELETE IN DATABASE...")
                }
            }

            deleteFile(comicEntry.file)
            ComicLoadingManager.deleteComicEntryInCache(comicEntry)
        }
        comicEntriesToDelete.clear()
    }

    fun setAppCurrentDir(dir:File) {
        App.currentDir = dir
    }

    fun setPrefLastComicPath(path: String) {
        SharedPref.set(PREF_LAST_COMIC_PATH, path)
    }
    fun setPrefRootDir(absolutePath: String) {
        SharedPref.set(PREF_ROOT_DIR, absolutePath)
    }

    fun updateComicEntriesFromDAO(comicEntriesFromDAO: List<ComicEntry>) {
        Timber.d("updateComicEntriesFromDAO")
        Timber.d("    comicEntriesFromDAO=${comicEntriesFromDAO}")

        val comicEntriesFromDisk = getComicEntriesFromDir(currentDir!!)
        Timber.w("comicEntriesFromDisk = $comicEntriesFromDisk")

        // Built a correct comicEntries list...
        comicEntriesToShow.clear()
        comicEntriesToShow = synchronizeDBWithDisk(comicEntriesFromDAO, comicEntriesFromDisk)

        state.value = BrowserViewModelState.ComicReady(currentDir!!, comicEntriesToShow)
    }

    private fun synchronizeDBWithDisk(comicEntriesFromDAO: List<ComicEntry>, comicEntriesFromDisk: List<ComicEntry>): MutableList<ComicEntry> {
        Timber.d("synchronizeDBWithDisk")
        Timber.d("   comicEntriesFromDAO=$comicEntriesFromDAO")
        Timber.d("   comicEntriesFromDisk=$comicEntriesFromDisk")
        val hashkeyToIgnore: MutableList<String> = mutableListOf()
        var result: MutableList<ComicEntry> = mutableListOf()
        var found: Boolean
        for (fe in comicEntriesFromDisk) {
//            Timber.v(" Looking for ${fe.dirPath}")
            found = false
            // Search in comicEntriesToDelete
            for (feToDelete in comicEntriesToDelete) {
                if (fe.hashkey == feToDelete.hashkey) {
//                    Timber.v("  -- IGNORED !")
                    hashkeyToIgnore.add(fe.hashkey)
                    found = true
                    break
                }
            }
            if (!found) {
                // Search in comicEntriesFromDAO
                for (feDAO in comicEntriesFromDAO) {
                    Timber.v("  -- ${fe.dirPath}")
                    if (fe.hashkey == feDAO.hashkey) {
                        Timber.v("      -- ${fe.hashkey} == ${feDAO.hashkey}")
                        feDAO.file = fe.file
                        feDAO.fromDAO = true
                        result.add(feDAO)
                        found = true
//                        Timber.v("  -- FOUND IN DAO !")
                        break
                    }
                }
            }
            if (!found) {
                fe.fromDAO = false
                result.add(fe)
//                Timber.v("  -- ADDING FROM DISK ${result.size} ${fe.name}")
            }
        }

        // Search in comicEntriesFromDAO if all files where found in the disk
        //   else, delete them in database and cache
        val comicEntriesToDelete: MutableList<ComicEntry> = mutableListOf()
        for (feDAO in comicEntriesFromDAO) {
            if (!feDAO.fromDAO && hashkeyToIgnore.indexOf(feDAO.hashkey)==-1) {
                // Not in the disk anymore, so delete it
                Timber.d("  Should be delete : ${feDAO.file.absolutePath}")
                comicEntriesToDelete.add(feDAO)
                ComicLoadingManager.deleteComicEntryInCache(feDAO)
            }
        }
        if (comicEntriesToDelete.isNotEmpty()) {
            Executors.newSingleThreadExecutor().execute {
                Timber.d("  DELETE ENTRIES IN DATABASE...")
                App.db.comicEntryDao().deleteComicEntries(*comicEntriesToDelete.map{it}.toTypedArray())
                Timber.d("  END DELETE ENTRIES IN DATABASE...")
            }
        }

        val result2: MutableList<ComicEntry> = mutableListOf()
        if (bSkipReadComic) {
            for (comic in result) {
                if (comic.fromDAO && comic.nbPages == (comic.currentPage+1)) {
                    Timber.v("  skip ${comic.name} (already read)")
                } else {
                    result2.add(comic)
                }
            }
            result = result2
        }

        Timber.d(" returns => $result")
        return result
    }
}
