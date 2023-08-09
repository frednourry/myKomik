package fr.nourry.mykomik

import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import fr.nourry.mykomik.loader.IdleController
import timber.log.Timber


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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