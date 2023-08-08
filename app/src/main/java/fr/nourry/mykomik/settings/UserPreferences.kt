package fr.nourry.mykomik.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fr.nourry.mykomik.R
import timber.log.Timber


class UserPreferences(val context:Context):SharedPreferences.OnSharedPreferenceChangeListener {
    // Preferences values (according to array.xml !!)
    private var readingDirectionValues: Array<String> = context.resources.getStringArray(R.array.settings_page_turn_direction_values)
    private var ReadingDirectionLTR:String = readingDirectionValues[0]
//    private var ReadingDirectionRTL:String = readingDirectionValues[1]
//    private var ReadingDirectionTTB:String = readingDirectionValues[2]

    // Preference label
    private val hideReadComicsLabel     = "hide_read_comics"
    private val hidePageNumberLabel     = "hide_page_number"
    private val readingDirectionLabel   = "page_turn_direction"
    private val disableRotationLabel    = "disable_rotation"
    private val tapToChangePageLabel    = "tap_to_change_page"
    private val adaptPageBackgroundAuto  = "adapt_page_background_auto"

    // Variables
    private lateinit var reading_direction:String
    private var hide_page_number:Boolean = false
    private var hide_read_comics:Boolean = false
    private var disable_rotation:Boolean = false
    private var tap_to_change_page:Boolean = false
    private var adapt_page_background_auto:Boolean = true

    var sharedPreferences: SharedPreferences

    companion object {
        private var mInstance: UserPreferences? = null
        fun getInstance(context:Context): UserPreferences {
            if (mInstance == null) {
                mInstance = UserPreferences(context)
            }
            return mInstance!!
        }
    }

    init {
        // Respect the same order present in array.xml !!

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        loadUserPreferences()

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun loadUserPreferences() {
        val temp = sharedPreferences.getString(readingDirectionLabel, ReadingDirectionLTR)
        Timber.i("loadUserPreferences:: $temp")
        reading_direction = temp ?: ReadingDirectionLTR

        hide_page_number = sharedPreferences.getBoolean(hidePageNumberLabel, false)
        hide_read_comics = sharedPreferences.getBoolean(hideReadComicsLabel, false)
        disable_rotation = sharedPreferences.getBoolean(disableRotationLabel, false)
        tap_to_change_page = sharedPreferences.getBoolean(tapToChangePageLabel, false)
        adapt_page_background_auto = sharedPreferences.getBoolean(adaptPageBackgroundAuto, true)
    }

    override fun onSharedPreferenceChanged(sharePref: SharedPreferences, key: String) {
        Timber.v("loadUserPreferences:: onSharedPreferenceChanged key==$key")
        if (sharePref == sharedPreferences) {
            when (key) {
                readingDirectionLabel -> reading_direction =
                    sharePref.getString(readingDirectionLabel, ReadingDirectionLTR)!!
                hidePageNumberLabel -> hide_page_number =
                    sharePref.getBoolean(hidePageNumberLabel, false)
                hideReadComicsLabel -> hide_read_comics =
                    sharePref.getBoolean(hideReadComicsLabel, false)
                disableRotationLabel -> disable_rotation =
                    sharePref.getBoolean(disableRotationLabel, false)
                tapToChangePageLabel -> tap_to_change_page =
                    sharePref.getBoolean(tapToChangePageLabel, false)
                adaptPageBackgroundAuto -> adapt_page_background_auto =
                    sharePref.getBoolean(adaptPageBackgroundAuto, true)
            }
        }
    }

    fun shouldHideReadComics():Boolean = hide_read_comics
    fun isReadingDirectionLTR():Boolean = reading_direction==ReadingDirectionLTR
/*    fun isReadingDirectionLTR():Boolean{
        Timber.e("loadUserPreferences:: reading_direction=$reading_direction");
        return reading_direction==ReadingDirectionLTR
    }*/
    fun shouldHidePageNumber():Boolean = hide_page_number
    fun isRotationDisabled():Boolean = disable_rotation
    fun isTappingToChangePage():Boolean = tap_to_change_page

    fun isAdaptPageBackgroundAuto():Boolean = adapt_page_background_auto
}