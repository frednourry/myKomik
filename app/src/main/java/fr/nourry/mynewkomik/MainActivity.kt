package fr.nourry.mynewkomik

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val comicEntryDao = App.database.comicEntryDao()

/*        var id : Long = 0
//        Executors.newSingleThreadExecutor().execute {
//            id = comicDBDao.insertComic(ComicDB(0, "myKey", "myPath", "MyName"))
//        }
        Timber.d("insert:: id=$id")
*/

/*        val l = comicEntryDao.getAllComicEntries().observe(this, Observer{
            comicEntries ->
                Timber.d("comicEntries = $comicEntries")
        })*/
    }

    override fun onSupportNavigateUp(): Boolean {
        return findNavController(R.id.nav_host_fragment).navigateUp()
    }
}