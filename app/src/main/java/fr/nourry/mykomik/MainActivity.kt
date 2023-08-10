package fr.nourry.mykomik

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import fr.nourry.mykomik.databinding.ActivityMainBinding
import fr.nourry.mykomik.loader.IdleController
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.toolbar)
        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.toolbar.setTitleTextColor(getColor(R.color.white))

        // Create the IdleController
        IdleController.getInstance().initialize()

/*        val comicEntryDao = App.db.comicEntryDao()
        comicEntryDao.getAllComicEntries().observe(this) { comicEntries ->
            Timber.d("comicEntries = $comicEntries")
        }*/

        // To locate the origin of the message : "A resource failed to call close." - just look for "StrictMode" in logs
/*        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )*/
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        IdleController.getInstance().resetIdleTimer()
    }
}