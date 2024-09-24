package fr.nourry.mykomik.browser

import android.net.Uri
import androidx.lifecycle.*
import fr.nourry.mykomik.App
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.preference.*
import fr.nourry.mykomik.utils.deleteComic
import fr.nourry.mykomik.utils.getComicEntriesFromUri
import kotlinx.coroutines.*
import android.util.Log
import java.util.concurrent.Executors

sealed class BrowserViewModelState(val currentTreeUri:Uri? = null, val isInit: Boolean = false) {
    class Init(private val uri: Uri?, val rootUriPath: Uri?, val lastComicUri: Uri?, val lastDirUri: Uri?, val prefCurrentPage: String) : BrowserViewModelState (
        currentTreeUri = uri,
        isInit = false
    )

    class ComicLoading(treeUri:Uri) : BrowserViewModelState (
        isInit = true,
        currentTreeUri = treeUri
    )

    data class ComicReady(val treeUri:Uri, val comics: List<ComicEntry>) : BrowserViewModelState(
        isInit = true,
        currentTreeUri = treeUri
    )

    class Error(val errorMessage:String, isInit:Boolean): BrowserViewModelState (
        isInit = isInit
    )
}


class BrowserViewModel : ViewModel() {

    companion object {
        const val TAG = "BrowserViewModel"
    }

    private var currentTreeUri:Uri? = null
    private var comicEntriesToShow: MutableList<ComicEntry> = mutableListOf()
    private var comicEntriesToDelete = mutableListOf<ComicEntry>()   // List of files that should not appear in 'comics' (it's a list of files that was asked to be delete)
    private var deletionJob: Any? = null                // Job to delete all the files in 'comicEntriesToDelete'

    private var bSkipReadComic = false

    private val state = MutableLiveData<BrowserViewModelState>()
    fun getState(): LiveData<BrowserViewModelState> = state
    private fun isInitialized(): Boolean = if (state.value != null) state.value!!.isInit else false

    private var currentUriTreeMutableLiveData = MutableLiveData<Uri>()

    var comicEntriesFromDAO: LiveData<List<ComicEntry>> = currentUriTreeMutableLiveData.switchMap { treeUri ->
//        Log.d(TAG,"Transformations.switchMap(currentDirFile):: treeUri=$treeUri")
        App.db.comicEntryDao().getComicEntriesByDirPath(treeUri.toString())
    }/*.distinctUntilChanged() */   // Important or else the livedata will send a changed signal even if nothing change...


    val TIME_BEFORE_DELETION = 5000 // in milliseconds


    fun errorPermissionDenied() {
        Log.d(TAG,"errorPermissionDenied")
        state.value = BrowserViewModelState.Error(
            "Permission denied: cannot read directory!",
            isInit = isInitialized()
        )
    }

    fun init(treeUri: Uri?, skipReadComic:Boolean=false) {
        Log.d(TAG,"init treeUri=$treeUri")
        val rootTreeUriString = SharedPref.getString(PREF_ROOT_TREE_URI, "")
        val lastComicUriString = SharedPref.getString(PREF_LAST_COMIC_URI, "")
        val lastDirUriString = SharedPref.getString(PREF_LAST_DIR_URI, "")
        val prefCurrentPage = SharedPref.getString(PREF_CURRENT_PAGE_LAST_COMIC, "0")
        Log.i(TAG,"rootTreeUriString=$rootTreeUriString lastComicUriString=$lastComicUriString lastDirUriString=$lastDirUriString prefCurrentPage=$prefCurrentPage")

        val rootTreeUri:Uri? = if (rootTreeUriString == "") null else Uri.parse(rootTreeUriString)
        val lastComicUri:Uri? = if (lastComicUriString == "") null else Uri.parse(lastComicUriString)
        val lastDirUri:Uri? = if (lastDirUriString == "") null else Uri.parse(lastDirUriString)

        Log.i(TAG,"treeUri=$treeUri rootTreeUri=$rootTreeUri lastComicUri=$lastComicUri lastDirUri=$lastDirUri prefCurrentPage=$prefCurrentPage")
        state.value = BrowserViewModelState.Init(treeUri, rootTreeUri, lastComicUri, lastDirUri, prefCurrentPage!!)

        bSkipReadComic = skipReadComic
    }

    // Load informations about a directory (comics and directories list)
    fun loadComics(treeUri: Uri) {
        Log.d(TAG,"----- loadComics($treeUri) -----")
        Log.v(TAG,"  comicEntriesToDelete = $comicEntriesToDelete")

        currentUriTreeMutableLiveData.value = treeUri

        currentTreeUri = treeUri

        setAppCurrentTreeUri(treeUri)

        state.value = BrowserViewModelState.ComicLoading(treeUri)
    }

    // Prepare to delete files (or directory) and start a timer that will really delete those files
    fun prepareDeleteComicEntries(deleteList: List<ComicEntry>) {
        Log.d(TAG,"prepareDeleteComicEntries($deleteList)")
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
        loadComics(App.currentTreeUri!!)
    }

    // Stop the timer that should delete the files in 'comicEntriesToDelete'
    fun undoDeleteComicEntries():Boolean {
        Log.d(TAG,"undoDeleteComicEntries !!")
        if(deletionJob != null) {
            (deletionJob as Job).cancel()
            deletionJob = null

            if (comicEntriesToDelete.size>0) {
                Log.d(TAG,"undoDeleteComicEntries :: comicEntriesToDelete.size>0")
                comicEntriesToDelete.clear()
                return true
            }
        }
        return false
    }

    // Delete the files in 'comicEntriesToDelete' (should be called by the timer 'deletionJob' or in 'prepareDeleteComicEntries()')
    private fun deleteComicEntries() {
        Log.d(TAG,"deleteComicEntries :: comicEntriesToDelete= $comicEntriesToDelete)")
        for (comicEntry in comicEntriesToDelete) {
            if (comicEntry.fromDAO) {
                Executors.newSingleThreadExecutor().execute {
                    Log.d(TAG,"  DELETE IN DATABASE...")
                    App.db.comicEntryDao().deleteComicEntry(comicEntry)
                    Log.d(TAG,"  END DELETE IN DATABASE...")
                }
            }

            deleteComic(App.appContext, comicEntry)
            ComicLoadingManager.deleteComicEntryInCache(comicEntry)
        }
        comicEntriesToDelete.clear()
    }

    fun setAppCurrentTreeUri(treeUri:Uri) {
        setPrefLastDirUri(treeUri)
        App.currentTreeUri = treeUri
    }

    fun setPrefLastComicUri(uri: Uri?) {
        if (uri == null)
            SharedPref.setString(PREF_LAST_COMIC_URI, "")
        else
            SharedPref.setString(PREF_LAST_COMIC_URI, uri.toString())
    }

    fun setPrefLastDirUri(dirUri: Uri?) {
        if (dirUri == null)
            SharedPref.setString(PREF_LAST_DIR_URI, "")
        else
            SharedPref.setString(PREF_LAST_DIR_URI, dirUri.toString())
    }

    fun setPrefRootTreeUri(treeUri: Uri) {
        SharedPref.setString(PREF_ROOT_TREE_URI, treeUri.toString())
    }

    fun updateComicEntriesFromDAO(comicEntriesFromDAO: List<ComicEntry>) {
        Log.d(TAG,"updateComicEntriesFromDAO")
//        Log.d(TAG,"    comicEntriesFromDAO=${comicEntriesFromDAO}")
//        Log.d(TAG,"    currentUriTreeMutableLiveData.value=${currentUriTreeMutableLiveData.value.toString()}")

        val comicEntriesFromDisk = getComicEntriesFromUri(App.appContext, ComicLoadingManager.comicExtensionList, currentTreeUri!!)
        Log.v(TAG,"comicEntriesFromDisk = $comicEntriesFromDisk")

        // Built a correct comicEntries list...
        comicEntriesToShow.clear()
        comicEntriesToShow = synchronizeDBWithDisk(comicEntriesFromDAO, comicEntriesFromDisk)

        state.value = BrowserViewModelState.ComicReady(currentTreeUri!!, comicEntriesToShow)
    }

    private fun synchronizeDBWithDisk(comicEntriesFromDAO: List<ComicEntry>, comicEntriesFromDisk: List<ComicEntry>): MutableList<ComicEntry> {
        Log.d(TAG,"synchronizeDBWithDisk")
//        Log.d(TAG,"   comicEntriesFromDAO=$comicEntriesFromDAO")
//        Log.d(TAG,"   comicEntriesFromDisk=$comicEntriesFromDisk")
        val hashkeyToIgnore: MutableList<String> = mutableListOf()
        var result: MutableList<ComicEntry> = mutableListOf()
        var found: Boolean
        for (fe in comicEntriesFromDisk) {
//            Log.v(TAG," Looking for ${fe.dirPath}")
            found = false
            // Search in comicEntriesToDelete
            for (feToDelete in comicEntriesToDelete) {
                if (fe.hashkey == feToDelete.hashkey) {
//                    Log.v(TAG,"  -- IGNORED !")
                    hashkeyToIgnore.add(fe.hashkey)
                    found = true
                    break
                }
            }
            if (!found) {
                // Search in comicEntriesFromDAO
                for (feDAO in comicEntriesFromDAO) {
//                    Log.v(TAG,"  -- ${fe.parentUriPath}")
                    if (fe.hashkey == feDAO.hashkey) {
//                        Log.v(TAG,"      -- ${fe.hashkey} == ${feDAO.hashkey}")
                        feDAO.uri = fe.uri
                        feDAO.fromDAO = true
                        result.add(feDAO)
                        found = true
//                        Log.v(TAG,"  -- FOUND IN DAO !")
                        break
                    }
                }
            }
            if (!found) {
                fe.fromDAO = false
                result.add(fe)
//                Log.v(TAG,"  -- ADDING FROM DISK ${result.size} ${fe.name}")
            }
        }

        // Search in comicEntriesFromDAO if all files where found in the disk
        //   else, delete them in database and cache
        val comicEntriesToDelete: MutableList<ComicEntry> = mutableListOf()
        for (feDAO in comicEntriesFromDAO) {
            if (!feDAO.fromDAO && hashkeyToIgnore.indexOf(feDAO.hashkey)==-1) {
                // Not in the disk anymore, so delete it
                Log.d(TAG,"  Should be delete : ${feDAO.uri}")
                comicEntriesToDelete.add(feDAO)
                ComicLoadingManager.deleteComicEntryInCache(feDAO)
            }
        }
        if (comicEntriesToDelete.isNotEmpty()) {
            Executors.newSingleThreadExecutor().execute {
                Log.d(TAG,"  DELETE ENTRIES IN DATABASE...")
                App.db.comicEntryDao().deleteComicEntries(*comicEntriesToDelete.map{it}.toTypedArray())
                Log.d(TAG,"  END DELETE ENTRIES IN DATABASE...")
            }
        }

        val result2: MutableList<ComicEntry> = mutableListOf()
        if (bSkipReadComic) {
            for (comic in result) {
                if (comic.fromDAO && comic.nbPages == (comic.currentPage+1)) {
                    Log.v(TAG,"  skip ${comic.name} (already read)")
                } else {
                    result2.add(comic)
                }
            }
            result = result2
        }

        Log.d(TAG," returns => $result")
        return result
    }
}
