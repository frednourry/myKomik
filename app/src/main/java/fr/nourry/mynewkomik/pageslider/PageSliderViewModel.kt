package fr.nourry.mynewkomik.pageslider

import androidx.lifecycle.*
import fr.nourry.mynewkomik.App
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.loader.ComicLoadingFinishedListener
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import fr.nourry.mynewkomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mynewkomik.preference.PREF_LAST_COMIC_PATH
import fr.nourry.mynewkomik.preference.SharedPref
import fr.nourry.mynewkomik.utils.getComicEntriesFromDir
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors


sealed class  PageSliderViewModelState(
    val isInitialized: Boolean = false
) {
    class Init() : PageSliderViewModelState(
        isInitialized = false
    )
    data class Loading(val comic:ComicEntry, val currentItem:Int, val nbItem:Int) : PageSliderViewModelState(
        isInitialized = false
    )

    data class Ready(val comic:ComicEntry, val currentPage: Int, val shouldUpdateAdapters:Boolean) : PageSliderViewModelState(
        isInitialized = true
    )

    data class PageSelection(val comic:ComicEntry, val currentPage: Int) : PageSliderViewModelState(
        isInitialized = true
    )

    class Cleaned() : PageSliderViewModelState(
        isInitialized = false
    )
    class Error(val errorMessage:String) : PageSliderViewModelState(
    )
}

class PageSliderViewModel : ViewModel(), ComicLoadingProgressListener, ComicLoadingFinishedListener {
    private val state = MutableLiveData<PageSliderViewModelState>()
    var currentComic : ComicEntry? = null
    private var currentPage = 0
    private var nbExpectedPages = 0

    fun getState(): LiveData<PageSliderViewModelState> = state

    private var comicEntriesInCurrentDir: MutableList<ComicEntry> = mutableListOf()
    private var currentIndexInDir = -1
    private var currentDirFile = MutableLiveData<File>()
    var comicEntriesFromDAO: LiveData<List<ComicEntry>> = Transformations.switchMap(currentDirFile) { file ->
        Timber.d("Transformations.switchMap(currentDirFile):: file:$file")
        App.db.comicEntryDao().getOnlyFileComicEntriesByDirPath(file.absolutePath)
    }.distinctUntilChanged()

    fun getCurrentPage():Int {
//        Timber.w("   currentPage=$currentPage")
        return currentPage
    }

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
        state.value = PageSliderViewModelState.Loading(comic, 0, 0)
        nbExpectedPages = comic.nbPages
        ComicLoadingManager.getInstance().loadComicPages(comic, this, numPage, offset, this)

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

        setPrefLastComicPath(newComic.file.absolutePath)

        initialize(newComic, 0 /*newComic.currentPage*/)
    }

    fun onSetCurrentPage(n:Int) {
        Timber.d("onSetCurrentPage($n) comic = $currentComic")

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

        state.value = PageSliderViewModelState.Cleaned()
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

    override fun onRetrieved(comic:ComicEntry, currentIndex: Int, size: Int, path:String) {
        Timber.d("onRetrieved currentIndex=$currentIndex size=$size path=$path")
        state.value = PageSliderViewModelState.Loading(currentComic!!, currentIndex, size)
    }

    override fun onFinished(result: ComicLoadingResult, comic: ComicEntry) {
        Timber.d("onFinished result=$result comic=${comic.file} comic.nbPages=${comic.nbPages}")
        if (result == ComicLoadingResult.SUCCESS) {
            // Images were successfully load, so let's go
            state.value = PageSliderViewModelState.Ready(comic, currentPage, nbExpectedPages != comic.nbPages)

            // Update DB if needed
            if (nbExpectedPages != comic.nbPages) {
                // Update DB
                Executors.newSingleThreadExecutor().execute {
                    if (comic.fromDAO) {
                        Timber.d("  UPDATE in DAO")
                        App.db.comicEntryDao().updateComicEntry(comic)
                    } else {
                        Timber.d("  INSERT in DAO")
                        comic.fromDAO = true
                        comic.id = App.db.comicEntryDao().insertComicEntry(comic)
                        Timber.d("  INSERT in DAO :: id = ${comic.id}")
                    }
                }
            }

        } else {
            state.value = PageSliderViewModelState.Error("Error loading directory")
        }
    }

    fun showPageSelector(page:Int) {
        Timber.d("showPageSelector page=$page")
        state.value = PageSliderViewModelState.PageSelection(currentComic!!, page)
    }

    fun cancelPageSelector() {
        Timber.d("cancelPageSelector")
        state.value = PageSliderViewModelState.Ready(currentComic!!, currentPage, false)
    }

    fun onClickPageSelector(page:Int) {
        Timber.d("onClickPageSelector page=$page")
        state.value = PageSliderViewModelState.Ready(currentComic!!, page, false)
    }

    fun setPrefLastComicPath(path: String) {
        SharedPref.set(PREF_LAST_COMIC_PATH, path)
    }

}