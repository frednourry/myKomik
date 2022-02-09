package fr.nourry.mynewkomik.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.utils.VolumeLabel
import fr.nourry.mynewkomik.utils.initVolumeDetection
import timber.log.Timber
import java.io.File

class DialogChooseRootDirectory(val rootPath: File?=null) : DialogFragment() {
    interface ConfirmationDialogListener {
        fun onChooseDirectory(file:File)
    }
    var listener: ConfirmationDialogListener? = null

    private lateinit var listView: ListView
    private var volumeList:ArrayList<VolumeLabel> = ArrayList()

    companion object {
        private var adapter:DialogChooseRootDirectoryAdapter? = null;

        // IMPORTANT FUNCTION : without it, when the phone is rotate this will crash the application (the -parent- fragment recreation will search a constructor...)
        @SuppressLint("StaticFieldLeak")
        private var myInstance: DialogChooseRootDirectory? = null
        fun newInstance(rootPath: File?=null): DialogChooseRootDirectory {
            if (myInstance == null) {
                myInstance = DialogChooseRootDirectory(rootPath)
            }
            return myInstance as DialogChooseRootDirectory
        }
        // END
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Timber.d("onCreateDialog")

        if (volumeList.count()==0) {
            volumeList = initVolumeDetection(requireContext())!!
        }

//        val builder = AlertDialog.Builder(requireContext(), R.style.Theme_MaterialComponents_DialogWhenLarge)    // Mini alertDialog on LG-H870 when give a theme...
        val builder = AlertDialog.Builder(requireContext(), /*R.style.CustomAlertDialog*/)          // Mini alertDialog on LG-H870 when give a theme...

        // Get the layout inflater
        val inflater = requireActivity().layoutInflater

        listView = inflater.inflate(R.layout.dialog_choose_root_list, null) as ListView
//        val dialogView = inflater.inflate(R.layout.dialog_choose_root, null) as View
//        listView = dialogView.findViewById(R.id.listView) as ListView


        if (adapter == null)
            adapter = DialogChooseRootDirectoryAdapter(inflater, volumeList)

        if (rootPath == null) {
            // Do nothing (this dialog is recreated)
            Timber.v("onCreateDialog: rootPath = null")
        } else {
            val origin:File? = rootPath
            //if (volumeList.count()>0) File(volumeList[0].path) else rootPath
            adapter!!.setRootPath(origin!!)
        }

        val thisObject = this
        adapter!!.listener = object:DialogChooseRootDirectoryAdapter.ConfirmationDialogListener {
            override fun onChooseDirectory(file:File) {
                Timber.d("onChooseDirectory :: ${file.absolutePath}")
                listener?.onChooseDirectory(file)
                thisObject.dismiss()
            }
        }
//        listView.adapter = adapter

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder
            .setTitle(getString(R.string.choose_root_text))
//          .setCustomTitle()  // TODO to try...
//            .setView(dialogView)
            .setView(listView)
            .setAdapter(adapter) { _, _ ->  }    // Click event is catch in the adapter!
        val alertDialog = builder.create()
        alertDialog.window?.setLayout(300, 400)

        return alertDialog
    }
}