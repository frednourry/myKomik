package fr.nourry.mykomik.pageslider

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager.widget.ViewPager
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.databinding.FragmentPageSliderBinding
import fr.nourry.mykomik.dialog.DialogComicLoading
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.settings.UserPreferences
import fr.nourry.mykomik.utils.getDocumentFileFromUri
import fr.nourry.mykomik.utils.getLocalDirName
import fr.nourry.mykomik.utils.getReadableDate
import fr.nourry.mykomik.utils.getSizeInMo
import timber.log.Timber
import java.io.IOException


private const val TAG_DIALOG_COMIC_LOADING = "LoadingComicDialog"

class PageSliderFragment: Fragment(), ViewPager.OnPageChangeListener, PageSliderAdapter.Listener  {

    val PAGE_SELECTOR_ANIMATION_DURATION = 300L         // in milliseconds
    val STATE_CURRENT_COMIC = "state:current_comic"
    val STATE_CURRENT_PAGE = "state:current_page"

    companion object {
        var PERMISSIONS_TO_WRITE = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private lateinit var pageSliderAdapter: PageSliderAdapter
    private lateinit var pageSelectorSliderAdapter: PageSelectorSliderAdapter
    private var currentPage = 0
    private lateinit var currentComic:ComicEntry
    private lateinit var viewModel: PageSliderViewModel
    private lateinit var supportActionBar: ActionBar

    private lateinit var toast: Toast

    private var dialogComicLoading:DialogComicLoading = DialogComicLoading.newInstance()

    // Test for View Binding (replace 'kotlin-android-extensions')
    private var _binding: FragmentPageSliderBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Informations when scrolling
    private var lastPageBeforeScrolling = 0
    private var lastPositionOffsetPixel = -1
    private var scrollingDirection = 0

    // Information on the last current image information
    private var lastImageScale = 1f
    private var lastImageOffsetX = 0f
    private var lastImageOffsetY = 0f
    private var lastMatrixValues : FloatArray = FloatArray(9)

    private var bRefreshSliderAdapter = false
    private var bRefreshSelectorSliderAdapter = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        activity?.actionBar?.hide()

        _binding = FragmentPageSliderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


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
                menuInflater.inflate(R.menu.menu_page_slider_fragment, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val previousItem = menu.findItem(R.id.action_go_previous)
                previousItem.isVisible = viewModel.hasPreviousComic()

                val nextItem = menu.findItem(R.id.action_go_next)
                nextItem.isVisible = viewModel.hasNextComic()

                super.onPrepareMenu(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                return when (menuItem.itemId) {
                    R.id.action_settings -> {
                        goSettings()
                        true
                    }
                    R.id.action_go_previous -> {
                        val newComic = viewModel.getPreviousComic()
                        if (newComic != null && newComic != currentComic) {
                            changeCurrentComic(newComic)
                        }
                        true
                    }
                    R.id.action_save -> {
                        popupSaveCurrentPage(currentPage)
                        true
                    }
                    R.id.action_go_next -> {
                        val newComic = viewModel.getNextComic()
                        if (newComic != null && newComic != currentComic) {
                            changeCurrentComic(newComic)
                        }
                        true
                    }
                    R.id.action_information -> {
                        showComicInformationPopup()
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

            if (!handleBackPressed() && !NavHostFragment.findNavController(thisFragment).popBackStack()) {
                Timber.d("    PAS DE RETOUR EN STACK !!")
                activity?.finish()
            }
        }

        ComicLoadingManager.getInstance().setLivecycleOwner(this)

        if (::currentComic.isInitialized) {
            // We were already here, so no need to use PageSliderFragmentArgs.fromBundle(requireArguments())
            Timber.w("Using last value of currentComic($currentComic). Not using args.")
            bRefreshSelectorSliderAdapter = true
            bRefreshSliderAdapter = true
        } else {
            Timber.i("Using args to set currentComic and currentPage")
            val args = PageSliderFragmentArgs.fromBundle(requireArguments())

            // Check if a comic path was saved in savedInstanceState[STATE_CURRENT_COMIC] (when rotating for example)
            val currentComicPath = savedInstanceState?.getString(STATE_CURRENT_COMIC) ?: ""
            if (currentComicPath == "") {
                currentComic = args.comic
            } else {
                val uri = Uri.parse(currentComicPath)
                val docFile = getDocumentFileFromUri(App.appContext, uri)
                currentComic = if (docFile != null && docFile.isFile) {
                    ComicEntry.createFromDocFile(docFile)
                } else {
                    args.comic
                }
            }
            currentPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE) ?: args.currentPage
            bRefreshSelectorSliderAdapter = false
            bRefreshSliderAdapter = true
        }

        viewModel = ViewModelProvider(this)[PageSliderViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner) {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        }

        viewModel.initialize(currentComic, currentPage/*, savedInstanceState == null*/)

        // LiveData for the ViewModel :
        //  NOTE: Observer needs a livecycle owner that is not accessible by the ViewModel directly, so to observe a liveData, our ViewModel observers uses this Fragment...
        viewModel.comicEntriesFromDAO.observe(viewLifecycleOwner) { comicEntriesFromDAO ->
//            Timber.w("UPDATED::comicEntriesFromDAO=$comicEntriesFromDAO")
            viewModel.updateComicEntriesFromDAO(App.appContext, comicEntriesFromDAO)
        }
        // End LiveDatas

        // Replace the title
        supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        supportActionBar.title = currentComic.name
        supportActionBar.setDisplayHomeAsUpEnabled(false)
/*        supportActionBar.setLogo(R.mipmap.ic_launcher)
        supportActionBar.setDisplayUseLogoEnabled(true)*/
    }


    private fun changeCurrentComic(comic:ComicEntry) {
        Timber.i("changeCurrentComic")
        currentComic = comic
        if (comic.docFile == null) {
            Timber.w("docFile is null !!")
        }
        viewModel.changeCurrentComic(currentComic, comic.currentPage)
        pageSliderAdapter.setNewComic(comic)
        if (::pageSelectorSliderAdapter.isInitialized) {
            pageSelectorSliderAdapter.setNewComic(comic)
        }

        // Update the bar
        supportActionBar.title = currentComic.name
    }

    override fun onDestroyView() {
        supportActionBar.show()
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_COMIC, currentComic.path)
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
    }

    private fun resetLastImageParameters() {
        Timber.d("resetLastImageParameters")
        lastImageScale = 1f
        lastImageOffsetX = 0f
        lastImageOffsetY = 0f
    }

    // Update UI according to the model state events
    private fun updateUI(state: PageSliderViewModelState) {
        Timber.i("Calling updateUI, switch state=${state::class}")
        return when(state) {
            is PageSliderViewModelState.Error -> handleStateError(state)
            is PageSliderViewModelState.Init -> handleStateInit(state)
            is PageSliderViewModelState.Loading -> handleStateLoading(state)
            is PageSliderViewModelState.Ready -> handleStateReady(state)
            is PageSliderViewModelState.PageSelection -> handleStatePageSelection(state)
            is PageSliderViewModelState.Cleaned -> handleStateCleaned(state)
        }
    }

    private fun handleStateCleaned(state: PageSliderViewModelState.Cleaned) {
        Timber.i("handleStateCleaned")
    }

    private fun handleStateLoading(state: PageSliderViewModelState.Loading) {
        Timber.i("handleStateLoading")

        binding.viewPager.visibility = View.INVISIBLE

        dialogComicLoading.isCancelable = false
        if (!dialogComicLoading.isAdded) {
            dialogComicLoading.show(parentFragmentManager, TAG_DIALOG_COMIC_LOADING)
        }
        dialogComicLoading.setProgress(state.currentItem, state.nbItem)
    }

    private fun handleStateReady(state: PageSliderViewModelState.Ready) {
        Timber.i("handleStateReady nbPages=${state.comic.nbPages} currentPage=${state.currentPage} shouldUpdateAdapters=${state.shouldUpdateAdapters} bRefreshSliderAdapter=$bRefreshSliderAdapter bRefreshSelectorSliderAdapter=$bRefreshSelectorSliderAdapter")
        var shouldUpdatePageSliderAdapter = state.shouldUpdateAdapters
        val shouldUpdatePageSelectorSliderAdapter = state.shouldUpdateAdapters

        supportActionBar.hide()

        if (binding.cachePageSelectorLayout.visibility == View.VISIBLE) {
            hidePageSelector()
        }

        if (dialogComicLoading.isAdded) {
            dialogComicLoading.dismiss()
        }

        if (!::pageSliderAdapter.isInitialized || bRefreshSliderAdapter) {
            // Trick for Right To Left reading:
            // As ViewPager doesn't support "layoutDirection",
            //  - we should inverse the viewPager (viewPager.rotationY = 180F)
            //  - we should inverse each item of the recyclerView (item.rotationY = 180F)
            val isLTR = UserPreferences.getInstance(requireContext()).isReadingDirectionLTR()
            pageSliderAdapter = PageSliderAdapter(requireContext(), viewModel, state.comic, isLTR)
            pageSliderAdapter.setPageSliderAdapterListener(this)

            binding.viewPager.adapter = pageSliderAdapter

/*
            // If use this, comment "onPageScrolled()" !!
            binding.viewPager.setPageTransformer(true)  { _, position ->
                Timber.w("setPageTransformer $position")
                if (position < -1) {
                    // [-00,-1): the page is way off-screen to the left.
                    Timber.w("  setPageTransformer finger LEFT")
                    scrollingDirection = 1
                } else if (position <= 1) {
                    // [-1,1]: the page is "centered"
                    Timber.w("  setPageTransformer finger CENTERED")
                } else {
                    // (1,+00]: the page is way off-screen to the right.
                    Timber.w("  setPageTransformer finger RIGHT")
                    scrollingDirection = -1
                }
            }
 */
            if (!isLTR) binding.viewPager.rotationY = 180F

            // Avoid screen rotation, if asked
            val isRotationDisabled = UserPreferences.getInstance(requireContext()).isRotationDisabled()
            if (isRotationDisabled)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

            binding.viewPager.addOnPageChangeListener(this)
            shouldUpdatePageSliderAdapter = true
            pageSliderAdapter.notifyDataSetChanged()
            bRefreshSliderAdapter = false
        } else {
            if (binding.viewPager.currentItem != state.currentPage) {
                pageSliderAdapter.onPageChanged()
                shouldUpdatePageSliderAdapter = true
            }


            Timber.i("PLOP ${binding.viewPager.currentItem} ${state.currentPage} $lastImageScale $lastImageOffsetX $lastImageOffsetY")
            MagnifyImageView.printFloatArray(lastMatrixValues, "  PLOP :: ")
//            if (lastImageScale != 1f && lastImageOffsetX != 0f && lastImageOffsetY != 0f) {
                pageSliderAdapter.setImageToUpdateParameters(state.currentPage, lastImageScale, lastImageOffsetX, lastImageOffsetY, lastMatrixValues)
                resetLastImageParameters()
//            }

        }

        if (shouldUpdatePageSliderAdapter) {
            pageSliderAdapter.notifyDataSetChanged()
        }

        if (binding.viewPager.currentItem != state.currentPage) {
            val oldPage = binding.viewPager.currentItem
            val newPage = state.currentPage
            binding.viewPager.setCurrentItem(state.currentPage, false)
            if (::pageSelectorSliderAdapter.isInitialized) {
                pageSelectorSliderAdapter.notifyItemChanged(oldPage)
                pageSelectorSliderAdapter.notifyItemChanged(newPage)
            }
        }
        if (::pageSelectorSliderAdapter.isInitialized && shouldUpdatePageSelectorSliderAdapter)
            pageSelectorSliderAdapter.notifyDataSetChanged()

        binding.viewPager.visibility = View.VISIBLE
    }

    private fun handleStatePageSelection(state: PageSliderViewModelState.PageSelection) {
        Timber.i("handleStatePageSelection currentPage=${state.currentPage}")

        // Ask to refresh the menu (for the previous and next items...)
        requireActivity().invalidateOptionsMenu()


        if (!::pageSelectorSliderAdapter.isInitialized || bRefreshSelectorSliderAdapter) {
            pageSelectorSliderAdapter = PageSelectorSliderAdapter(viewModel, state.comic)

            binding.recyclerViewPageSelector.layoutManager = GridLayoutManager(requireContext(), 1)  // Should be higher, but cool result so keep it...
            binding.recyclerViewPageSelector.adapter = pageSelectorSliderAdapter
            pageSelectorSliderAdapter.notifyDataSetChanged()
            binding.recyclerViewPageSelector.scrollToPosition(state.currentPage)

            binding.recyclerViewPageSelector.overScrollMode = View.OVER_SCROLL_ALWAYS

            binding.cachePageSelectorLayout.setOnClickListener {
                Timber.d("onClick background")
                viewModel.cancelPageSelector()
            }
            binding.buttonGoFirst.setOnClickListener {
                Timber.d("onClick buttonGoFirst")
                binding.recyclerViewPageSelector.scrollToPosition(0)
            }
            binding.buttonGoLast.setOnClickListener {
                Timber.d("onClick buttonGoLast")
                binding.recyclerViewPageSelector.scrollToPosition(currentComic.nbPages-1)
            }
            bRefreshSelectorSliderAdapter = false
        }

        binding.recyclerViewPageSelector.scrollToPosition(state.currentPage)

        showPageSelector()
    }

    private fun showPageSelector() {
        Timber.d("showPageSelector")

        // Hide/show buttons?
        val measuredHeight = binding.recyclerViewPageSelector.measuredHeight    // Save measuredHeight before recompute it
        binding.recyclerViewPageSelector.measure(View.MeasureSpec.makeMeasureSpec(binding.recyclerViewPageSelector.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val pageSelectorTotalHeight = binding.recyclerViewPageSelector.measuredHeight
        Timber.v("   measuredHeight = $measuredHeight pageSelectorTotalHeight = $pageSelectorTotalHeight")
        if (pageSelectorTotalHeight<measuredHeight) {
            Timber.v("   showPageSelector :: hide buttons")
            binding.buttonGoFirst.visibility = View.INVISIBLE
            binding.buttonGoLast.visibility = View.INVISIBLE
        } else {
            Timber.v("   showPageSelector :: show buttons")
            binding.buttonGoFirst.visibility = View.VISIBLE
            binding.buttonGoLast.visibility = View.VISIBLE
        }

        // Animation
        binding.pageSelectorLayout.x = -(binding.pageSelectorLayout.width.toFloat())
        val animMove = ObjectAnimator.ofFloat(binding.pageSelectorLayout, "x", 0f)
        animMove.duration = PAGE_SELECTOR_ANIMATION_DURATION

        binding.cachePageSelectorLayout.alpha = 0F
        val animAlpha = ObjectAnimator.ofFloat(binding.cachePageSelectorLayout, "alpha", 100f)
        animAlpha.duration = PAGE_SELECTOR_ANIMATION_DURATION

        animMove.start()
        animAlpha.start()

        binding.cachePageSelectorLayout.visibility = View.VISIBLE
        supportActionBar.show()
    }

    private fun hidePageSelector() {
        Timber.d("hidePageSelector")
        supportActionBar.hide()

        val animMove = ObjectAnimator.ofFloat(binding.pageSelectorLayout, "x", -binding.pageSelectorLayout.width.toFloat())
        animMove.duration = PAGE_SELECTOR_ANIMATION_DURATION
        animMove.doOnEnd {
            binding.cachePageSelectorLayout.visibility = View.INVISIBLE
        }

        val animAlpha = ObjectAnimator.ofFloat(binding.cachePageSelectorLayout, "alpha", 0f)
        animAlpha.duration = PAGE_SELECTOR_ANIMATION_DURATION

        animMove.start()
        animAlpha.start()
    }

    private fun handleStateInit(state: PageSliderViewModelState.Init) {
        Timber.i("handleStateInit")
    }

    private fun handleStateError(state: PageSliderViewModelState.Error) {
        Timber.i("handleStateError")
        dialogComicLoading.dismiss()
        val alert = AlertDialog.Builder(requireContext())
            .setMessage("This file is not valid !")
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _,_ ->
                findNavController().popBackStack()
            }
            .create()
        alert.show()
    }

    private fun handleBackPressed(): Boolean {
        return if (binding.cachePageSelectorLayout.visibility == View.VISIBLE) {
            // Hide the PageSelector
            viewModel.cancelPageSelector()
            true
        } else
            false
    }

    private fun askNextComic() {
        Timber.d("askNextComic")
        val thisFragment = this
        val alert: AlertDialog = if (!viewModel.hasNextComic()) {
            AlertDialog.Builder(requireContext())
                .setMessage(resources.getString(R.string.no_next_issue))
                .setPositiveButton(R.string.ok) { _,_ -> }
                .setCancelable(true)
                .create()
            } else {
                AlertDialog.Builder(requireContext())
                            .setMessage(resources.getString(R.string.ask_read_next_issue))
                            .setPositiveButton(R.string.ok) { _, _ ->
                                val newComic = viewModel.getNextComic()
                                Timber.d("    newComic=$newComic")
                                if (newComic != null && newComic != currentComic) {
                                    resetLastImageParameters()
                                    changeCurrentComic(newComic)
                                }
                            }
                            .setNeutralButton(R.string.back_to_browser) { _, _ -> NavHostFragment.findNavController(thisFragment).popBackStack(R.id.browserFragment, false) }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .setCancelable(true)
                            .create()
            }
        alert.show()
    }

    private fun askPreviousComic() {
        Timber.d("askPreviousComic")
        val thisFragment = this
        val alert: AlertDialog = if (!viewModel.hasPreviousComic()) {
            AlertDialog.Builder(requireContext())
                .setMessage(resources.getString(R.string.no_previous_issue))
                .setPositiveButton(R.string.ok) { _,_ -> }
                .setCancelable(true)
                .create()
        } else {
            AlertDialog.Builder(requireContext())
                .setMessage(resources.getString(R.string.ask_read_previous_issue))
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newComic = viewModel.getPreviousComic()
                    Timber.d("    newComic=$newComic")
                    if (newComic != null && newComic != currentComic) {
                        resetLastImageParameters()
                        changeCurrentComic(newComic)
                    }
                }
                .setNeutralButton(R.string.back_to_browser) { _, _ -> NavHostFragment.findNavController(thisFragment).popBackStack(R.id.browserFragment, false) }
                .setNegativeButton(R.string.no) { _, _ -> }
                .setCancelable(true)
                .create()
        }
        alert.show()
    }

    private fun goSettings() {
        val action = PageSliderFragmentDirections.actionPageSliderFragmentToSettingsFragment()
        findNavController().navigate(action)
    }

    override fun onPageTap(currentMagnifyImageView:MagnifyImageView, currentPage:Int, x:Float, y:Float) {
        Timber.i("onPageTap x=$x y=$y")

        if (UserPreferences.getInstance(requireContext()).isTappingToChangePage()) {
            // Check if the tap is near the border
            val width = App.physicalConstants.metrics.widthPixels
            val directionLTR = UserPreferences.getInstance(requireContext()).isReadingDirectionLTR()
            Timber.i("   onPageTap "+(width*0.1)+" < $x < "+(width*0.9))

            if (x<width*0.1) {
                if (directionLTR)
                    scrollToPreviousPage()
                else
                    scrollToNextPage()
                return
            }
            if (x>width*0.9) {
                if (directionLTR)
                    scrollToNextPage()
                else
                    scrollToPreviousPage()

                return
            }
        }

        // Save current image informations
        Timber.v("currentMagnifyImageView:: scale=${currentMagnifyImageView.getCurrentScale()} offsetx=${currentMagnifyImageView.getOffsetX()} offsety=${currentMagnifyImageView.getOffsetY()}")
        lastImageScale = currentMagnifyImageView.getCurrentScale()
        lastImageOffsetX = currentMagnifyImageView.getOffsetX()
        lastImageOffsetY = currentMagnifyImageView.getOffsetY()
        lastMatrixValues = currentMagnifyImageView.getMatrixValues()

        viewModel.showPageSelector(currentPage)
    }

    private fun scrollToNextPage() {
        Timber.i("scrollToNextPage:: want to go to page "+(currentPage+1)+"/"+(currentComic.nbPages))
        if (currentPage+1<currentComic.nbPages) {
            lastPageBeforeScrolling = currentPage   // Set manually 'lastPageBeforeScrolling' before ask the scrolling
            resetLastImageParameters()
            binding.viewPager.setCurrentItem(currentPage+1, true)
        } else {
            askNextComic()
        }
    }

    private fun scrollToPreviousPage() {
        Timber.i("scrollToPreviousPage:: want to go to page "+(currentPage-1)+"/"+(currentComic.nbPages))
        if (currentPage-1>=0) {
            lastPageBeforeScrolling = currentPage   // Set manually 'lastPageBeforeScrolling' before ask the scrolling
            resetLastImageParameters()
            binding.viewPager.setCurrentItem(currentPage-1, true)
        } else {
            askPreviousComic()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        Timber.i("onPageScrollStateChanged state = $state")
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            lastPageBeforeScrolling = currentPage
        } else if (state == ViewPager.SCROLL_STATE_IDLE) {
            Timber.i("   lastPageBeforeScrolling=$lastPageBeforeScrolling currentPage=$currentPage")
            // Compare if we change the page
            if (lastPageBeforeScrolling == currentPage) {
                // We couldn't change the current page
/*                if (scrollingDirection>0) {
                    Timber.i("LAST PAGE: ASK NEW ONE ?")
                } else if (scrollingDirection<0) {
                    Timber.i("FIRST PAGE: ASK PREVIOUS ONE ?")
                }*/
                if (currentPage == 0) {
                    // TODO to change : bug if there only one page in the comic !
                    Timber.i("FIRST PAGE: ASK PREVIOUS ONE ?")
                    askPreviousComic()
                } else if (currentPage == (viewModel.currentComic?.nbPages ?: -1) -1) {
                    Timber.i("LAST PAGE: ASK NEW ONE ?")
                    askNextComic()
                }
            } else {
                // Informs the pageSliderAdapter that the page was changed
                pageSliderAdapter.onPageChanged()
            }
            lastPositionOffsetPixel = -1
            scrollingDirection = 0
        }
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        Timber.i("onPageScrolled position = $position positionOffset=$positionOffset positionOffsetPixels=$positionOffsetPixels")
        if (lastPositionOffsetPixel > 0) {
            scrollingDirection = if (lastPositionOffsetPixel > positionOffsetPixels) {
                -1
            } else if (lastPositionOffsetPixel < positionOffsetPixels) {
                +1
            } else {
                0
            }
            Timber.i("    scrollingDirection=$scrollingDirection   positionOffsetPixels=$positionOffsetPixels")
        } else {
            scrollingDirection = 0
        }
        lastPositionOffsetPixel = positionOffsetPixels
    }

    override fun onPageSelected(position: Int) {
        Timber.i("onPageSelected currentPage = $position oldPosition = $currentPage")

        if (::pageSelectorSliderAdapter.isInitialized) {
            pageSelectorSliderAdapter.notifyItemChanged(currentPage)
            pageSelectorSliderAdapter.notifyItemChanged(position)
        }

        currentPage = position

        if (!UserPreferences.getInstance(requireContext()).shouldHidePageNumber()) {
            showOrUpdateToast(resources.getString(R.string.page_number_info, (position+1), currentComic.nbPages))
        }

        viewModel.onSetCurrentPage(position)

        lastImageScale = 1f
        lastImageOffsetX = 0f
        lastImageOffsetY = 0f

    }

    private fun showOrUpdateToast(text:String) {
        if (::toast.isInitialized) {
            toast.cancel()
        }
        toast = Toast.makeText(context, text, Toast.LENGTH_SHORT)
        toast.show()

/*
        // NOT WORKING !!
        if (::toast.isInitialized) {
            toast.cancel()
            toast.setText(text)
        } else {
            toast = Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT)
        }
        toast.show()*/
    }


    private fun showComicInformationPopup() {
        Timber.v("showComicInformationPopup $currentComic")
        if (currentComic.docFile == null && currentComic.uri!=null) {
            currentComic.setDocumentFile(getDocumentFileFromUri(App.appContext, currentComic.uri!!))
        }

        val title = getString(R.string.popup_info_title)
        val message = Html.fromHtml(
                        "<b>"+getString(R.string.popup_info_name)+" : </b>"+currentComic.name + "<br/>\n" +
                                "<b>"+getString(R.string.popup_info_path)+" : </b>"+getLocalDirName(App.currentTreeUri, currentComic.uri) + "<br/>\n" +
                                "<b>"+getString(R.string.popup_info_date) + " : </b>" + getReadableDate(currentComic.lastModified) + "<br/>\n" +
                                "<b>"+getString(R.string.popup_info_size)+" : </b>%.2f".format(getSizeInMo(currentComic.size)) + getString(R.string.unit_megabyte)
                        , 0)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _,_ ->
            }
            .create()
            .show()
    }

    // Popup to ask if the user want to save this page
    private fun simplePopup(title:String, message:String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _,_ ->
            }
            .create()
            .show()
    }

    // Ask the viewModel to save the current page AFTER the authorization are checked !
    private fun saveCurrentPageInPictureDirectory(numPage:Int) {
        val title = getString(R.string.save_page)
        var imageSavedPath = ""
        var message2 = ""
        try {
            imageSavedPath = viewModel.saveCurrentPageInPictureDirectory(currentComic, numPage)
            message2 = if (imageSavedPath == "") {
                getString(R.string.save_page_message_unknown_error)
            } else {
                getString(R.string.save_page_message_ok, imageSavedPath)
            }
        } catch (e:IOException) {
            message2 = getString(R.string.save_page_message_error_io, imageSavedPath, e.message)
            e.printStackTrace()
        }
        simplePopup(title, message2)
    }

    // Register permission callback
    var pageToSaveAfterRequest = 0  // To remember which page to save after asking permissions
    private val writingPermissionRequestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.entries.all {
            it.value == true
        }
        Timber.v("writingPermissionRequestLauncher: granted = $granted")
        if (granted) {
            saveCurrentPageInPictureDirectory(pageToSaveAfterRequest)
        } else {
            //display error dialog
            simplePopup(getString(R.string.save_page), "Error: You should give the authorization to write on your device if you want to save a page")
        }
    }

    // Popup to ask if the user want to save this page
    private fun popupSaveCurrentPage(numPage: Int) {
        val title = getString(R.string.save_page)
        val message = getString(R.string.save_page_message)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _,_ ->
                if (checkPermissionToSave()) {
                    saveCurrentPageInPictureDirectory(numPage)
                } else {
                    pageToSaveAfterRequest = numPage
                    writingPermissionRequestLauncher.launch(PERMISSIONS_TO_WRITE)
                }
            }
            .create()
            .show()
    }

    // Check if the permissions defined in PERMISSIONS_TO_WRITE are GRANTED
    private fun checkPermissionToSave() : Boolean {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            // After Q, no need to ask permission to write in PICTURES directory
            true
        else
            PERMISSIONS_TO_WRITE.all {
                ActivityCompat.checkSelfPermission(activity as Context, it) == PackageManager.PERMISSION_GRANTED
            }
    }
}
