package fr.nourry.mynewkomik.dialog

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import java.io.File

class DialogChooseRootDirectoryHolder {
    var button: Button? = null
    var file: File? = null
//    var iconView: ImageView? = null
//    var isParentFolder = false
    var textView: TextView? = null
    var adapter: DialogChooseRootDirectoryAdapter? = null
}