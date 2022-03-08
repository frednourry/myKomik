package fr.nourry.mynewkomik.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mynewkomik.*
import fr.nourry.mynewkomik.dialog.DialogChooseRootDirectory
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mynewkomik.preference.PREF_ROOT_DIR
import fr.nourry.mynewkomik.preference.PREF_LAST_COMIC
import fr.nourry.mynewkomik.preference.SharedPref
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.getDefaultDirectory
import fr.nourry.mynewkomik.utils.isDirExists
import kotlinx.android.synthetic.main.fragment_browser.*
import timber.log.Timber
import java.io.File


private const val TAG_DIALOG_CHOOSE_ROOT = "SelectDirectoryDialog"

class BrowserFragment : Fragment(), BrowserAdapter.OnComicAdapterListener {

    companion object {
        fun newInstance() = BrowserFragment()

        var PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
    }

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootDirectory : File
    private var lastComic : File? = null

    private lateinit var browserAdapter: BrowserAdapter
    private var comics = mutableListOf<Comic>()


    private val confirmationDialogListener = object: DialogChooseRootDirectory.ConfirmationDialogListener {
        override fun onChooseDirectory(file:File) {
            Timber.d("onChooseDirectory :: ${file.absolutePath}")

            // Save
            SharedPref.set(PREF_ROOT_DIR, file.absolutePath)

            rootDirectory = file
            viewModel.loadComics(rootDirectory)
        }
    }

    private val permissionRequestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value == true
            }
            if (granted) {
                viewModel.init()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_browser_fragment, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Timber.i("onCreateView")

        ComicLoadingManager.getInstance().initialize(requireContext(), requireActivity().cacheDir.absolutePath)
        ComicLoadingManager.getInstance().setLivecycleOwner(this)


        // Re-associate the DialogChooseRootDirectory listener if necessary
        val dialogChooseRootDirectory = parentFragmentManager.findFragmentByTag(TAG_DIALOG_CHOOSE_ROOT)
        if (dialogChooseRootDirectory != null) {
            (dialogChooseRootDirectory as DialogChooseRootDirectory).listener = confirmationDialogListener
        }

        return inflater.inflate(R.layout.fragment_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("onViewCreated")

        val thisFragment = this
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Handle the back button event
            Timber.d("BACK PRESSED !!!!!!!")

            if (!handleBackPressedToChangeDirectory() && !NavHostFragment.findNavController(thisFragment).popBackStack()) {
                Timber.v("    NO STACK !!")
                activity?.finish()
            }
        }

        activity?.let { SharedPref.init(it) }

        browserAdapter = BrowserAdapter(comics, this)
        recyclerView.layoutManager = GridLayoutManager(context, getNbColumns(180))
        recyclerView.adapter = browserAdapter

        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner) {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        }

        askPermission()
    }

    private fun getNbColumns(columnWidth: Int): Int {
        val displayMetrics = App.physicalConstants.metrics
        return ((displayMetrics.widthPixels / displayMetrics.density) / columnWidth).toInt()
    }

    private fun setCurrentDir(dir:File) {
        App.currentDir = dir
    }

    /**
     *  Demander les autorisation d'accès à la SD card
     */

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun askPermission() {
        Timber.d("askPermission")
        if (hasPermissions(activity as Context, PERMISSIONS)) {
            viewModel.init()
        } else {
            permissionRequestLauncher.launch(PERMISSIONS)
        }
    }

    // Update UI according to the model state events
    private fun updateUI(state: BrowserViewModelState) {
        Timber.i("Calling updateUI, switch state=${state::class}")
        return when(state) {
            is BrowserViewModelState.Error -> handleStateError(state)
            is BrowserViewModelState.Init -> handleStateInit()
            is BrowserViewModelState.ComicLoading -> handleStateLoading(state.currentDir)
            is BrowserViewModelState.ComicReady -> handleStateReady(state)
            else -> {}
        }
    }

    // An error occurs
    private fun handleStateError(state: BrowserViewModelState.Error) {
        Timber.i("handleStateError")
        Snackbar.make(coordinatorLayout, "ERROR: ${state.errorMessage}", Snackbar.LENGTH_LONG).show()

        // Exit app ?

    }

    private fun handleStateInit() {
        Timber.i("handleStateInit")
        initBrowser()
    }

    private fun handleStateLoading(dir: File?) {
        Timber.i("handleStateLoading")
        if (dir != null) {
            Timber.i("handleStateLoading "+dir.name)
            setCurrentDir(dir)

            // Stop all loading...
            ComicLoadingManager.getInstance().clean()
        }
    }

    private fun handleStateReady(state: BrowserViewModelState.ComicReady) {
        Timber.i("handleStateReady")

        setCurrentDir(state.currentDir!!)
        (requireActivity() as AppCompatActivity).supportActionBar?.title = App.currentDir?.canonicalPath

        SharedPref.set(PREF_LAST_COMIC, "")    // Forget the last comic...

        comics.clear()
        comics.addAll(state.comics)
        browserAdapter.notifyDataSetChanged()
    }


    // Show a dialog to ask where is the root comics directory
    private fun showChooseDirectoryDialog(rootFile: File?, isCancelable:Boolean) {
        val root:File = rootFile ?: getDefaultDirectory(requireContext())

        val dialog = DialogChooseRootDirectory.newInstance(root)
        dialog.isCancelable = isCancelable
        dialog.listener = confirmationDialogListener

        dialog.show(parentFragmentManager, TAG_DIALOG_CHOOSE_ROOT)
    }

    private fun initBrowser() {
        Timber.d("initBrowser")

        val directoryPath = SharedPref.get(PREF_ROOT_DIR, "")
        if (directoryPath == "" || !isDirExists(directoryPath!!)) {
            var rootDir:File? = null
            if (isDirExists(directoryPath))
                rootDir = File("/storage/emulated/O")
            showChooseDirectoryDialog(rootDir, false)
        } else {
            rootDirectory = File(directoryPath)

            if (App.currentDir == null) {
                // It's the first time we come in this fragment, so use the rootDirectory


                // Ask if we should use the last comic
                val lastComicPath = SharedPref.get(PREF_LAST_COMIC, "")
                if (lastComicPath != "") {
                    lastComic = File(lastComicPath!!)
                    if (!lastComic!!.exists() || !lastComic!!.isFile) {
                        lastComic = null
                    }
                }
                if (lastComic != null && lastComic!!.isFile) {
                    // Continue reading from where you last left off "....." ?
                    val alert = AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.ask_continue_with_same_comic)+ " ("+lastComic!!.name+")")
                        .setPositiveButton(R.string.ok) { _,_ ->
                            App.currentDir = File(lastComic!!.parent!!) // Set the last comic path as the current directory

                            // Call the fragment to view the last comic
                            var currentPage = 0
                            val prefCurrentPage = SharedPref.get(PREF_CURRENT_PAGE_LAST_COMIC, "0")
                            if (prefCurrentPage != null) {
                                currentPage = prefCurrentPage.toInt()
                            }
                            val action = BrowserFragmentDirections.actionBrowserFragmentToPictureSliderFragment(Comic(lastComic!!), currentPage)
                            findNavController().navigate(action)
                        }
                        .setNegativeButton(android.R.string.cancel) { _,_ ->
                            SharedPref.set(PREF_LAST_COMIC, "")    // Forget the last comic...
                            viewModel.loadComics(rootDirectory)
                        }
                        .create()
                    alert.show()
                } else {
                    viewModel.loadComics(rootDirectory)
                }

            } else {
                //
                viewModel.loadComics(App.currentDir!!)
            }
        }
    }

    override fun onComicClicked(comic: Comic) {
        Timber.d("onComicClicked "+comic.file.name)
        if (comic.file.isDirectory) {
            Timber.i("Directory !")
            viewModel.loadComics(comic.file)
        } else {
            Timber.i("File ${comic.file.name} !")
            lastComic= comic.file
            SharedPref.set(PREF_LAST_COMIC, comic.file.absolutePath)
            val action = BrowserFragmentDirections.actionBrowserFragmentToPictureSliderFragment(comic, 0)
            findNavController().navigate(action)
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_choose -> {
                askToChangeRootDirectory()
                return true
            }
            R.id.action_empty_cache -> {
                askToClearCache()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun askToChangeRootDirectory() {
        val alert = AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.ask_change_root_directory))
            .setPositiveButton(R.string.ok) { _,_ -> showChooseDirectoryDialog(rootDirectory, true) }
            .setNegativeButton(android.R.string.cancel) { _,_ -> }
            .create()
        alert.show()
    }

    private fun askToClearCache() {
        val alert = AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.ask_clear_cache))
            .setPositiveButton(R.string.ok) { _,_ ->
                val cacheDir = App.physicalConstants.cacheDir
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    Toast.makeText(requireContext(), "Clear cache...", Toast.LENGTH_SHORT).show()
                    clearFilesInDir(cacheDir)

                    // Glide...
                    Glide.get(requireContext()).clearMemory()
                    Thread {
                        Glide.get(requireContext()).clearDiskCache()
                    }.start()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _,_ -> }
            .create()
        alert.show()
    }


    // The button back is pressed, so can we move to the parent directory?
    // Returns true if and only if we can
    private fun handleBackPressedToChangeDirectory():Boolean {
        Timber.d("handleBackPressedToChangeDirectory - current=${App.currentDir} (root=$rootDirectory)")
        return if (App.currentDir?.parentFile == null || App.currentDir?.absolutePath == rootDirectory.absolutePath) {
            false
        } else {
            viewModel.loadComics(App.currentDir?.parentFile!!)
            true
        }
    }

}