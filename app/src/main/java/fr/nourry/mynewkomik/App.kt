package fr.nourry.mynewkomik

import android.app.Application
import android.content.Context
import fr.nourry.mynewkomik.utils.PhysicalConstants
import timber.log.Timber
import fr.nourry.mynewkomik.utils.getDefaultDirectory
import java.io.File

class App: Application() {

    companion object {
        lateinit var appContext: Context
        lateinit var physicalConstants: PhysicalConstants

        var currentDir: File? = null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        App.appContext = this
        physicalConstants = PhysicalConstants.newInstance(appContext)
    }
}