package fr.nourry.mynewkomik.pageslider

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.*
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
import androidx.viewpager.widget.ViewPager
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.databinding.FragmentPageSliderBinding
import fr.nourry.mynewkomik.dialog.DialogComicLoading
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import timber.log.Timber
import java.util.concurrent.Executors


private const val TAG_DIALOG_COMIC_LOADING = "LoadingComicDialog"

class PageSliderFragment: Fragment(), ViewPager.OnPageChangeListener  {

    val PAGE_SELECTOR_ANIMATION_DURATION = 300L
    val STATE_CURRENT_PAGE = "state:current_page"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity?.actionBar?.hide()

        _binding = FragmentPageSliderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        val args = PageSliderFragmentArgs.fromBundle(requireArguments())
        currentComic = args.comic
        currentPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE) ?: args.currentPage


        viewModel = ViewModelProvider(this)[PageSliderViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner) {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        }
        viewModel.initialize(currentComic, currentPage/*, savedInstanceState == null*/)

        // LiveData for the ViewModel :
        //  NOTE: Observer needs a livecycle owner that is not accessible by the ViewModel directly, so to observe a liveData, our ViewModel observers uses this Fragment...
        viewModel.comicEntriesFromDAO.observe(viewLifecycleOwner) { comicEntriesFromDAO ->
            Timber.w("UPDATED::comicEntriesFromDAO=$comicEntriesFromDAO")
            viewModel.updateComicEntriesFromDAO(comicEntriesFromDAO)
        }
        // End LiveDatas

        // Replace the title
        supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        supportActionBar.title = currentComic.file.name
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val previousItem = menu.findItem(R.id.action_go_previous)
        previousItem.isVisible = viewModel.hasPreviousComic()

        val nextItem = menu.findItem(R.id.action_go_next)
        nextItem.isVisible = viewModel.hasNextComic()

        super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_page_slider_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_go_previous -> {
                val newComic = viewModel.getPreviousComic()
                if (newComic != null && newComic != currentComic) {
                    changeCurrentComic(newComic)
                }
                true
            }
            R.id.action_go_next -> {
                val newComic = viewModel.getNextComic()
                if (newComic != null && newComic != currentComic) {
                    changeCurrentComic(newComic)
                }
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun changeCurrentComic(comic:ComicEntry) {
        Timber.i("changeCurrentComic")
        currentComic = comic
        viewModel.changeCurrentComic(currentComic, comic.currentPage)
        pageSliderAdapter.setNewComic(comic)
        if (::pageSelectorSliderAdapter.isInitialized) {
            pageSelectorSliderAdapter.setNewComic(comic)
        }
    }

    override fun onDestroyView() {
        supportActionBar.show()
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
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
            else -> {}
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
        Timber.i("handleStateReady nbPages=${state.comic.nbPages} currentPage=${state.currentPage} shouldUpdateAdapters=${state.shouldUpdateAdapters}")
        var shouldUpdatePageSliderAdapter = state.shouldUpdateAdapters
        val shouldUpdatePageSelectorSliderAdapter = state.shouldUpdateAdapters

        supportActionBar.hide()

        if (binding.cachePageSelectorLayout.visibility == View.VISIBLE) {
            hidePageSelector()
        }

        if (dialogComicLoading.isAdded) {
            dialogComicLoading.dismiss()
        }

        if (!::pageSliderAdapter.isInitialized) {
            pageSliderAdapter = PageSliderAdapter(requireContext(), viewModel, state.comic)

            binding.viewPager.adapter = pageSliderAdapter

            binding.viewPager.addOnPageChangeListener(this)
            shouldUpdatePageSliderAdapter = true
            pageSliderAdapter.notifyDataSetChanged()
        } else {
            if (binding.viewPager.currentItem != state.currentPage) {
                pageSliderAdapter.onPageChanged()
            }
        }

        if (shouldUpdatePageSliderAdapter)  pageSliderAdapter.notifyDataSetChanged()

        if (binding.viewPager.currentItem != state.currentPage) {
            val oldPage = binding.viewPager.currentItem
            val newPage = state.currentPage
            binding.viewPager.setCurrentItem(state.currentPage, false)
            if (::pageSelectorSliderAdapter.isInitialized && !shouldUpdatePageSelectorSliderAdapter) {
                pageSelectorSliderAdapter.notifyItemChanged(oldPage)
                pageSelectorSliderAdapter.notifyItemChanged(newPage)
            }
        }
        if (::pageSelectorSliderAdapter.isInitialized && shouldUpdatePageSelectorSliderAdapter)  pageSelectorSliderAdapter.notifyDataSetChanged()

        binding.viewPager.visibility = View.VISIBLE
    }

    private fun handleStatePageSelection(state: PageSliderViewModelState.PageSelection) {
        Timber.i("handleStatePageSelection currentPage=${state.currentPage}")

        // Ask to refresh the menu (for the previous and next items...)
        requireActivity().invalidateOptionsMenu()


        if (!::pageSelectorSliderAdapter.isInitialized) {
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
        }

        binding.recyclerViewPageSelector.scrollToPosition(state.currentPage)

        showPageSelector()
    }

    private fun showPageSelector() {
        Timber.d("showPageSelector")

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

        val animAlpha = ObjectAnimator.ofFloat(binding.cachePageSelectorLayout, "alpha", 0f)
        animAlpha.duration = PAGE_SELECTOR_ANIMATION_DURATION

        animMove.start()
        animAlpha.start()

        Executors.newSingleThreadExecutor().execute {
            Thread.sleep(PAGE_SELECTOR_ANIMATION_DURATION)
            binding.cachePageSelectorLayout.visibility = View.INVISIBLE
        }
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
                .setMessage(R.string.no_next_issue)
                .setPositiveButton(R.string.ok) { _,_ -> }
                .setCancelable(true)
                .create()
            } else {
                AlertDialog.Builder(requireContext())
                            .setMessage("Begin reading next issue?")
                            .setPositiveButton(R.string.ok) { _, _ ->
                                val newComic = viewModel.getNextComic()
                                Timber.d("    newComic=$newComic")
                                if (newComic != null && newComic != currentComic) {
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
                .setMessage(R.string.no_previous_issue)
                .setPositiveButton(R.string.ok) { _,_ -> }
                .setCancelable(true)
                .create()
        } else {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.ask_read_previous_issue)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newComic = viewModel.getPreviousComic()
                    Timber.d("    newComic=$newComic")
                    if (newComic != null && newComic != currentComic) {
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

    override fun onPageScrollStateChanged(state: Int) {
        Timber.i("onPageScrollStateChanged state = $state")
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            lastPageBeforeScrolling = currentPage
        } else if (state == ViewPager.SCROLL_STATE_IDLE) {
            Timber.w("   lastPageBeforeScrolling=$lastPageBeforeScrolling currentPage=$currentPage")
            // Compare if we change the page
            if (lastPageBeforeScrolling == currentPage) {
                // We couldn't change the current page
/*                if (scrollingDirection>0) {
                    Timber.w("LAST PAGE: ASK NEW ONE ?")
                } else if (scrollingDirection<0) {
                    Timber.w("FIRST PAGE: ASK PREVIOUS ONE ?")
                }*/
                if (currentPage == 0) {
                    // TODO to change : bug if there only one page in the comic !
                    Timber.w("FIRST PAGE: ASK PREVIOUS ONE ?")
                    askPreviousComic()
                } else if (currentPage == (viewModel.currentComic?.nbPages ?: -1) -1) {
                    Timber.w("LAST PAGE: ASK NEW ONE ?")
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
            if (lastPositionOffsetPixel > positionOffsetPixels) {
                scrollingDirection = -1
            } else if (lastPositionOffsetPixel < positionOffsetPixels) {
                scrollingDirection = +1
            } else {
                scrollingDirection = 0
            }
            Timber.w("    scrollingDirection=$scrollingDirection   positionOffsetPixels=$positionOffsetPixels")
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
        showOrUpdateToast("Page ${position+1} of ${currentComic.nbPages}")
        viewModel.onSetCurrentPage(position)
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


}
