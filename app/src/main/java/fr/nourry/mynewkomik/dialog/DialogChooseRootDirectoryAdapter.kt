package fr.nourry.mynewkomik.dialog

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.utils.*
import kotlinx.android.synthetic.main.dialog_choose_root_list_item.view.*
import timber.log.Timber
import java.io.File

data class SelectableDir (val label: String, val pathFile:File, val isVolume:Boolean, val isImportant:Boolean, val isParent:Boolean)

class DialogChooseRootDirectoryAdapter(private val inflater: LayoutInflater, private val volumeList: ArrayList<VolumeLabel>) : BaseAdapter(), ListAdapter {
    interface ConfirmationDialogListener {
        fun onChooseDirectory(file:File)
    }
    var listener: ConfirmationDialogListener? = null

    private lateinit var rootFile: File
    private lateinit var selectableDirList: MutableList<SelectableDir>

    fun setRootPath(root:File) {
        val dirList = getDirectoriesList(root) as MutableList<File>
        rootFile = root

        updateDirListWithSelfAndParent(dirList)
    }

    override fun getCount(): Int {
        return selectableDirList.size
    }

    override fun getItem(index: Int): Any {
        return selectableDirList[index]
    }

    override fun getItemId(index: Int): Long {
        return index.toLong()
    }

    override fun getView(index: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val selectableDir = selectableDirList[index]

        val viewHolder: DialogChooseRootDirectoryHolder
        if (convertView == null) {
            view = inflater.inflate(R.layout.dialog_choose_root_list_item, parent, false)
            viewHolder = DialogChooseRootDirectoryHolder()

        } else {
            view = convertView
            viewHolder = view.tag as DialogChooseRootDirectoryHolder
        }

        viewHolder.textView = view.textView
        viewHolder.button = view.button
        viewHolder.adapter = this
        viewHolder.diskIcon = view.diskIcon
        viewHolder.selectableDir = selectableDir
        viewHolder.separator = view.separatorView
        view.tag = viewHolder

        view.setOnClickListener { v ->
            val tag = v.tag as DialogChooseRootDirectoryHolder
            val adapter = tag.adapter
            val textView = tag.textView
            val text = textView?.text
            val selectableDir = tag.selectableDir
            Timber.i("OnClickListener " + text + " " + selectableDir?.pathFile?.name)
            if (selectableDir != null) {
                if (selectableDir.pathFile.exists() && selectableDir.pathFile.isDirectory && selectableDir.label != "") {
                    val newList = getDirectoriesList(selectableDir.pathFile) as MutableList<File>
                    adapter?.refreshDirList(selectableDir.pathFile, newList)
                }
            }
        }
        viewHolder.button?.setOnClickListener { b ->
            if (b.parent != null) {
                val view = b.parent as View
                val holder = view.tag as DialogChooseRootDirectoryHolder
                holder.selectableDir?.let { listener?.onChooseDirectory(it.pathFile) }
            }
        }


        // Set name and button visibility
        view.button.visibility = View.VISIBLE
        view.textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0F)
        view.textView?.setTypeface(null, Typeface.NORMAL)   // Reset text style

//        view.textView?.setTextColor(this.defaultColor)
        view.textView?.text = selectableDir.label
        view.diskIcon.visibility = if (selectableDir.isVolume) View.VISIBLE else View.INVISIBLE
        view.separatorView.visibility = if (selectableDir.label == "") View.VISIBLE else View.INVISIBLE

        if (selectableDir.isVolume) {
//            view.textView?.setTextColor(this.selectedColor)
            view.textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22.0F)
        }
        if (selectableDir.isImportant) {
            view.textView?.setTypeface(view.textView?.typeface, Typeface.BOLD)
        }

        if (selectableDir.isParent || selectableDir.isVolume || selectableDir.label == "") {
            view.button.visibility = View.INVISIBLE
        }

        return view
    }

    private fun updateDirListWithSelfAndParent(dirList: MutableList<File>) {
        val parentFile = rootFile.parentFile
        var bShowRootFile = true
        var bShowParentRootFile = true

        val newSelectableDir: MutableList<SelectableDir> = mutableListOf<SelectableDir>()

        // Add each volume list
        for (volume in volumeList) {
            var isImportant = false
            if (rootFile.absolutePath.indexOf(volume.path) == 0) {
                isImportant = true
                if (rootFile.absolutePath.count() == volume.path.count()) {
                    // Same path, so don't show rootFile
                    bShowRootFile = false
                    bShowParentRootFile = false
                }
            }

            newSelectableDir.add(SelectableDir("["+volume.name+"]", File(volume.path), true, isImportant, false))
        }

        // Add a separator
        if (newSelectableDir.count()>0) {
            // Add separator
            newSelectableDir.add(SelectableDir("", File("plop"), false, false, false))
        }

        // Add the parent path ?
        if (parentFile== null || !isValidDir(parentFile)) {
            bShowParentRootFile = false
        }
        if (bShowParentRootFile) {
            newSelectableDir.add(SelectableDir("..", parentFile!!, false, false, true))
        }

        // Add this path
        if (bShowRootFile) {
            newSelectableDir.add(SelectableDir(rootFile.name, rootFile, false, true, false))
        }


        // Add sub directories
        for (dir in dirList) {
            newSelectableDir.add(SelectableDir("  "+dir.name, dir, false, false, false))
        }

        this.selectableDirList = newSelectableDir
    }

    private fun refreshDirList(newRoot:File, dirList: MutableList<File>) {
        this.rootFile = newRoot
        updateDirListWithSelfAndParent(dirList)
        this.notifyDataSetChanged()
    }

}