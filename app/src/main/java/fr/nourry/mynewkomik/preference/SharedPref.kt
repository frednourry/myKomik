package fr.nourry.mynewkomik.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

// Load preferences (https://developer.android.com/training/data-storage/shared-preferences)

const val PREF_ROOT_DIR                 = "comics_dir"
const val PREF_LAST_COMIC_PATH          = "comics_last_comic"
const val PREF_CURRENT_PAGE_LAST_COMIC  = "comics_current_page_last_comic"

object SharedPref {
    private var sharedPref : SharedPreferences? = null

    fun init(a: FragmentActivity) {
        sharedPref = a.getPreferences(Context.MODE_PRIVATE) ?: return
    }

    fun get(param_name:String, defaultValue:String=""): String? {
        if (sharedPref == null) {
            Timber.i("SharedPref.get($param_name) with SharedPref not initialized !")
            return null
        }

        return sharedPref!!.getString(param_name, defaultValue)
    }

    fun set(param_name:String, value:String) {
        if (sharedPref == null) {
            Timber.i("SharedPref.set($param_name) with SharedPref not initialized !")
            return
        }

        with (sharedPref!!.edit()) {
            putString(param_name, value)
            apply()
        }
    }



}