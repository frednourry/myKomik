package fr.nourry.mynewkomik

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import fr.nourry.mynewkomik.loader.ComicLoadingManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pour le controller de la navigation
        val navControler = findNavController(R.id.nav_host_fragment)
        setupActionBarWithNavController(navControler)
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp()
    }
}