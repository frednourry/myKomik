package fr.nourry.mynewkomik.pictureslider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mynewkomik.ComicPicture
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.dialog.DialogComicLoading
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.preference.PREF_CURRENT_PAGE_LAST_COMIC
import fr.nourry.mynewkomik.preference.SharedPref
import kotlinx.android.synthetic.main.fragment_picture_slider.*
import kotlinx.android.synthetic.main.fragment_picture_slider.coordinatorLayout
import timber.log.Timber

private const val TAG_DIALOG_COMIC_LOADING = "LoadingComicDialog"

class PictureSliderFragment: Fragment(), ViewPager.OnPageChangeListener  {

    val STATE_CURRENT_PAGE = "state:current_page"

    private lateinit var pictureSliderAdapter: PictureSliderAdapter
//    private lateinit var pictureSliderAdapter: PictureSliderAdapter2
    private var pictures = mutableListOf<ComicPicture>()
    private var currentPage = 0
    private lateinit var viewModel: PictureSliderViewModel

    private var dialogComicLoading:DialogComicLoading = DialogComicLoading.newInstance()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity?.actionBar?.hide()
        return inflater.inflate(R.layout.fragment_picture_slider, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val thisFragment = this
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Handle the back button event
            Timber.d("BACK PRESSED !!!!!!!")

            if (!handleBackPressedToChangeDirectory() && !NavHostFragment.findNavController(thisFragment).popBackStack()) {
                Timber.d("    PAS DE RETOUR EN STACK !!")
                activity?.finish()
            }
        }

        ComicLoadingManager.getInstance().setLivecycleOwner(this)

        // ViewPager2
//        pictureSliderAdapter = PictureSliderAdapter2(requireContext(), pictures)
        // ViewPager1
        pictureSliderAdapter = PictureSliderAdapter(requireContext(), pictures)
        viewPager.adapter = pictureSliderAdapter
        pictureSliderAdapter.notifyDataSetChanged()

        viewModel = ViewModelProvider(this)[PictureSliderViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner) {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        }

        val args = PictureSliderFragmentArgs.fromBundle(requireArguments())
        val comic = args.comic
        currentPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE) ?: args.currentPage

        viewModel.initialize(comic, currentPage, savedInstanceState == null)

        viewPager.addOnPageChangeListener(this)

        // Replace the title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = comic.file.name
    }

    override fun onDestroyView() {
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_PAGE, currentPage)
    }

    // Update UI according to the model state events
    private fun updateUI(state: PictureSliderViewModelState) {
        Timber.i("Calling updateUI, switch state=${state::class}")
        return when(state) {
            is PictureSliderViewModelState.Error -> handleStateError(state)
            is PictureSliderViewModelState.Init -> handleStateInit(state)
            is PictureSliderViewModelState.Loading -> handleStateLoading(state)
            is PictureSliderViewModelState.Ready -> handleStateReady(state)
            is PictureSliderViewModelState.Cleaned -> handleStateCleaned(state)
            else -> {}
        }
    }

    private fun handleStateCleaned(state: PictureSliderViewModelState.Cleaned) {
        Timber.i("handleStateCleaned")
    }

    private fun handleStateLoading(state: PictureSliderViewModelState.Loading) {
        Timber.i("handleStateLoading")
        dialogComicLoading.isCancelable = false
        if (!dialogComicLoading.isAdded) {
            dialogComicLoading.show(parentFragmentManager, TAG_DIALOG_COMIC_LOADING)
        }
        dialogComicLoading.setProgress(state.currentItem, state.nbItem)
    }

    private fun handleStateReady(state: PictureSliderViewModelState.Ready) {
        Timber.i("handleStateReady currentPage=${state.currentPage}")
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()

        dialogComicLoading.dismiss()
        pictures.clear()
        pictures.addAll(state.pictures)
        pictureSliderAdapter.notifyDataSetChanged()
        viewPager.currentItem = state.currentPage
    }

    private fun handleStateInit(state: PictureSliderViewModelState.Init) {
        Timber.i("handleStateInit")
    }

    private fun handleStateError(state: PictureSliderViewModelState.Error) {
        Timber.i("handleStateError")
        Snackbar.make(coordinatorLayout, "ERROR: ${state.errorMessage}", Snackbar.LENGTH_LONG).show()
    }

    private fun handleBackPressedToChangeDirectory(): Boolean {
        viewModel.clean()
        return false
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
//        Timber.i("onPageScrolled position = $position positionOffset=$positionOffset positionOffsetPixels=$positionOffsetPixels")
    }

    override fun onPageSelected(position: Int) {
        Timber.i("onPageSelected currentPage = $position")
        SharedPref.set(PREF_CURRENT_PAGE_LAST_COMIC, position.toString())
        currentPage = position
        viewModel.setCurrentPage(position)
    }

    override fun onPageScrollStateChanged(state: Int) {
//        Timber.i("onPageScrollStateChanged state = $state")
    }
}
