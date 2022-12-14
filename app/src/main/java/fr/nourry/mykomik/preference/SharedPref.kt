package fr.nourry.mykomik.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

// Load preferences (https://developer.android.com/training/data-storage/shared-preferences)

const val PREF_ROOT_DIR                 = "comics_dir"
const val PREF_LAST_COMIC_PATH          = "comics_last_comic"
const val PREF_CURRENT_PAGE_LAST_COMIC  = "comics_current_page_last_comic"

object SharedPref {
    private lateinit var sharedPref : SharedPreferences

    fun init(a: FragmentActivity) {
        sharedPref = a.getPreferences(Context.MODE_PRIVATE)
    }

    fun get(param_name:String, defaultValue:String=""): String? {
        return sharedPref.getString(param_name, defaultValue)
    }

    fun set(param_name:String, value:String) {
        with (sharedPref.edit()) {
            Timber.i("SharedPref.set($param_name, $value) ")
            putString(param_name, value)
            apply()
        }
    }
}