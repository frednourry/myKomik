package fr.nourry.mynewkomik.pictureslider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager.widget.ViewPager
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.databinding.FragmentPictureSliderBinding
import fr.nourry.mynewkomik.dialog.DialogComicLoading
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import timber.log.Timber

private const val TAG_DIALOG_COMIC_LOADING = "LoadingComicDialog"

class PictureSliderFragment: Fragment(), ViewPager.OnPageChangeListener  {

    val STATE_CURRENT_PAGE = "state:current_page"

    private lateinit var pictureSliderAdapter: PictureSliderAdapter
//    private lateinit var pictureSliderAdapter: PictureSliderAdapter2
    private var currentPage = 0
    private lateinit var viewModel: PictureSliderViewModel

    private var dialogComicLoading:DialogComicLoading = DialogComicLoading.newInstance()

    // Test for View Binding (replace 'kotlin-android-extensions')
    private var _binding: FragmentPictureSliderBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity?.actionBar?.hide()

        _binding = FragmentPictureSliderBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
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

        val args = PictureSliderFragmentArgs.fromBundle(requireArguments())
        val comic = args.comic
        currentPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE) ?: args.currentPage


        // ViewPager2
//        pictureSliderAdapter = PictureSliderAdapter2(requireContext(), pictures)
        // ViewPager1
/*        pictureSliderAdapter = PictureSliderAdapter(requireContext(), comic)
        binding.viewPager.adapter = pictureSliderAdapter
            pictureSliderAdapter.notifyDataSetChanged()
*/
        viewModel = ViewModelProvider(this)[PictureSliderViewModel::class.java]
        viewModel.getState().observe(viewLifecycleOwner) {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        }
        viewModel.initialize(comic, currentPage/*, savedInstanceState == null*/)

        binding.viewPager.addOnPageChangeListener(this)

        // Replace the title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = comic.file.name
    }

    override fun onDestroyView() {
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
        _binding = null
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

        if (dialogComicLoading.isAdded) {
            dialogComicLoading.dismiss()
        }

        if (!::pictureSliderAdapter.isInitialized) {
            Timber.i("   1")
            pictureSliderAdapter = PictureSliderAdapter(requireContext(), state.comic)
            Timber.i("   2")
            binding.viewPager.currentItem = state.currentPage
            Timber.i("   3 ${binding.viewPager}")
            binding.viewPager.adapter = pictureSliderAdapter
            Timber.i("   4 ${binding.viewPager.adapter}")
//            pictureSliderAdapter.notifyDataSetChanged()
        }

        binding.viewPager.currentItem = state.currentPage
        pictureSliderAdapter.notifyDataSetChanged()
    }

    private fun handleStateInit(state: PictureSliderViewModelState.Init) {
        Timber.i("handleStateInit")
    }

    private fun handleStateError(state: PictureSliderViewModelState.Error) {
        Timber.i("handleStateError")
        dialogComicLoading.dismiss()
        val alert = AlertDialog.Builder(requireContext())
            .setMessage("This file is not valid !")
            .setPositiveButton(R.string.ok) { _,_ ->
                findNavController().popBackStack()
            }
            .create()
        alert.show()
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
        currentPage = position
        viewModel.setCurrentPage(position)
    }

    override fun onPageScrollStateChanged(state: Int) {
//        Timber.i("onPageScrollStateChanged state = $state")
    }
}
