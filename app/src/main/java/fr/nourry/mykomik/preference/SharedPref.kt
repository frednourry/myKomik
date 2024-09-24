package fr.nourry.mykomik.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import fr.nourry.mykomik.App
import android.util.Log

// Load preferences (https://developer.android.com/training/data-storage/shared-preferences)

const val PREF_ROOT_TREE_URI                = "comics_tree_uri"
const val PREF_LAST_DIR_URI                 = "comics_last_dir_uri"
const val PREF_LAST_COMIC_URI               = "comics_last_comic_uri"
const val PREF_CURRENT_PAGE_LAST_COMIC      = "comics_current_page_last_comic"
const val PREF_IMAGE_DISPLAY_OPTION_LOCKED  = "image_display_option_locked"

object SharedPref {
    const val TAG = "SharedPref"

    private lateinit var sharedPref : SharedPreferences

    fun init(a: FragmentActivity) {
        sharedPref = a.getPreferences(Context.MODE_PRIVATE)
    }

    fun getString(paramName:String, defaultValue:String=""): String? {
        return sharedPref.getString(paramName, defaultValue)
    }

    fun setString(paramName:String, value:String) {
        if (App.isGuestMode || App.isSimpleViewerMode) return

        with (sharedPref.edit()) {
            Log.i(TAG,"SharedPref.setString($paramName, $value) ")
            putString(paramName, value)
            apply()
        }
    }

    fun getInt(paramName:String, defaultValue:Int=0): Int {
        return sharedPref.getInt(paramName, defaultValue)
    }

    fun setInt(paramName:String, value:Int) {
        if (App.isGuestMode || App.isSimpleViewerMode) return

        with (sharedPref.edit()) {
            Log.i(TAG,"SharedPref.setInt($paramName, $value) ")
            putInt(paramName, value)
            apply()
        }
    }
}