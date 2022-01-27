package fr.nourry.mynewkomik

import android.app.Application
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import timber.log.Timber
import fr.nourry.mynewkomik.utils.getComicsDirectory
import java.io.File

class App: Application() {

    companion object {
        private lateinit var appContext: Context

        var currentDir: File? = null
        val comicsDefaultDirectory by lazy {
            getComicsDirectory(appContext)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        App.appContext = this
    }
}