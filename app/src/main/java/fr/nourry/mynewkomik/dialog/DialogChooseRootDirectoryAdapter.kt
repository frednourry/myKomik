package fr.nourry.mynewkomik.dialog

import android.graphics.Color
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

class DialogChooseRootDirectoryAdapter(private val inflater: LayoutInflater) : BaseAdapter(), ListAdapter {
    interface ConfirmationDialogListener {
        fun onChooseDirectory(file:File)
    }
    var listener: ConfirmationDialogListener? = null

    private lateinit var rootFile: File
    private lateinit var dirList: MutableList<File>
    private var selectedColor: Int = Color.BLACK
    private var defaultColor: Int = Color.DKGRAY

    fun setRootPath(root:File, selectedColor:Int = Color.BLACK, defaultColor:Int = Color.DKGRAY) {
        rootFile = root
        dirList = getDirectoriesList(root) as MutableList<File>
        updateDirListWithSelfAndParent()
        this.selectedColor = selectedColor
        this.defaultColor = defaultColor
    }

    override fun getCount(): Int {
        return dirList.size
    }

    override fun getItem(index: Int): Any {
        return dirList[index]
    }

    override fun getItemId(index: Int): Long {
        return index.toLong()
    }

    override fun getView(index: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val file = dirList[index]

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
        viewHolder.file = file
//viewHolder.isParentFolder = false
        view.tag = viewHolder

        view.setOnClickListener { v ->
            val tag = v.tag as DialogChooseRootDirectoryHolder
            val adapter = tag.adapter
            val textView = tag.textView
            val text = textView?.text
            val f = tag.file
            Timber.i("OnClickListener " + text + " " + f?.name)
            if (f != null) {
                if (f.exists() && f.isDirectory) {
                    val newList = getDirectoriesList(f) as MutableList<File>
                    adapter?.refreshDirList(f, newList)
                }
            }
        }
        viewHolder.button?.setOnClickListener { b ->
            if (b.parent != null) {
                val view = b.parent as View
                val holder = view.tag as DialogChooseRootDirectoryHolder
                holder.file?.let { listener?.onChooseDirectory(it) }
            }
        }


        // Set name and button visibility
        view.button.visibility = View.VISIBLE
        view.textView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20.0F)
        view.textView?.setTextColor(this.defaultColor)
//        TextViewCompat.setTextAppearance(view.textView, android.R.style.TextAppearance_DeviceDefault);

        when (file) {
            rootFile -> {
                view.textView?.setTextColor(this.selectedColor)
                view.textView?.text = file.name
                view.textView?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24.0F)
    //            TextViewCompat.setTextAppearance(view.textView,  android.R.style.TextAppearance_Large);
            }
            rootFile.parentFile -> {
                view.textView?.text = ".."
                view.button.visibility = View.INVISIBLE
            }
            else -> {
                view.textView?.text = "   " + file.name
            }
        }

        return view
    }

    private fun updateDirListWithSelfAndParent() {
        val parentFile = rootFile.parentFile

        dirList.add(0, rootFile)
        if (parentFile!= null && isValidDir(parentFile)) {
            dirList.add(0, parentFile)
        }
    }

    private fun refreshDirList(newRoot:File, dirList: MutableList<File>) {
        this.rootFile = newRoot
        this.dirList = dirList
        updateDirListWithSelfAndParent()
        this.notifyDataSetChanged()
    }

}