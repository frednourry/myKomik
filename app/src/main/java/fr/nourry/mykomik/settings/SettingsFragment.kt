package fr.nourry.mykomik.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import fr.nourry.mykomik.loader.IdleController

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (App.isGuestMode || App.isSimpleViewerMode)
            setPreferencesFromResource(R.xml.fragment_reader_settings, rootKey)
        else
            setPreferencesFromResource(R.xml.fragment_full_settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val supportActionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        supportActionBar.title = getString(R.string.settings)
        supportActionBar.setDisplayHomeAsUpEnabled(true)

        IdleController.getInstance().resetIdleTimer()
    }
}