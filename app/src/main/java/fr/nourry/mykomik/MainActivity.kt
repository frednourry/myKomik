package fr.nourry.mykomik

import android.os.Bundle
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
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        Timber.d("onUserInteraction")
        IdleController.getInstance().resetIdleTimer()
    }
}