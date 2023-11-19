package fr.nourry.mykomik.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import fr.nourry.mykomik.App
import android.util.Log

// Load preferences (https://developer.android.com/training/data-storage/shared-preferences)

const val PREF_ROOT_TREE_URI            = "comics_tree_uri"
const val PREF_LAST_DIR_URI             = "comics_last_dir_uri"
const val PREF_LAST_COMIC_URI           = "comics_last_comic_uri"
const val PREF_CURRENT_PAGE_LAST_COMIC  = "comics_current_page_last_comic"

object SharedPref {
    const val TAG = "SharedPref"

    private lateinit var sharedPref : SharedPreferences

    fun init(a: FragmentActivity) {
        sharedPref = a.getPreferences(Context.MODE_PRIVATE)
    }

    fun get(param_name:String, defaultValue:String=""): String? {
        return sharedPref.getString(param_name, defaultValue)
    }

    fun set(param_name:String, value:String) {
        if (App.isGuestMode || App.isSimpleViewerMode) return

        with (sharedPref.edit()) {
            Log.i(TAG,"SharedPref.set($param_name, $value) ")
            putString(param_name, value)
            apply()
        }
    }
}