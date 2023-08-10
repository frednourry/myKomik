package fr.nourry.mykomik.loader

import android.net.Uri
import android.os.Handler
import android.os.Looper
import fr.nourry.mykomik.App
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.utils.getComicEntriesFromUri
import fr.nourry.mykomik.utils.getComicFromUri
import fr.nourry.mykomik.utils.getDirectoryUrisFromUri
import timber.log.Timber

/**
 * To watch the user interactions and to tasks when the user do nothing
 */
class IdleController : ComicLoadingProgressListener {
    companion object {
        private const val defaultIdleDelay = 2000L        // in milliseconds

        private var mInstance: IdleController? = null

        fun getInstance(): IdleController =
            mInstance ?: synchronized(this) {
                val newInstance = mInstance ?: IdleController().also { mInstance = it }
                newInstance
            }
    }

    private var handler: Handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable {
        // Timeout, so do something here
        onIdle()
    }

    private var idleDelay:Long = 0
    private var isIdle = false
    private var loadCompleted = false                           // Is there some comic cover to load ?

    private var dirUriList = mutableListOf<Uri>()               // The list of directories to browse
    private var currentUri : Uri? = null                        // The current uri
    private var currentComicList = mutableListOf<ComicEntry>()  // The comic uri list in the "currentUri"


    fun initialize() {
        Timber.v("initialize")
        idleDelay = defaultIdleDelay
    }

    /**
     * Call this when the user asks something to this app
     */
    fun resetIdleTimer() {
        // Cancel the current task here?
        //   empty the comic list ?
        isIdle = false

        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, idleDelay)
    }

    fun reinit() {
        resetIdleTimer()

        loadCompleted = false
        dirUriList.clear()

    }


    private fun onIdle() {
        Timber.v("onIdle")
        isIdle = true

        if (App.rootTreeUri == null || App.currentTreeUri == null)
            return

        // If there is something to load
        if (!loadCompleted) {
            // Build a list of directories
            if (dirUriList.isEmpty()) {
                val uriList = getDirectoryUrisFromUri(App.appContext, App.rootTreeUri!!)
                if (uriList.isNotEmpty()) {
                    dirUriList = uriList as MutableList<Uri>
                }
                Timber.v("dirUriList = $dirUriList")
            }

            // Can we continue?
            if (!isIdle) return

            // Build the comic entry list
            if (currentUri == null) {
                // First time !
                currentUri = App.currentTreeUri
                dirUriList.remove(currentUri)

                val tempComicList = getComicEntriesFromUri(App.appContext, currentUri!!, true)
                if (tempComicList.isNotEmpty()) {
                    currentComicList = tempComicList as MutableList<ComicEntry>
                } else {
                    loadCompleted = true
                    Timber.v("  **** LOAD COMPLETED !! ****")
                    return
                }
                Timber.v("currentUri = $currentUri")
                Timber.v("currentComicList = $currentComicList")
            }

            // Can we continue?
            if (!isIdle) return

            proceedLoadNextComicNext()
        }
    }

    /**
     * Called when a comic cover thumbnail is generated
     */
    override fun onRetrieved(comic: ComicEntry, currentIndex: Int, size: Int, path: String) {
        Timber.v("onRetrieved comic=${comic.name} path=$path")

        // Can we continue?
        if (!isIdle) return

        proceedLoadNextComicNext()
    }

    private fun proceedLoadNextComicNext() {
        // If the current comic list is not empty, start loading the next cover
        if (currentComicList.isNotEmpty()) {
            val comic = currentComicList.removeAt(0)
            ComicLoadingManager.getInstance().loadComicEntryCover(comic, this)
        } else {
            // else change currentUri
            // Choose another directory (if any)
            if (dirUriList.isEmpty()) {
                loadCompleted = true
                Timber.v("  **** LOAD COMPLETED !! ****")
                return
            } else {
                while (currentComicList.isEmpty()) {
                    if (dirUriList.isEmpty()) break     // No remaining directory... so exit

                    currentUri = dirUriList.removeAt(0)

                    // Build the new comic entry list
                    val tempComicList = getComicEntriesFromUri(App.appContext, currentUri!!, true)
                    if (tempComicList.isNotEmpty()) {
                        currentComicList = tempComicList as MutableList<ComicEntry>
                    }
                    // Add this uri to generate an directory icon
                    currentComicList.add(getComicFromUri(App.appContext, currentUri)!!)

                    Timber.v("new currentUri = $currentUri")
                    Timber.v("new currentComicList = $currentComicList")
                    Timber.v("dirUriList = $dirUriList")

                    // Can we continue?
                    if (!isIdle) return
                }

                // Start loading covers
                if (currentComicList.isNotEmpty()) {
                    val newComic = currentComicList.removeAt(0)
                    ComicLoadingManager.getInstance().loadComicEntryCover(newComic, this)
                } else {
                    loadCompleted = true
                    Timber.v("  **** LOAD COMPLETED !! ****")
                    return
                }
            }
        }
    }
}