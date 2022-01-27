package fr.nourry.mynewkomik.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import fr.nourry.mynewkomik.R
import timber.log.Timber
import java.io.File

class DialogChooseRootDirectory(val rootPath: File, val selectColor:Int = Color.BLACK, val defaultColor:Int = Color.DKGRAY) : DialogFragment() {
    interface ConfirmationDialogListener {
        fun onChooseDirectory(file:File)
    }
    var listener: ConfirmationDialogListener? = null

    private lateinit var listView: ListView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val builder = AlertDialog.Builder(requireContext())

        // Get the layout inflater
        val inflater = requireActivity().layoutInflater
        listView = inflater.inflate(R.layout.dialog_choose_root_list, null) as ListView
        val adapter = DialogChooseRootDirectoryAdapter(inflater)
        adapter.setRootPath(rootPath, selectColor, defaultColor)
        adapter.listener = object:DialogChooseRootDirectoryAdapter.ConfirmationDialogListener {
            override fun onChooseDirectory(file:File) {
                Timber.d("onChooseDirectory :: ${file.absolutePath}")
                listener?.onChooseDirectory(file)
            }
        }

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder
            .setTitle(getString(R.string.choose_root_text))
            .setView(listView)
            .setAdapter(adapter) { _, _ ->  }    // Click event is catch in the adapter!
            .setCancelable(false)   // Doesn't work ...!
        val alertDialog = builder.create()
        alertDialog.window?.setLayout(300, 400)

        return alertDialog
    }
}