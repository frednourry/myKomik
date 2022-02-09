package fr.nourry.mynewkomik

import android.app.Application
import android.content.Context
import timber.log.Timber
import fr.nourry.mynewkomik.utils.getDefaultDirectory
import java.io.File

class App: Application() {

    companion object {
        private lateinit var appContext: Context

        var currentDir: File? = null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        App.appContext = this
    }
}