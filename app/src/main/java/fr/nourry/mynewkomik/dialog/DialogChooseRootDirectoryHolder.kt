package fr.nourry.mynewkomik.dialog

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class DialogChooseRootDirectoryHolder {
    var button: Button? = null
    var selectableDir:SelectableDir? = null
    var diskIcon: ImageView? = null
    var separator:ImageView? = null
    var textView: TextView? = null
    var adapter: DialogChooseRootDirectoryAdapter? = null
}