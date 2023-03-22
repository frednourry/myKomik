package fr.nourry.mykomik.browser

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.databinding.FragmentBrowserBinding
import fr.nourry.mykomik.dialog.DialogChooseRootDirectory
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.preference.PREF_ROOT_DIR
import fr.nourry.mykomik.preference.SharedPref
import fr.nourry.mykomik.settings.UserPreferences
import fr.nourry.mykomik.utils.clearFilesInDir
import fr.nourry.mykomik.utils.getDefaultDirectory
import fr.nourry.mykomik.utils.isDirExists
import timber.log.Timber
import java.io.File


private const val TAG_DIALOG_CHOOSE_ROOT = "SelectDirectoryDialog"

class BrowserFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener, BrowserAdapter.OnComicAdapterListener {

    companion object {
        fun newInstance() = BrowserFragment()
    }

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootDirectory : File
    private var lastComic : File? = null

    private lateinit var browserAdapter: BrowserAdapter
    private lateinit var supportActionBar:ActionBar
    private var comics = mutableListOf<ComicEntry>()

    private var isFilteredMode = false          // Special mode too select items (to erase or something else...)
    private var selectedComicIndexes:ArrayList<Int> = ArrayList(0)

    // Test for View Binding (replace 'kotlin-android-extensions')
    private var _binding: FragmentBrowserBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!


    // Menu item from the side menu (to remind to inactive them)
    private lateinit var sideMenuItemClearCache : MenuItem
    private lateinit var sideMenuItemChangeRootDirectory : MenuItem


    private val confirmationChooseRootDialogListener = object: DialogChooseRootDirectory.ConfirmationDialogListener {
        override fun onChooseDirectory(file:File) {
            Timber.d("onChooseDirectory :: ${file.absolutePath}")

            // Save
            viewModel.setPrefRootDir(file.absolutePath)

            rootDirectory = file
            loadComics(rootDirectory)
        }
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

        //// MENU
        // The usage of an interface lets you inject your own implementation
        val menuHost: MenuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                if (isFilteredMode) {
                    menuInflater.inflate(R.menu.menu_browser_selection_fragment, menu)
                } else {
                    menuInflater.inflate(R.menu.menu_browser_fragment, menu)
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.clear()
                if (isFilteredMode) {
                    requireActivity().menuInflater.inflate(R.menu.menu_browser_selection_fragment, menu)
                } else {
                    requireActivity().menuInflater.inflate(R.menu.menu_browser_fragment, menu)
                }
                super.onPrepareMenu(menu)

                    // Disable the menu items to hide in guest mode
                if (!isFilteredMode) {
                    val menuItemClearCache = menu.findItem(R.id.action_clear_cache)
                    menuItemClearCache.isEnabled = !App.isGuestMode
                    val menuItemChangeRootDirectory = menu.findItem(R.id.action_choose)
                    menuItemChangeRootDirectory.isEnabled = !App.isGuestMode
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
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
                    R.id.action_settings -> {
                        goSettings()
                        true
                    }
                    R.id.action_select_none -> {
                        selectNone()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        //// End MENU



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

        // Action bar
        supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        supportActionBar.setDisplayHomeAsUpEnabled(false)
/*        supportActionBar.setLogo(R.mipmap.ic_launcher)
        supportActionBar.setDisplayUseLogoEnabled(true)*/

        // Side menubar (DrawerLayout and NavigationView)
        NavigationUI.setupWithNavController(binding.navigationView,NavHostFragment.findNavController(thisFragment))
        binding.navigationView.setNavigationItemSelectedListener(this)

        val menuItemGuest = binding.navigationView.menu.findItem(R.id.action_nav_guest_switch)
        val switchGuest = menuItemGuest.actionView as SwitchCompat
        switchGuest.isChecked = switchGuest.isChecked
        switchGuest.setOnClickListener(View.OnClickListener {
            Timber.d("switchGuest.onClick :: ${switchGuest.isChecked}")
            setGuestMode(switchGuest.isChecked)
        })
        sideMenuItemClearCache = binding.navigationView.menu.findItem(R.id.action_nav_clear_cache)
        sideMenuItemChangeRootDirectory = binding.navigationView.menu.findItem(R.id.action_nav_choose)

        val skipReadComic = UserPreferences.getInstance(requireContext()).shouldHideReadComics()
        viewModel.init(skipReadComic)
    }
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        Timber.d("onNavigationItemSelected $item")
        return when (item.itemId) {
            R.id.action_nav_guest_switch ->  {
                val switchGuest = item.actionView as SwitchCompat
                switchGuest.isChecked = !switchGuest.isChecked
                setGuestMode(switchGuest.isChecked)
                true
            }
            R.id.action_nav_choose -> {
                askToChangeRootDirectory()
                true
            }
            R.id.action_nav_clear_cache -> {
                askToClearCache()
                true
            }
            R.id.action_nav_about -> {
                showAboutPopup()
                true
            }
            R.id.action_nav_settings -> {
                goSettings()
                true
            }
            else -> false
        }
    }

    private fun getNbColumns(columnWidth: Int): Int {
        val displayMetrics = fr.nourry.mykomik.App.physicalConstants.metrics
        return ((displayMetrics.widthPixels / displayMetrics.density) / columnWidth).toInt()
    }

    // Update UI according to the model state events
    private fun updateUI(state: BrowserViewModelState) {
        Timber.i("Calling updateUI, switch state=${state::class}")
        when(state) {
            is BrowserViewModelState.Error -> handleStateError(state)
            is BrowserViewModelState.Init -> handleStateInit(state)
            is BrowserViewModelState.ComicLoading -> handleStateLoading(state.currentDir)
            is BrowserViewModelState.ComicReady -> handleStateReady(state)
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

        supportActionBar.title = getLocalDirName(App.currentDir?.canonicalPath)

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
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

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
                            val action = BrowserFragmentDirections.actionBrowserFragmentToPageSliderFragment(ComicEntry(lastComic!!), currentPage)
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
            val action = BrowserFragmentDirections.actionBrowserFragmentToPageSliderFragment(comic, comic.currentPage)
            findNavController().navigate(action)
        }
    }

    override fun onComicEntryLongClicked(comic: ComicEntry, position:Int) {
        Timber.v("onComicEntryLongClicked "+comic.file.name)

        if (App.isGuestMode) {
            Timber.i("onComicEntryLongClicked:: Guest Mode, so do nothing")
        } else {
            // Show checkboxes and a new menu
            setFilterMode(true, arrayListOf(position))
        }
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

    private fun setGuestMode(isGuest:Boolean) {
        Timber.v("setGuestMode:: $isGuest")
        App.isGuestMode = isGuest

        // Refresh the browser
        browserAdapter.selectNone()
        disableMultiSelect()
        browserAdapter.notifyDataSetChanged()


        // Update menu option
/*        if (menuItemClearCache == null) Timber.i("menuItemClearCache == null") else Timber.i("menuItemClearCache not null")
        menuItemClearCache.isEnabled = !isGuest
        menuItemChangeRootDirectory.isEnabled = !isGuest
        requireActivity().invalidateOptionsMenu()
*/
        // Update side menu
        Timber.w("menuItemClearCache is null ! $sideMenuItemClearCache")
        sideMenuItemClearCache.isEnabled = !isGuest
        sideMenuItemChangeRootDirectory.isEnabled = !isGuest
    }

    private fun askToChangeRootDirectory() {
        // Can't change root directory in guest mode !
        if (App.isGuestMode)
            return

        val alert = AlertDialog.Builder(requireContext())
            .setMessage(R.string.ask_change_root_directory)
            .setPositiveButton(R.string.ok) { _,_ -> showChooseDirectoryDialog(rootDirectory, true) }
            .setNegativeButton(android.R.string.cancel) { _,_ -> }
            .create()
        alert.show()
    }

    private fun askToClearCache() {
        // Can't clear cache in guest mode !
        if (App.isGuestMode)
            return

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
        // Can't delete comics in guest mode !
        if (App.isGuestMode)
            return

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

    private fun goSettings() {
        val action = BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
        findNavController().navigate(action)
    }

    private fun showAboutPopup() {
        val title = getString(R.string.app_name)+" "+ App.packageInfo.versionName
        val message = getString(R.string.about_description)
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

    // Strip 'dirPath' of the root directory path (except the last dir name)
    private fun getLocalDirName(dirPath:String?):String {
        var result = dirPath ?: ""
        var rootPath = SharedPref.get(PREF_ROOT_DIR, "")
        if (rootPath != null && rootPath != "") {
            rootPath = rootPath.substring(0, rootPath.lastIndexOf("/"))
            if (rootPath.isNotEmpty()) {
                val i = result.indexOf(rootPath)
                if (i >= 0) {
                    result = result.substring(rootPath.length)+"/"
                }
            }
        }
        return result
    }
}