package fr.nourry.mykomik.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import fr.nourry.mykomik.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)
    }
}