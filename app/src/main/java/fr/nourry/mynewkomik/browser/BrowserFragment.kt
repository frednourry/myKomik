package fr.nourry.mynewkomik.browser

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mynewkomik.*
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.databinding.FragmentBrowserBinding
import fr.nourry.mynewkomik.dialog.DialogChooseRootDirectory
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.preference.SharedPref
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.getDefaultDirectory
import fr.nourry.mynewkomik.utils.isDirExists
import timber.log.Timber
import java.io.File


private const val TAG_DIALOG_CHOOSE_ROOT = "SelectDirectoryDialog"

class BrowserFragment : Fragment(), BrowserAdapter.OnComicAdapterListener {

    companion object {
        fun newInstance() = BrowserFragment()

    }

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootDirectory : File
    private var lastComic : File? = null

    private lateinit var browserAdapter: BrowserAdapter
    private lateinit var supportActionBar:ActionBar
    private var comics = mutableListOf<ComicEntry>()

    private var isFilteredMode = false          // Special mode too select items (to erase or somthing else...)
    private var selectedComicIndexes:ArrayList<Int> = ArrayList(0)

    // Test for View Binding (replace 'kotlin-android-extensions')
    private var _binding: FragmentBrowserBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!


    private val confirmationChooseRootDialogListener = object: DialogChooseRootDirectory.ConfirmationDialogListener {
        override fun onChooseDirectory(file:File) {
            Timber.d("onChooseDirectory :: ${file.absolutePath}")

            // Save
            viewModel.setPrefRootDir(file.absolutePath)

            rootDirectory = file
            loadComics(rootDirectory)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.clear()
        if (isFilteredMode) {
            requireActivity().menuInflater.inflate(R.menu.menu_browser_selection_fragment, menu)
        } else {
            requireActivity().menuInflater.inflate(R.menu.menu_browser_fragment, menu)

        }
        super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_browser_fragment, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Timber.i("onCreateView")

        ComicLoadingManager.getInstance()
            .initialize(requireContext(), requireActivity().cacheDir.absolutePath)
        ComicLoadingManager.getInstance().setLivecycleOwner(this)

        // Re-associate the DialogChooseRootDirectory listener if necessary
        val dialogChooseRootDirectory = parentFragmentManager.findFragmentByTag(TAG_DIALOG_CHOOSE_ROOT)
        if (dialogChooseRootDirectory != null) {
            (dialogChooseRootDirectory as DialogChooseRootDirectory).listener = confirmationChooseRootDialogListener
        }

        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("onViewCreated")

        val thisFragment = this
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Handle the back button event
            Timber.d("BACK PRESSED !!!!!!!")

            // Check if we can bo back in the tree file AND if the previous fragment was this one (to prevent to go back to PermissionFragment...)
            if (!handleBackPressedToChangeDirectory() && !NavHostFragment.findNavController(thisFragment).popBackStack(R.id.browserFragment, false)) {
                Timber.v("    NO STACK !!")
                activity?.finish()
            }
        }

        activity?.let { SharedPref.init(it) }

        browserAdapter = BrowserAdapter(comics, this)
        binding.recyclerView.layoutManager = GridLayoutManager(context, getNbColumns(180))  // Should be higher, but cool result so keep it...
        binding.recyclerView.adapter = browserAdapter

        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner) {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        }

        // LiveData for the ViewModel :
        //  NOTE: Observer needs a livecycle owner that is not accessible by the ViewModel directly, so to observe a liveData, our ViewModel observers uses this Fragment...
        viewModel.comicEntriesFromDAO.observe(viewLifecycleOwner) { comicEntriesFromDAO ->
            Timber.w("UPDATED::comicEntriesFromDAO=$comicEntriesFromDAO")
            viewModel.updateComicEntriesFromDAO(comicEntriesFromDAO)
        }
        // End LiveDatas

        supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!

        viewModel.init()
    }

    private fun getNbColumns(columnWidth: Int): Int {
        val displayMetrics = App.physicalConstants.metrics
        return ((displayMetrics.widthPixels / displayMetrics.density) / columnWidth).toInt()
    }

    // Update UI according to the model state events
    private fun updateUI(state: BrowserViewModelState) {
        Timber.i("Calling updateUI, switch state=${state::class}")
        return when(state) {
            is BrowserViewModelState.Error -> handleStateError(state)
            is BrowserViewModelState.Init -> handleStateInit(state)
            is BrowserViewModelState.ComicLoading -> handleStateLoading(state.currentDir)
            is BrowserViewModelState.ComicReady -> handleStateReady(state)
            else -> {}
        }
    }

    // An error occurs
    private fun handleStateError(state: BrowserViewModelState.Error) {
        Timber.i("handleStateError")
        Snackbar.make(binding.coordinatorLayout, "ERROR: ${state.errorMessage}", Snackbar.LENGTH_LONG).show()

        // Exit app ?

    }

    private fun handleStateInit(state:BrowserViewModelState.Init) {
        Timber.i("handleStateInit")
        initBrowser(state.directoryPath, state.lastComicPath, state.prefCurrentPage)
    }

    private fun handleStateLoading(dir: File?) {
        Timber.i("handleStateLoading")
        if (dir != null) {
            Timber.i("handleStateLoading "+dir.name)
            viewModel.setAppCurrentDir(dir)

            // Stop all loading...
            ComicLoadingManager.getInstance().clean()
        }
    }

    private fun handleStateReady(state: BrowserViewModelState.ComicReady) {
        Timber.i("handleStateReady")
        Timber.i("  state.comics=${state.comics}")

        supportActionBar.title = App.currentDir?.canonicalPath

        viewModel.setPrefLastComicPath("")      // Forget the last comic...

        disableMultiSelect()

        comics.clear()
        comics.addAll(state.comics)
        browserAdapter.notifyDataSetChanged()
        binding.recyclerView.scrollToPosition(0)
    }


    // Show a dialog to ask where is the root comics directory
    private fun showChooseDirectoryDialog(rootFile: File?, isCancelable:Boolean) {
        Timber.w("showChooseDirectoryDialog:: rootFile = $rootFile")
        val root:File = rootFile ?: getDefaultDirectory(requireContext())

        val dialog = DialogChooseRootDirectory.newInstance(root)
        dialog.isCancelable = isCancelable
        dialog.listener = confirmationChooseRootDialogListener

        dialog.show(parentFragmentManager, TAG_DIALOG_CHOOSE_ROOT)
    }

    private fun initBrowser(directoryPath:String, lastComicPath:String, prefCurrentPage:String) {
        Timber.d("initBrowser directoryPath=$directoryPath lastComicPath=$lastComicPath prefCurrentPage=$prefCurrentPage")

        if (directoryPath == "" || !isDirExists(directoryPath)) {
            showChooseDirectoryDialog(null, false)
        } else {
            rootDirectory = File(directoryPath)

            if (App.currentDir == null) {
                // It's the first time we come in this fragment

                // Ask if we should use the last comic
                if (lastComicPath != "") {
                    lastComic = File(lastComicPath)
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
                            if (prefCurrentPage != "") {
                                currentPage = prefCurrentPage.toInt()
                            }
                            val action = BrowserFragmentDirections.actionBrowserFragmentToPictureSliderFragment(ComicEntry(lastComic!!), currentPage)
                            findNavController().navigate(action)
                        }
                        .setNegativeButton(android.R.string.cancel) { _,_ ->
                            viewModel.setPrefLastComicPath("")      // Forget the last comic...
                            loadComics(rootDirectory)
                        }
                        .setCancelable(false)
                        .create()
                    alert.show()
                } else {
                    loadComics(rootDirectory)
                }

            } else {
                //
                loadComics(App.currentDir!!)
            }
        }
    }

    // Ask the viewModel to load comics informations in a given directory
    fun loadComics(dir:File) {
        viewModel.loadComics(dir)
    }

    override fun onComicEntryClicked(comic: ComicEntry, position:Int) {
        Timber.v("onComicEntryClicked "+comic.file.name)
        if (comic.isDirectory) {
            Timber.i("Directory !")
            loadComics(comic.file)
        } else {
            Timber.i("File ${comic.file.name} !")
            lastComic= comic.file
            viewModel.setPrefLastComicPath(comic.file.absolutePath)
            val action = BrowserFragmentDirections.actionBrowserFragmentToPictureSliderFragment(comic, comic.currentPage)
            findNavController().navigate(action)
        }
    }

    override fun onComicEntryLongClicked(comic: ComicEntry, position:Int) {
        Timber.v("onComicEntryLongClicked "+comic.file.name)

        // Show checkboxes and a new menu
        setFilterMode(true, arrayListOf(position))
    }

    override fun onComicEntrySelected(list:ArrayList<Int>) {
        selectedComicIndexes = list
        updateNbItemsSelected(list.size)
    }

    private fun updateNbItemsSelected(nb:Int) {
        val customView = supportActionBar.customView
        if (customView != null) {
            val textView = customView.findViewById<TextView>(R.id.actionbar_selection_count_textView)
            if (textView != null)
                textView.text = "$nb"
        }
    }

    private fun setFilterMode(bFilter:Boolean, selectedList:ArrayList<Int>? = null) {
        Timber.v("setFilterMode($bFilter) isFilteredMode=$isFilteredMode")
        if (isFilteredMode == bFilter) return

        if (!isFilteredMode) {
            isFilteredMode = true
            browserAdapter.setFilterMode(isFilteredMode, selectedList)
            supportActionBar.setDisplayShowTitleEnabled(false)
            supportActionBar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            supportActionBar.setHomeButtonEnabled(false)
            val multiSelect = requireActivity().layoutInflater.inflate(R.layout.fragment_browser_actionbar_selection, null) as LinearLayout
            (multiSelect.findViewById<View>(R.id.actionbar_selection_done) as Button).setOnClickListener { this@BrowserFragment.disableMultiSelect() }
            supportActionBar.customView = multiSelect
            requireActivity().invalidateOptionsMenu()
        } else {
            isFilteredMode = false
            supportActionBar.displayOptions = ActionBar.DISPLAY_SHOW_HOME
            browserAdapter.setFilterMode(isFilteredMode, selectedList)
            supportActionBar.setDisplayShowTitleEnabled(true)
            supportActionBar.setHomeButtonEnabled(true)
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun disableMultiSelect() {
        setFilterMode(false)
    }

    private fun selectAll() {
        browserAdapter.selectAll()
        updateNbItemsSelected(comics.size)
    }

    private fun selectNone() {
        browserAdapter.selectNone()
        updateNbItemsSelected(0)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_choose -> {
                askToChangeRootDirectory()
                true
            }
            R.id.action_clear_cache -> {
                askToClearCache()
                true
            }
            R.id.action_about -> {
                showAboutPopup()
                true
            }
            R.id.action_delete_selection -> {
                askToDeleteSelection()
               true
            }
            R.id.action_select_all -> {
                selectAll()
                true
            }
            R.id.action_select_none -> {
                selectNone()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun askToChangeRootDirectory() {
        val alert = AlertDialog.Builder(requireContext())
            .setMessage(R.string.ask_change_root_directory)
            .setPositiveButton(R.string.ok) { _,_ -> showChooseDirectoryDialog(rootDirectory, true) }
            .setNegativeButton(android.R.string.cancel) { _,_ -> }
            .create()
        alert.show()
    }

    private fun askToClearCache() {
        val alert = AlertDialog.Builder(requireContext())
            .setMessage(R.string.ask_clear_cache)
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

    private fun askToDeleteSelection() {
        val alert: AlertDialog
        if (selectedComicIndexes.size == 0) {
            alert = AlertDialog.Builder(requireContext())
                .setMessage(R.string.no_file_selected)
                .setPositiveButton(R.string.ok) { _,_ -> }
                .create()
        } else {
            // Build a list of files to delete and add it to the message to display
            var message = if (selectedComicIndexes.size == 1)
                getString(R.string.ask_delete_this_file)
            else
                getString(R.string.ask_delete_files, selectedComicIndexes.size)

            val deleteList: MutableList<ComicEntry> = arrayListOf()
            for (cpt in selectedComicIndexes) {
                deleteList.add(comics[cpt])
                message += "\n - "+comics[cpt].file.name
            }

            alert = AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton(R.string.ok) { _,_ ->
                    Timber.d("selectedComicIndexes = $selectedComicIndexes")

                    // Ask the viewModel to delete those files
                    viewModel.prepareDeleteComicEntries(deleteList)

                    // Give some time of reflection...
                    showSnackBarUndoDeletion()
                }
                .setNegativeButton(android.R.string.cancel) { _,_ -> }
                .create()
        }

        alert.show()
    }

    private fun showAboutPopup() {
        val title = getString(R.string.app_name)+" "+App.packageInfo.versionName
        var message = getString(R.string.about_description)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok) { _,_ ->
                }
            .create()
            .show()
    }

    private fun showSnackBarUndoDeletion() {
        Snackbar
            .make(binding.coordinatorLayout, R.string.message_files_deleted, Snackbar.LENGTH_LONG)
            .setDuration(viewModel.TIME_BEFORE_DELETION)
            .setAction(R.string.message_undo) {
                if (viewModel.undoDeleteComicEntries()) {
                    Timber.d("Deleting undone, so need to refresh dir...")
                    loadComics(App.currentDir!!)
                }
            }
            .show()
    }


    // The button back is pressed, so can we move to the parent directory?
    // Returns true if and only if we can
    private fun handleBackPressedToChangeDirectory():Boolean {
        Timber.d("handleBackPressedToChangeDirectory - current=${App.currentDir} (root=$rootDirectory)")
        return if (isFilteredMode) {
            setFilterMode(false)
            true
        } else if (App.currentDir?.parentFile == null || App.currentDir?.absolutePath == rootDirectory.absolutePath) {
            false
        } else {
            loadComics(App.currentDir?.parentFile!!)
            true
        }
    }

}