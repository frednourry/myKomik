package fr.nourry.mynewkomik.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import fr.nourry.mynewkomik.R
import timber.log.Timber

class DialogComicLoading() : DialogFragment() {
    lateinit var progressBar: ProgressBar

    companion object {
        private var myInstance: DialogComicLoading? = null
        fun newInstance(): DialogComicLoading {
            if (myInstance == null) {
                myInstance = DialogComicLoading()
            }
            return myInstance as DialogComicLoading
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Timber.d("onCreateDialog")

        // Get the layout inflater
        var inflater = requireActivity().layoutInflater
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // Inflate and set the layout for the dialog
        val myView = inflater.inflate(R.layout.dialog_comic_loading, null)
        progressBar = myView.findViewById(R.id.progressBar)

        // Pass null as the parent view because its going in the dialog layout
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        builder
            .setTitle("Loading comic...")
            .setView(myView)
        val alertDialog = builder.create()
        this.setStyle(STYLE_NO_TITLE, 0)

        return alertDialog
    }

    override fun onStart() {
        super.onStart()

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        this.dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
//        this.dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun setProgress(index:Int, size:Int) {
        if (this::progressBar.isInitialized)
            progressBar.progress = (100 * index.toFloat()/size.toFloat()).toInt()
    }
}