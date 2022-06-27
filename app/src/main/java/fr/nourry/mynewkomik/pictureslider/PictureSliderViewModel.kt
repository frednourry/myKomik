package fr.nourry.mynewkomik.pictureslider

import androidx.lifecycle.*
import fr.nourry.mynewkomik.App
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import fr.nourry.mynewkomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mynewkomik.preference.SharedPref
import fr.nourry.mynewkomik.utils.getComicEntriesFromDir
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

    private var comicEntriesInCurrentDir: MutableList<ComicEntry> = mutableListOf()
    private var currentIndexInDir = -1
    private var currentDirFile = MutableLiveData<File>()
    var comicEntriesFromDAO: LiveData<List<ComicEntry>> = Transformations.switchMap(currentDirFile) { file ->
        Timber.d("Transformations.switchMap(currentDirFile):: file:$file")
        App.db.comicEntryDao().getOnlyFileComicEntriesByDirPath(file.absolutePath)
    }.distinctUntilChanged()

    fun initialize(comic: ComicEntry, pageToGo: Int/*, shouldUncompress:Boolean*/) {
        Timber.d("initialize(${comic.file.name}) pageToGo=$pageToGo"/* shouldUncompress=$shouldUncompress*/)
        Timber.d("   comic = $comic")

        ComicLoadingManager.getInstance().clearComicDir()

        currentComic = comic
        currentPage = pageToGo

        currentComic!!.currentPage = pageToGo

        currentDirFile.value = comic.file.parentFile

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

    // Change the current comic with a new one in the list 'comicEntriesInCurrentDir'
    fun changeCurrentComic(newComic:ComicEntry, numPage:Int){
        Timber.d("changeCurrentComic $newComic")

        currentIndexInDir = comicEntriesInCurrentDir.indexOf(newComic)
        Timber.d("  currentIndexInDir=$currentIndexInDir")
        if (currentIndexInDir < 0) {
            Timber.w("  newComic not in comicEntriesInCurrentDir!!")
        }

        initialize(newComic, 0 /*newComic.currentPage*/)
    }

    fun onSetCurrentPage(n:Int) {
        Timber.d("onSetCurrentPage($n)")
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

    fun updateComicEntriesFromDAO(comicEntriesFromDAO: List<ComicEntry>) {
        Timber.d("updateComicEntriesFromDAO")
        Timber.d("    comicEntriesFromDAO=${comicEntriesFromDAO}")

        val comicEntriesFromDisk = getComicEntriesFromDir(currentDirFile.value!!, true)
        Timber.w("    comicEntriesFromDisk = $comicEntriesFromDisk")

        // Built a correct comicEntries list...
        comicEntriesInCurrentDir.clear()
        comicEntriesInCurrentDir = synchronizeDBWithDisk(comicEntriesFromDAO, comicEntriesFromDisk)
    }

    private fun synchronizeDBWithDisk(comicEntriesFromDAO: List<ComicEntry>, comicEntriesFromDisk: List<ComicEntry>): MutableList<ComicEntry> {
        Timber.d("synchronizeDBWithDisk")
        Timber.d("   comicEntriesFromDAO=$comicEntriesFromDAO")
        Timber.d("   comicEntriesFromDisk=$comicEntriesFromDisk")
        val result: MutableList<ComicEntry> = mutableListOf()
        var found = false
        var index=0
        for (fe in comicEntriesFromDisk) {
//            Timber.v(" Looking for ${fe.dirPath}")
            found = false
            // Search in comicEntriesFromDAO
            for (feDAO in comicEntriesFromDAO) {
                Timber.v("  -- ${fe.dirPath}")
                if (fe.hashkey == feDAO.hashkey) {
                    Timber.v("      -- ${fe.hashkey} == ${feDAO.hashkey}")
                    feDAO.file = fe.file
                    feDAO.fromDAO = true
                    result.add(feDAO)
                    found = true
                    break
                }
            }
            if (!found) {
                fe.fromDAO = false
                result.add(fe)
            }
            if (fe.hashkey == currentComic!!.hashkey) {
                currentIndexInDir = index
                Timber.d("   currentIndexInDir=$currentIndexInDir")
            }
            index++
        }

        Timber.d(" returns => $result")
        return result
    }

    fun hasNextComic():Boolean {
        return currentIndexInDir<comicEntriesInCurrentDir.size-1
    }
    fun getNextComic():ComicEntry? {
        if (hasNextComic()) {
            return comicEntriesInCurrentDir[currentIndexInDir+1]
        }
        return currentComic
    }

    fun hasPreviousComic():Boolean {
        return currentIndexInDir>0
    }
    fun getPreviousComic():ComicEntry? {
        if (hasPreviousComic()) {
            return comicEntriesInCurrentDir[currentIndexInDir-1]
        }
        return currentComic
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