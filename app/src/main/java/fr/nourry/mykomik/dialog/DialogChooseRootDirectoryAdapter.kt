package fr.nourry.mykomik.dialog

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import fr.nourry.mykomik.databinding.DialogChooseRootListItemBinding
import fr.nourry.mykomik.utils.*
import timber.log.Timber
import java.io.File

data class SelectableDir (val label: String, val pathFile:File, val isVolume:Boolean, val isImportant:Boolean, val isParent:Boolean)

class DialogChooseRootDirectoryAdapter(private val volumeList: ArrayList<VolumeLabel>) : BaseAdapter(), ListAdapter {
    interface ConfirmationDialogListener {
        fun onChooseDirectory(file:File)
    }
    var listener: ConfirmationDialogListener? = null

    inner class ViewHolder(binding: DialogChooseRootListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var diskIcon = binding.diskIcon
        var textView = binding.textView
        var button = binding.button
        var separatorView = binding.separatorView
        var selectableDir:SelectableDir? = null
        var adapter: DialogChooseRootDirectoryAdapter? = null
    }

    private lateinit var rootFile: File
    private lateinit var selectableDirList: MutableList<SelectableDir>

    fun setRootPath(root:File) {
        Timber.d("setRootPath($root)")
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

        val viewHolder: ViewHolder //DialogChooseRootDirectoryHolder
        if (convertView == null) {
            val binding = DialogChooseRootListItemBinding.inflate(LayoutInflater.from(parent!!.context), parent,false)
            view = binding.root
            viewHolder = ViewHolder(binding)

            viewHolder.adapter = this
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }
        viewHolder.selectableDir = selectableDir

        view.setOnClickListener { v ->
            val tag = v.tag as ViewHolder
            val adapter = tag.adapter
            val textView = tag.textView
            val text = textView.text
            val selectableDir2 = tag.selectableDir as SelectableDir
            Timber.d("OnClickListener " + text + " " + selectableDir2.pathFile.name)
            if (selectableDir2.pathFile.exists() && selectableDir2.pathFile.isDirectory && (selectableDir2.label != "")) {
                val newList = getDirectoriesList(selectableDir2.pathFile)
                if (newList.isNotEmpty()) {
                    adapter?.refreshDirList(selectableDir2.pathFile, newList as MutableList<File>)
                }
            }
        }
        viewHolder.button.setOnClickListener { b ->
            if (b.parent != null) {
                val v = b.parent as View
                val holder = v.tag as ViewHolder
                holder.selectableDir?.let { listener?.onChooseDirectory(it.pathFile) }
            }
        }


        // Set name and button visibility
        viewHolder.button.visibility = View.VISIBLE
//        view.textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0F)
        viewHolder.textView.setTextAppearance(android.R.style.TextAppearance_Small)

        viewHolder.textView.text = selectableDir.label
        viewHolder.diskIcon.visibility = if (selectableDir.isVolume) View.VISIBLE else View.INVISIBLE
        viewHolder.separatorView.visibility = if (selectableDir.label == "") View.VISIBLE else View.INVISIBLE

        if (selectableDir.isVolume) {
            viewHolder.textView.setTextAppearance(android.R.style.TextAppearance_Medium)
        }
        if (selectableDir.isImportant) {
            viewHolder.textView.setTypeface(viewHolder.textView.typeface, Typeface.BOLD)
        }

        if (selectableDir.isParent || selectableDir.isVolume || selectableDir.label == "") {
            viewHolder.button.visibility = View.INVISIBLE
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
        if (newSelectableDir.isNotEmpty()) {
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
            newSelectableDir.add(SelectableDir(dir.name, dir, false, false, false))
        }

        this.selectableDirList = newSelectableDir
    }

    private fun refreshDirList(newRoot:File, dirList: MutableList<File>) {
        this.rootFile = newRoot
        updateDirListWithSelfAndParent(dirList)
        this.notifyDataSetChanged()
    }

}