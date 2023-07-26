package fr.nourry.mykomik.browser

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.databinding.FragmentBrowserBinding
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.loader.IdleController
import fr.nourry.mykomik.preference.SharedPref
import fr.nourry.mykomik.settings.UserPreferences
import fr.nourry.mykomik.utils.*
import timber.log.Timber
import kotlin.collections.ArrayList

class BrowserFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener, BrowserAdapter.OnComicAdapterListener {

    private lateinit var viewModel: BrowserViewModel
    private lateinit var rootTreeUri : Uri
    private var lastComicUri : Uri? = null

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


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Timber.i("onCreateView")

        ComicLoadingManager.getInstance().initialize(requireContext(), App.thumbnailCacheDirectory, App.pageCacheDirectory)
        ComicLoadingManager.getInstance().setLivecycleOwner(this)

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

        // Update metrics
        App.physicalConstants.updateMetrics(requireContext())

        activity?.let { SharedPref.init(it) }

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
//            Timber.w("UPDATED::comicEntriesFromDAO=$comicEntriesFromDAO")
            viewModel.updateComicEntriesFromDAO(comicEntriesFromDAO)
        }
        // End LiveDatas

        // Action bar
        supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        supportActionBar.setDisplayHomeAsUpEnabled(false)

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

        // Check the Shared Storage for a tree uri
        var uriInStorage = treeUriInSharedStorage()
        if (uriInStorage != null) {
            // Convert uriInStorage in a usable one
            val id = DocumentsContract.getTreeDocumentId(uriInStorage)
            val trueUriInStorage = DocumentsContract.buildDocumentUriUsingTree(uriInStorage, id)

            uriInStorage = trueUriInStorage
            rootTreeUri = trueUriInStorage
            App.rootTreeUri = rootTreeUri
        }
        val skipReadComic = UserPreferences.getInstance(requireContext()).shouldHideReadComics()

        // Define the current URI
        viewModel.init(App.currentTreeUri?: uriInStorage, skipReadComic)
    }

    // Permissions

    // Return the first tree uri un the Shared Storage
    private fun treeUriInSharedStorage() : Uri? {
        for (perm in requireContext().contentResolver.persistedUriPermissions) {
            if (DocumentsContract.isTreeUri(perm.uri)) {
                return perm.uri
            }
        }
        return null
    }

    private fun askTreeUriPermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (::rootTreeUri.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker when it loads.
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootTreeUri)
            }
        }
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        permissionIntentLauncher.launch(intent)
    }

    private var permissionIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Timber.i("result=$result")
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let{ intent->

                var flags: Int = intent.flags
                flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                intent.data?.let { treeUri ->
                    Timber.i("treeUri=$treeUri")
                    // treeUri is the Uri

                    val documentsTree = DocumentFile.fromTreeUri(requireContext(), treeUri)
                    documentsTree?.let {

                        // TO DELETE : Show contents
/*                        Timber.i( "documentsTree.uri=${documentsTree.uri}")
                        Timber.i( "documentsTree=$documentsTree name:${documentsTree.name} type:${documentsTree.type} isDirectory:${documentsTree.isDirectory}")
                        val childDocuments = documentsTree.listFiles()
                        for (docFile in childDocuments) {
                            Timber.i( "   => docFile = $docFile name:${docFile.name} type:${docFile.type} isDirectory:${docFile.isDirectory} uri:${docFile.uri}")
                        }*/
                        // END TO DELETE

                        // Check if it's a directory (should be...)
                        if (documentsTree.isDirectory) {
                            // Save this uri in PersistableUriPermission (keep only one, so delete the other ones)
                            releasePermissionsInSharedStorage()

                            requireContext().contentResolver.takePersistableUriPermission(
                                treeUri,
                                flags //Intent.FLAG_GRANT_READ_URI_PERMISSION //or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )

                            // Convert treeUri in a usable one
                            val id = DocumentsContract.getTreeDocumentId(treeUri)
                            val trueUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

                            rootTreeUri = trueUri
                            viewModel.setPrefRootTreeUri(trueUri)
                            App.rootTreeUri = trueUri

                            // Update the view model
                            viewModel.init(trueUri)

                        }
                    }
                }
            }
        } else {
            Timber.w("registerForActivityResult NOT OK !")
        }
    }

    private fun releasePermissionsInSharedStorage() {
        val perms = requireContext().contentResolver.persistedUriPermissions
        for (perm in perms) {
            Timber.i("releaseOnePermission -> releasing ${perm.uri.path}}")
            requireContext().contentResolver.releasePersistableUriPermission(perm.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            break
        }
    }
    // End permissions


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
            is BrowserViewModelState.ComicLoading -> handleStateLoading(state.currentTreeUri)
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
        Timber.i("handleStateInit state.rootUriPath=${state.rootUriPath} state.lastComicUri=${state.lastComicUri}")

        if (state.currentTreeUri == null) {
            askTreeUriPermission()
        } else {
            Timber.i("     state.lastComicUri=${state.lastComicUri}")
            initBrowser(state.currentTreeUri, state.lastComicUri, state.prefCurrentPage)
        }
    }

    private fun handleStateLoading(treeUri: Uri?) {
        Timber.i("handleStateLoading")
        if (treeUri != null) {
            Timber.i("handleStateLoading "+treeUri)
            viewModel.setAppCurrentTreeUri(treeUri)

            // Stop all loading...
            ComicLoadingManager.getInstance().clean()
        }
    }

    private fun handleStateReady(state: BrowserViewModelState.ComicReady) {
        Timber.i("handleStateReady")
        Timber.i("  state.comics=${state.comics}")

        IdleController.getInstance().resetIdleTimer()

        supportActionBar.title = getLocalDirName(rootTreeUri, App.currentTreeUri)

        viewModel.setPrefLastComicUri(null)      // Forget the last comic...

        disableMultiSelect()

        comics.clear()
        comics.addAll(state.comics)
        browserAdapter.notifyDataSetChanged()
        binding.recyclerView.scrollToPosition(0)

        IdleController.getInstance().resetIdleTimer()
    }

    private fun initBrowser(treeUri: Uri, lastUri:Uri?, prefCurrentPage:String) {
        Timber.d("initBrowser treeUri=$treeUri lastComic=$lastUri prefCurrentPage=$prefCurrentPage App.currentTreeUri=${App.currentTreeUri}")
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

        if (App.currentTreeUri == null) {
            // It's the first time we come in this fragment

            // Ask if we should use the last comic
            val lastComic = getComicFromUri(requireContext(), lastUri, true)
            if (lastComic != null) {
                lastComicUri = lastUri
            }

            if (lastComic != null) {

                // Continue reading from where you last left off "....." ?
                val alert = AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.ask_continue_with_same_comic)+ " ("+lastComic.name+")")
                    .setPositiveButton(R.string.ok) { _,_ ->
                        Timber.i("comic.parentTreeUriPath = ${lastComic.parentUriPath}")
                        App.currentTreeUri = Uri.parse(lastComic.parentUriPath)

                        // Call the fragment to view the last comic
                        var currentPage = 0
                        if (prefCurrentPage != "") {
                            currentPage = prefCurrentPage.toInt()
                        }

                        goPageSliderFragment(lastComic, currentPage)
                    }
                    .setNegativeButton(android.R.string.cancel) { _,_ ->
                        viewModel.setPrefLastComicUri(lastUri)      // Forget the last comic...
                        loadComics(rootTreeUri)
                    }
                    .setCancelable(false)
                    .create()
                alert.show()
            } else {
                loadComics(rootTreeUri)
            }

        } else {
            //
            loadComics(App.currentTreeUri!!)
        }
    }

    // Ask the viewModel to load comics informations in a given directory
    private fun loadComics(treeUri:Uri) {
        Timber.v("loadComics "+treeUri)
        viewModel.loadComics(treeUri)
    }

    override fun onComicEntryClicked(comic: ComicEntry, position:Int) {
        Timber.v("onComicEntryClicked "+comic.uri)
        if (comic.isDirectory) {
            Timber.i("Directory !")

            loadComics(comic.uri!!)
        } else {
            Timber.i("File ${comic.uri} !")
            lastComicUri= comic.uri
            viewModel.setPrefLastComicUri(comic.uri)

            goPageSliderFragment(comic, comic.currentPage)
        }
    }

    override fun onComicEntryLongClicked(comic: ComicEntry, position:Int) {
        Timber.v("onComicEntryLongClicked "+comic.uri)

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
            .setPositiveButton(R.string.ok) { _,_ -> askTreeUriPermission() }
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
                message += "\n - "+comics[cpt].name
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

    private fun goPageSliderFragment(comic:ComicEntry, page:Int) {
//        supportActionBar.hide() // Do this or else the image height will be detected with the supportActionBar height !!
        val action = BrowserFragmentDirections.actionBrowserFragmentToPageSliderFragment(comic, page)
        findNavController().navigate(action)
    }

    private fun goSettings() {
        val action = BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
        findNavController().navigate(action)
    }

    private fun showAboutPopup() {
        val title = App.appName+" "+ App.packageInfo.versionName
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
                    loadComics(App.currentTreeUri!!)
                }
            }
            .show()
    }


    // The button back is pressed, so can we move to the parent directory?
    // Returns true if and only if we can
    private fun handleBackPressedToChangeDirectory():Boolean {
        if (::rootTreeUri.isInitialized)
            Timber.d("handleBackPressedToChangeDirectory - current=${App.currentTreeUri} (root=$rootTreeUri)")
        else
            Timber.d("handleBackPressedToChangeDirectory - current=${App.currentTreeUri} (root=uninitialized)")

        return if (isFilteredMode) {
            setFilterMode(false)
            true
//        } else if (App.currentTreeUri?.parentFile == null || App.currentTreeUri?.absolutePath == rootDirectory.absolutePath) {
        } else if (!::rootTreeUri.isInitialized) {
            false
        } else if(App.currentTreeUri == rootTreeUri) {
            false
        } else {
            // Find the parent uri, if any
            val parentPath = getParentUriPath(App.currentTreeUri!!)
            if (parentPath != "") {
                val parentUri = Uri.parse(parentPath)
                loadComics(parentUri)
                true
            } else
                false
        }
    }
}