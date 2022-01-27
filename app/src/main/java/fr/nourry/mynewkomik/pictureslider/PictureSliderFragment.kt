package fr.nourry.mynewkomik.pictureslider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import fr.nourry.mynewkomik.ComicPicture
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import kotlinx.android.synthetic.main.fragment_picture_slider.*
import kotlinx.android.synthetic.main.fragment_picture_slider.coordinatorLayout
import timber.log.Timber

class PictureSliderFragment: Fragment() {

    private lateinit var pictureSliderAdapter: PictureSliderAdapter
//    private lateinit var pictureSliderAdapter: PictureSliderAdapter2
    private var pictures = mutableListOf<ComicPicture>()
    private lateinit var viewModel: PictureSliderViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        viewModel.getState().observe(viewLifecycleOwner, {
            Timber.i("BrowserFragment::observer change state !!")
            updateUI(it!!)
        })

        val args = PictureSliderFragmentArgs.fromBundle(requireArguments())
        val comic = args.comic
        viewModel.initialize(comic)

        // Replace the title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = comic.file.name
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
        Toast.makeText(requireContext(), "L O A D I N G . . .", Toast.LENGTH_SHORT).show()
    }

    private fun handleStateReady(state: PictureSliderViewModelState.Ready) {
        Timber.i("handleStateReady")
        pictures.clear()
        pictures.addAll(state.pictures)
        pictureSliderAdapter.notifyDataSetChanged()
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


}
