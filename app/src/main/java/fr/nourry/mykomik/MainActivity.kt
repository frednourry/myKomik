package fr.nourry.mykomik

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import timber.log.Timber

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

/*        val comicEntryDao = App.db.comicEntryDao()
        comicEntryDao.getAllComicEntries().observe(this) { comicEntries ->
            Timber.d("comicEntries = $comicEntries")
        }*/
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp()
    }
}