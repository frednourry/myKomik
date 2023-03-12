package fr.nourry.mykomik.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import fr.nourry.mykomik.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        supportActionBar.title = getString(R.string.settings)
        supportActionBar.setDisplayHomeAsUpEnabled(true)
        supportActionBar.setLogo(R.mipmap.ic_launcher)
        supportActionBar.setDisplayUseLogoEnabled(true)
    }
}