package fr.nourry.mynewkomik

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import androidx.room.Room
import fr.nourry.mynewkomik.database.AppDatabase
import fr.nourry.mynewkomik.database.DATABASE_NAME
import fr.nourry.mynewkomik.utils.PhysicalConstants
import timber.log.Timber
import java.io.File

class App: Application() {

    companion object {
        lateinit var appContext: Context
        lateinit var physicalConstants: PhysicalConstants
        lateinit var db : AppDatabase
        lateinit var packageInfo: PackageInfo

        var currentDir: File? = null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        App.appContext = this
        physicalConstants = PhysicalConstants.newInstance(appContext)
        packageInfo = packageManager.getPackageInfo(packageName, 0)

        db = Room.databaseBuilder(this, AppDatabase::class.java, DATABASE_NAME)
//            .allowMainThreadQueries()     // Very bad !!
            .build()
    }
}