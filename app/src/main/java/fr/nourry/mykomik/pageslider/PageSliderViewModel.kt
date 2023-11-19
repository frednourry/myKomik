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
import android.util.Log
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
    companion object {
        const val TAG = "PageSliderViewModel"
    }

    private val state = MutableLiveData<PageSliderViewModelState>()
    var currentComic : ComicEntry? = null
    private var currentPage = 0
    private var nbExpectedPages = 0
    private var zoomOption = DisplayOption.FULL
    private var zoomLocked = false

    fun getState(): LiveData<PageSliderViewModelState> = state

    private var comicEntriesInCurrentDir: MutableList<ComicEntry> = mutableListOf()
    private var currentIndexInDir = -1
    private var currentUri = MutableLiveData<Uri>()
    var comicEntriesFromDAO: LiveData<List<ComicEntry>> = currentUri.switchMap { uri ->
//        Log.d(TAG,"Transformations.switchMap(currentUri):: uri:$uri")
        App.db.comicEntryDao().getOnlyFileComicEntriesByDirPath(uri.toString())
    }.distinctUntilChanged()

    fun getCurrentPage():Int {
//        Log.w(TAG,"   currentPage=$currentPage")
        return currentPage
    }

    fun initialize(comic: ComicEntry, pageToGo: Int/*, shouldUncompress:Boolean*/) {
        Log.d(TAG,"initialize(${comic.name}) pageToGo=$pageToGo"/* shouldUncompress=$shouldUncompress*/)
        Log.d(TAG,"   comic = $comic")

        ComicLoadingManager.getInstance().clearComicDir()

        currentComic = comic
        currentPage = pageToGo

        currentComic!!.currentPage = pageToGo

//        currentDirFile.value = comic.file.parentFile
        if (comic.uri != null) {
            currentUri.value = comic.uri!!
        } else {
            Log.w(TAG,"initialize:: comic.uri is null !")
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

        Log.d(TAG,"initialize:: waiting....")
    }

    // Change the current comic with a new one in the list 'comicEntriesInCurrentDir'
    fun changeCurrentComic(newComic:ComicEntry, numPage:Int){
        Log.d(TAG,"changeCurrentComic $newComic")

        currentIndexInDir = comicEntriesInCurrentDir.indexOf(newComic)
        Log.d(TAG,"  currentIndexInDir=$currentIndexInDir")
        if (currentIndexInDir < 0) {
            Log.w(TAG,"  newComic not in comicEntriesInCurrentDir!!")
        }

        setPrefLastComicPath(newComic.uri.toString())

        initialize(newComic, 0 /*newComic.currentPage*/)
        onSetCurrentPage(0, true)
    }

    fun onSetCurrentPage(n:Int, forceUpdateDAO:Boolean=false) {
        Log.d(TAG,"onSetCurrentPage($n) comic = $currentComic")

        if (!App.isGuestMode && !App.isSimpleViewerMode)
            SharedPref.set(PREF_CURRENT_PAGE_LAST_COMIC, n.toString())

        currentPage = n
        if (forceUpdateDAO || currentComic!!.currentPage != n) {
            currentComic!!.currentPage = n

            // Update DB if not in guest mode
            if (!App.isGuestMode) {
                Executors.newSingleThreadExecutor().execute {
                    if (currentComic!!.fromDAO) {
                        Log.d(TAG,"  UPDATE in DAO")
                        App.db.comicEntryDao().updateComicEntry(currentComic!!)
                    } else {
                        Log.d(TAG,"  INSERT in DAO")
                        currentComic!!.fromDAO = true
                        currentComic!!.id = App.db.comicEntryDao().insertComicEntry(currentComic!!)
                        Log.d(TAG,"  INSERT in DAO :: id = ${currentComic!!.id}")
                    }
                }
            }
        }
    }

    fun clean() {
        Log.d(TAG,"clean")

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
        Log.d(TAG,"updateComicEntriesFromDAO")
//        Log.d(TAG,"    comicEntriesFromDAO=${comicEntriesFromDAO}")

        val comicEntriesFromDisk = if (App.currentTreeUri != null) {
                                        getComicEntriesFromUri(
                                            context,
                                            ComicLoadingManager.comicExtensionList,
                                            App.currentTreeUri!!
                                        )
                                    } else {
                                        emptyList()
                                    }

//        Log.w(TAG,"    comicEntriesFromDisk (${comicEntriesFromDisk.size}) = $comicEntriesFromDisk")

        // Built a correct comicEntries list...
        comicEntriesInCurrentDir.clear()

        if (!App.isSimpleViewerMode) {
            comicEntriesInCurrentDir = synchronizeDBWithDisk(comicEntriesFromDAO, comicEntriesFromDisk)
        }
    }

    private fun synchronizeDBWithDisk(comicEntriesFromDAO: List<ComicEntry>, comicEntriesFromDisk: List<ComicEntry>): MutableList<ComicEntry> {
        Log.d(TAG,"synchronizeDBWithDisk")
        Log.d(TAG,"   comicEntriesFromDAO=$comicEntriesFromDAO")
        Log.d(TAG,"   comicEntriesFromDisk=$comicEntriesFromDisk")
        val result: MutableList<ComicEntry> = mutableListOf()
        var found: Boolean
        for ((index, fe) in comicEntriesFromDisk.withIndex()) {
//            Log.v(TAG," Looking for ${fe.dirPath}")
            found = false
            // Search in comicEntriesFromDAO
            for (feDAO in comicEntriesFromDAO) {
//                Log.v(TAG,"  -- ${fe.dirPath}")
                if (fe.hashkey == feDAO.hashkey) {
//                    Log.v(TAG,"      -- ${fe.hashkey} == ${feDAO.hashkey}")
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
//                Log.d(TAG,"   currentIndexInDir=$currentIndexInDir")
            }
        }

        Log.d(TAG," returns (${result.size}) => $result")
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
        Log.d(TAG,"onRetrieved currentIndex=$currentIndex size=$size path=$path")
        state.value = PageSliderViewModelState.Loading(currentComic!!, currentIndex, size)
    }

    override fun onFinished(result: ComicLoadingResult, comic: ComicEntry, errorMessage:String) {
        Log.d(TAG,"onFinished result=$result comic=${comic.name} comic.nbPages=${comic.nbPages} errorMessage=$errorMessage")
        if (result == ComicLoadingResult.SUCCESS) {
            // Images were successfully load, so let's go
            state.value = PageSliderViewModelState.Ready(comic, currentPage, nbExpectedPages != comic.nbPages)

            // Update DB if needed (and not in Guest Mode or Simple Viewer Mode)
            if (nbExpectedPages != comic.nbPages && !App.isGuestMode && !App.isSimpleViewerMode) {
                // Update DB
                Executors.newSingleThreadExecutor().execute {
                    if (comic.fromDAO) {
                        Log.d(TAG,"  UPDATE in DAO $comic")
                        App.db.comicEntryDao().updateComicEntry(comic)
                    } else {
                        Log.d(TAG,"  INSERT in DAO $comic")
                        comic.fromDAO = true
                        comic.id = App.db.comicEntryDao().insertComicEntry(comic)
                        Log.d(TAG,"  INSERT in DAO :: id = ${comic.id}")
                    }
                }
            }

        } else {
            state.value = PageSliderViewModelState.Error(errorMessage)
        }
    }

    fun showPageSelector(page:Int) {
        Log.d(TAG,"showPageSelector page=$page")
        state.value = PageSliderViewModelState.PageSelection(currentComic!!, page)
    }

    fun cancelPageSelector() {
        Log.d(TAG,"cancelPageSelector")
        state.value = PageSliderViewModelState.Ready(currentComic!!, currentPage, false)
    }

    fun onClickPageSelector(page:Int) {
        Log.d(TAG,"onClickPageSelector page=$page")
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
        Log.d(TAG,"saveCurrentPageInPictureDirectory")
        val dirImageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val saveDirectory = File(concatPath(dirImageFile.absolutePath, App.appName))
        Log.d(TAG,"saveCurrentPageInPictureDirectory:: dirImageFile=$dirImageFile saveDirectory=$saveDirectory")

        if (!saveDirectory.exists()) {
/*            if (!saveDirectory.mkdir())
                Log.d(TAG,"saveCurrentPage: 1")
                // Exit
                return ""*/
            saveDirectory.mkdir()
            if (!saveDirectory.exists()) {
                Log.d(TAG,"saveCurrentPageInPictureDirectory: unable to create Picture directory")
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
            Log.d(TAG,"saveCurrentPage: 2")
            // Copy this file in 'imageToSavePath'
            currentCachePageFile.copyTo(outputFile)

            // Tell the MediaScanner that there is a new image
            MediaScannerConnection.scanFile(App.appContext, arrayOf<String>(outputFile.absolutePath),null, null)

            // Return the file name, minus the stupid part (ie something like that /storage/emulated/0/)
            return outputFile.absolutePath.substring(dirImageFile.absolutePath.lastIndexOf('/'))
        } else
            return ""
    }

    fun setDisplayOption(zo : DisplayOption, lock:Boolean=false) {
        Log.d(TAG,"setZoomOption($zo)")
        zoomOption = zo
        zoomLocked = lock
    }

    fun isZoomOptionLocked() = zoomLocked
}