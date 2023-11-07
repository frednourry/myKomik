package fr.nourry.mykomik

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import fr.nourry.mykomik.databinding.ActivitySimpleViewerBinding
import fr.nourry.mykomik.utils.getComicFromIntentUri
import timber.log.Timber

class SimpleViewerActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivitySimpleViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")

        // Check if this app was opened by an intent
        if (intent != null) {
            if (intent.action == Intent.ACTION_VIEW) {
                Timber.i("There is an intent ACTION_VIEW :")
                intent.data?.let { uri ->
                    Timber.i(" uri = $uri")
                    if (uri != null) {
                        val comic = getComicFromIntentUri(this, uri)
                        Timber.i(" comic = $comic")
                        App.appIntentUri = uri
                    }
                }
            }
        }

        super.onCreate(savedInstanceState)

        binding = ActivitySimpleViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_simple)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_simple)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}