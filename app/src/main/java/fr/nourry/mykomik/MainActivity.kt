package fr.nourry.mykomik

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import fr.nourry.mykomik.databinding.ActivityMainBinding
import fr.nourry.mykomik.loader.IdleController
import android.util.Log


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG,"onCreate")
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar
        setSupportActionBar(binding.toolbar)
        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Create the IdleController
        IdleController.getInstance().initialize(this)

/*        val comicEntryDao = App.db.comicEntryDao()
        comicEntryDao.getAllComicEntries().observe(this) { comicEntries ->
            Log.d(TAG,"comicEntries = $comicEntries")
        }*/

        // To locate the origin of the message : "A resource failed to call close." - just look for "StrictMode" in logs
/*        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
//                .detectNonSdkApiUsage()
//                .detectAll()
//                .detectLeakedClosableObjects()
//                .detectFileUriExposure()
//                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .penaltyDeath()
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