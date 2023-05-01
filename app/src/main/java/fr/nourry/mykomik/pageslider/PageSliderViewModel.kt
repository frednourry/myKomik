package fr.nourry.mykomik.pageslider

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.*
import fr.nourry.mykomik.App
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.loader.ComicLoadingFinishedListener
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.loader.ComicLoadingProgressListener
import fr.nourry.mykomik.loader.ComicLoadingResult
import fr.nourry.mykomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mykomik.preference.PREF_LAST_COMIC_URI
import fr.nourry.mykomik.preference.SharedPref
import fr.nourry.mykomik.utils.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors


sealed class  PageSliderViewModelState(
    val isInitialized: Boolean = false,
) {
    class Init : PageSliderViewModelState(
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

    class Cleaned : PageSliderViewModelState(
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
    private var currentUri = MutableLiveData<Uri>()
    var comicEntriesFromDAO: LiveData<List<ComicEntry>> = currentUri.switchMap { uri ->
//        Timber.d("Transformations.switchMap(currentUri):: uri:$uri")
        App.db.comicEntryDao().getOnlyFileComicEntriesByDirPath(uri.toString())
    }.distinctUntilChanged()

    fun getCurrentPage():Int {
//        Timber.w("   currentPage=$currentPage")
        return currentPage
    }

    fun initialize(comic: ComicEntry, pageToGo: Int/*, shouldUncompress:Boolean*/) {
        Timber.d("initialize(${comic.name}) pageToGo=$pageToGo"/* shouldUncompress=$shouldUncompress*/)
        Timber.d("   comic = $comic")

        ComicLoadingManager.getInstance().clearComicDir()

        currentComic = comic
        currentPage = pageToGo

        currentComic!!.currentPage = pageToGo

//        currentDirFile.value = comic.file.parentFile
        if (comic.uri != null) {
            currentUri.value = comic.uri!!
        } else {
            Timber.w("initialize:: comic.uri is null !")
        }

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

        setPrefLastComicPath(newComic.uri.toString())

        initialize(newComic, 0 /*newComic.currentPage*/)
        onSetCurrentPage(0, true)
    }

    fun onSetCurrentPage(n:Int, forceUpdateDAO:Boolean=false) {
        Timber.d("onSetCurrentPage($n) comic = $currentComic")

        if (!App.isGuestMode)
            SharedPref.set(PREF_CURRENT_PAGE_LAST_COMIC, n.toString())

        currentPage = n
        if (forceUpdateDAO || currentComic!!.currentPage != n) {
            currentComic!!.currentPage = n

            // Update DB if not in guest mode
            if (!App.isGuestMode) {
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

    fun updateComicEntriesFromDAO(context: Context, comicEntriesFromDAO: List<ComicEntry>) {
        Timber.d("updateComicEntriesFromDAO")
//        Timber.d("    comicEntriesFromDAO=${comicEntriesFromDAO}")

        val comicEntriesFromDisk = getComicEntriesFromUri(context, App.currentTreeUri!!, true)

//        Timber.w("    comicEntriesFromDisk (${comicEntriesFromDisk.size}) = $comicEntriesFromDisk")

        // Built a correct comicEntries list...
        comicEntriesInCurrentDir.clear()

        comicEntriesInCurrentDir = synchronizeDBWithDisk(comicEntriesFromDAO, comicEntriesFromDisk)
    }

    private fun synchronizeDBWithDisk(comicEntriesFromDAO: List<ComicEntry>, comicEntriesFromDisk: List<ComicEntry>): MutableList<ComicEntry> {
        Timber.d("synchronizeDBWithDisk")
        Timber.d("   comicEntriesFromDAO=$comicEntriesFromDAO")
        Timber.d("   comicEntriesFromDisk=$comicEntriesFromDisk")
        val result: MutableList<ComicEntry> = mutableListOf()
        var found: Boolean
        for ((index, fe) in comicEntriesFromDisk.withIndex()) {
//            Timber.v(" Looking for ${fe.dirPath}")
            found = false
            // Search in comicEntriesFromDAO
            for (feDAO in comicEntriesFromDAO) {
//                Timber.v("  -- ${fe.dirPath}")
                if (fe.hashkey == feDAO.hashkey) {
//                    Timber.v("      -- ${fe.hashkey} == ${feDAO.hashkey}")
                    feDAO.uri = fe.uri
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
//                Timber.d("   currentIndexInDir=$currentIndexInDir")
            }
        }

        Timber.d(" returns (${result.size}) => $result")
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
        Timber.d("onFinished result=$result comic=${comic.name} comic.nbPages=${comic.nbPages}")
        if (result == ComicLoadingResult.SUCCESS) {
            // Images were successfully load, so let's go
            state.value = PageSliderViewModelState.Ready(comic, currentPage, nbExpectedPages != comic.nbPages)

            // Update DB if needed (and not in Guest Mode)
            if (nbExpectedPages != comic.nbPages && !App.isGuestMode) {
                // Update DB
                Executors.newSingleThreadExecutor().execute {
                    if (comic.fromDAO) {
                        Timber.d("  UPDATE in DAO $comic")
                        App.db.comicEntryDao().updateComicEntry(comic)
                    } else {
                        Timber.d("  INSERT in DAO $comic")
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

    private fun setPrefLastComicPath(path: String) {
        if (!App.isGuestMode)
            SharedPref.set(PREF_LAST_COMIC_URI, path)
    }

    // Save a page in a file
    // NOTE: the page should already be in cache !
    @Throws(IOException::class)
    fun saveCurrentPageInPictureDirectory(comic: ComicEntry, numPage: Int):String {
        Timber.d("saveCurrentPageInPictureDirectory")
        val dirImageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val saveDirectory = File(concatPath(dirImageFile.absolutePath, App.appName))
        Timber.d("saveCurrentPageInPictureDirectory:: dirImageFile=$dirImageFile saveDirectory=$saveDirectory")

        if (!saveDirectory.exists()) {
/*            if (!saveDirectory.mkdir())
                Timber.d("saveCurrentPage: 1")
                // Exit
                return ""*/
            saveDirectory.mkdir()
            if (!saveDirectory.exists()) {
                Timber.d("saveCurrentPageInPictureDirectory: unable to create Picture directory")
                return ""
            }
        }

        // Get a path to save the image
        var imageToSavePath: String = concatPath(saveDirectory.absolutePath,  stripExtension(comic.name)+"_p"+(numPage+1)+".jpg")
        var outputFile = File(imageToSavePath)
        var tempCpt=0

        // Check if already exists (if so, change the name)
        while (outputFile.exists()) {
            tempCpt++
            imageToSavePath = concatPath(saveDirectory.absolutePath,  stripExtension(comic.name)+"_p"+(numPage+1)+"_"+tempCpt+".jpg")
            outputFile = File(imageToSavePath)
        }

        // Check if the source page is really in cache
        val currentCachePagePath = ComicLoadingManager.getInstance().getComicEntryPageFilePath(comic, numPage)
        val currentCachePageFile = File(currentCachePagePath)
        if (currentCachePageFile.exists()) {
            Timber.d("saveCurrentPage: 2")
            // Copy this file in 'imageToSavePath'
            currentCachePageFile.copyTo(outputFile)

            // Tell the MediaScanner that there is a new image
            MediaScannerConnection.scanFile(App.appContext, arrayOf<String>(outputFile.absolutePath),null, null)

            // Return the file name, minus the stupid part (ie something like that /storage/emulated/0/)
            return outputFile.absolutePath.substring(dirImageFile.absolutePath.lastIndexOf('/'))
        } else
            return ""
    }

}