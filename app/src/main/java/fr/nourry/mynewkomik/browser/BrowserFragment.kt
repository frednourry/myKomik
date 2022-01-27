package fr.nourry.mynewkomik.browser

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import fr.nourry.mynewkomik.utils.isDirExists
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mynewkomik.*
import fr.nourry.mynewkomik.dialog.DialogChooseRootDirectory
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.preference.SharedPref
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.getComicsDirectory
import kotlinx.android.synthetic.main.fragment_browser.*
import timber.log.Timber
import java.io.File
import androidx.appcompat.app.AlertDialog


private const val REQUEST_PERMISSION = 1

private const val PARAM_ROOT_DIR    = "comics_dir"

class BrowserFragment : Fragment(), ComicAdapter.OnComicAdapterListener {

    companion object {
        fun newInstance() = BrowserFragment()
    }

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootDirectory : File
    private lateinit var currentDirectory : File

    private lateinit var comicAdapter: ComicAdapter
    private var comics = mutableListOf<Comic>()

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
                Timber.d("    PAS DE RETOUR EN STACK !!")
                activity?.finish()
            }
        }

        activity?.let { SharedPref.init(it) }

        comicAdapter = ComicAdapter(comics, this)
        recyclerView.layoutManager =GridLayoutManager(context, 3)
        recyclerView.adapter = comicAdapter

        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner, {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        })

        askPermission()
    }

    private fun setCurrentDir(dir:File) {
        currentDirectory = dir
        App.currentDir = dir
    }

    /**
     *  Demander les autorisation d'accès à la SD card
     */
    private fun askPermission() {
        Timber.d("askPermission")

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_PERMISSION)
            return
        }
        viewModel.init()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode) {
            REQUEST_PERMISSION -> {
                if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    viewModel.errorPermissionDenied()
                    return
                }
                viewModel.init()
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
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
        (requireActivity() as AppCompatActivity).supportActionBar?.title = currentDirectory.canonicalPath

        comics.clear()
        comics.addAll(state.comics)
        comicAdapter.notifyDataSetChanged()
    }


    // Show a dialog to ask where is the root comics directory
    private fun showChooseDirectoryDialog(rootFile: File?) {
        val root:File = rootFile ?: getComicsDirectory(requireContext())

        val dialog = DialogChooseRootDirectory(root)
        dialog.isCancelable = false

        dialog.listener = object:DialogChooseRootDirectory.ConfirmationDialogListener {
            override fun onChooseDirectory(file:File) {
                Timber.d("onChooseDirectory :: ${file.absolutePath}")
                dialog.dismiss()

                // Save
                SharedPref.set(PARAM_ROOT_DIR, file.absolutePath)

                rootDirectory = file
                viewModel.loadComics(rootDirectory)
            }
        }
        dialog.show(childFragmentManager, "SelectDirectoryDialog")
    }

    private fun initBrowser() {
        Timber.d("initBrowser")

        val directoryPath = SharedPref.get(PARAM_ROOT_DIR, "")
        if (directoryPath == "" || !isDirExists(directoryPath!!)) {
            Snackbar.make(coordinatorLayout, "TODO Saisie du répertoire !!", Snackbar.LENGTH_LONG).show()
            var rootDir:File? = null
            if (isDirExists(directoryPath))
                rootDir = File("/storage/emulated/O")
            showChooseDirectoryDialog(rootDir)
        } else {
            rootDirectory = File(directoryPath)

            if (App.currentDir == null) {
                viewModel.loadComics(rootDirectory)

            } else {
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
            val action = BrowserFragmentDirections.actionBrowserFragmentToPictureSliderFragment(comic)
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
            .setPositiveButton(R.string.ok) { _,_ -> showChooseDirectoryDialog(rootDirectory) }
            .setNegativeButton(android.R.string.cancel) { _,_ -> }
            .create()
        alert.show()
    }

    private fun askToClearCache() {
        val alert = AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.ask_clear_cache))
            .setPositiveButton(R.string.ok) { _,_ ->
                val cacheDir = File(requireActivity().cacheDir.absolutePath)
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    Toast.makeText(requireContext(), "Clear cache...", Toast.LENGTH_SHORT).show()
                    clearFilesInDir(cacheDir)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _,_ -> }
            .create()
        alert.show()
    }


    // The button back is pressed, so can we move to the parent directory?
    // Returns true if and only if we can
    private fun handleBackPressedToChangeDirectory():Boolean {
        Timber.d("handleBackPressedToChangeDirectory - current=$currentDirectory (root=$rootDirectory)")
        return if (currentDirectory.parentFile == null || currentDirectory.absolutePath == rootDirectory.absolutePath) {
            false
        } else {
            viewModel.loadComics(currentDirectory.parentFile!!)
            true
        }
    }

}