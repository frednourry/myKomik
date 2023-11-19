package fr.nourry.mykomik.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fr.nourry.mykomik.R
import fr.nourry.mykomik.loader.IdleController
import android.util.Log


data class AppUserPreferences(var readingDirection:String,
                              var hidePageNumber:Boolean,
                              var hideReadComics:Boolean,
                              var disableRotation:Boolean,
                              var tapToChangePage:Boolean,
                              var adaptPageBackgroundAuto:Boolean,
                              var generateThumbnailsAuto:Boolean) {
    fun copy(): AppUserPreferences {
        return AppUserPreferences(readingDirection, hidePageNumber, hideReadComics,
                                  disableRotation, tapToChangePage, adaptPageBackgroundAuto,
                                  generateThumbnailsAuto)
    }
}

class UserPreferences(val context:Context):SharedPreferences.OnSharedPreferenceChangeListener {
    // Preferences values (according to array.xml !!)
    private var readingDirectionValues: Array<String> = context.resources.getStringArray(R.array.settings_page_turn_direction_values)
    private var ReadingDirectionLTR:String = readingDirectionValues[0]

    // Preference label
    private val hideReadComicsLabel     = "hide_read_comics"
    private val hidePageNumberLabel     = "hide_page_number"
    private val readingDirectionLabel   = "page_turn_direction"
    private val disableRotationLabel    = "disable_rotation"
    private val tapToChangePageLabel    = "tap_to_change_page"
    private val adaptPageBackgroundAuto = "adapt_page_background_auto"
    private val generateThumbnailsAuto  = "generate_thumbnails_auto"

    // Values
    private var appUserPreferences = AppUserPreferences(
                                        readingDirection = ReadingDirectionLTR,
                                        hidePageNumber = false,
                                        hideReadComics = false,
                                        disableRotation = false,
                                        tapToChangePage = false,
                                        adaptPageBackgroundAuto = true,
                                        generateThumbnailsAuto = true)
    private var appUserPreferencesSave : AppUserPreferences? = null     // To save the preferences in Guest Mode

    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val TAG = "UserPreferences"

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

        loadUserPreferences()

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun loadUserPreferences() {
        val temp = sharedPreferences.getString(readingDirectionLabel, ReadingDirectionLTR)
        Log.i(TAG,"loadUserPreferences:: $temp")
        appUserPreferences.readingDirection = temp ?: ReadingDirectionLTR

        appUserPreferences.hidePageNumber = sharedPreferences.getBoolean(hidePageNumberLabel, false)
        appUserPreferences.hideReadComics = sharedPreferences.getBoolean(hideReadComicsLabel, false)
        appUserPreferences.disableRotation = sharedPreferences.getBoolean(disableRotationLabel, false)
        appUserPreferences.tapToChangePage = sharedPreferences.getBoolean(tapToChangePageLabel, false)
        appUserPreferences.adaptPageBackgroundAuto = sharedPreferences.getBoolean(adaptPageBackgroundAuto, true)
        appUserPreferences.generateThumbnailsAuto = sharedPreferences.getBoolean(generateThumbnailsAuto, true)
    }

    fun saveAppUserPreference() {
        Log.v(TAG,"saveAppUserPreference")
        appUserPreferencesSave = appUserPreferences.copy()
    }

    fun restoreAppUserPreference() {
        Log.v(TAG,"restoreAppUserPreference")
        if (appUserPreferencesSave != null) {
            appUserPreferences = appUserPreferencesSave!!

            // Restore these values in sharedPreferences
            sharedPreferences.edit().putString(readingDirectionLabel, appUserPreferences.readingDirection).apply()
            sharedPreferences.edit().putBoolean(hidePageNumberLabel, appUserPreferences.hidePageNumber).apply()
            sharedPreferences.edit().putBoolean(hideReadComicsLabel, appUserPreferences.hideReadComics).apply()
            sharedPreferences.edit().putBoolean(disableRotationLabel, appUserPreferences.disableRotation).apply()
            sharedPreferences.edit().putBoolean(tapToChangePageLabel, appUserPreferences.tapToChangePage).apply()
            sharedPreferences.edit().putBoolean(adaptPageBackgroundAuto, appUserPreferences.adaptPageBackgroundAuto).apply()

            appUserPreferencesSave = null
        }
    }

    override fun onSharedPreferenceChanged(sharePref: SharedPreferences?, key: String?) {
        Log.v(TAG,"loadUserPreferences:: onSharedPreferenceChanged key==$key")

        if (key != null && sharePref!= null && sharePref == sharedPreferences) {
            when (key) {
                readingDirectionLabel   -> appUserPreferences.readingDirection = sharePref.getString(readingDirectionLabel, ReadingDirectionLTR)!!
                hidePageNumberLabel     -> appUserPreferences.hidePageNumber = sharePref.getBoolean(hidePageNumberLabel, false)
                hideReadComicsLabel     -> appUserPreferences.hideReadComics = sharePref.getBoolean(hideReadComicsLabel, false)
                disableRotationLabel    -> appUserPreferences.disableRotation = sharePref.getBoolean(disableRotationLabel, false)
                tapToChangePageLabel    -> appUserPreferences.tapToChangePage = sharePref.getBoolean(tapToChangePageLabel, false)
                adaptPageBackgroundAuto -> appUserPreferences.adaptPageBackgroundAuto = sharePref.getBoolean(adaptPageBackgroundAuto, true)
                generateThumbnailsAuto  -> {
                                                appUserPreferences.generateThumbnailsAuto = sharePref.getBoolean(generateThumbnailsAuto, true)
                                                if (appUserPreferences.generateThumbnailsAuto)
                                                    IdleController.getInstance().reinit()
                                                else
                                                    IdleController.getInstance().resetIdleTimer()
                                            }
            }
        }
    }

    fun shouldHideReadComics():Boolean = appUserPreferences.hideReadComics
    fun isReadingDirectionLTR():Boolean = appUserPreferences.readingDirection==ReadingDirectionLTR
    fun shouldHidePageNumber():Boolean = appUserPreferences.hidePageNumber
    fun isRotationDisabled():Boolean = appUserPreferences.disableRotation
    fun isTappingToChangePage():Boolean = appUserPreferences.tapToChangePage
    fun isAdaptPageBackgroundAuto():Boolean = appUserPreferences.adaptPageBackgroundAuto
    fun isGenerateThumbnailsAuto():Boolean = appUserPreferences.generateThumbnailsAuto
}