package fr.nourry.mykomik

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.room.Room
import io.github.frednourry.FnyLib7z
import fr.nourry.mykomik.database.AppDatabase
import fr.nourry.mykomik.database.DATABASE_NAME
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.pageslider.DisplayOption
import fr.nourry.mykomik.utils.PhysicalConstants
import android.util.Log
import java.io.File

class App: Application() {

    companion object {
        const val TAG = "App"

        lateinit var appContext: Context
        lateinit var physicalConstants: PhysicalConstants
        lateinit var db : AppDatabase
        lateinit var packageInfo: PackageInfo
        lateinit var appName : String

        lateinit var pageCacheDirectory : File
        lateinit var thumbnailCacheDirectory : File

        var isGuestMode = false

        var rootTreeUri: Uri? = null
        var currentTreeUri: Uri? = null

        var appIntentUri:Uri? = null        // Uri that will be given by an Intent ACTION_VIEW
        var isSimpleViewerMode = false      // True only the app starts with an Intent ACTION_VIEW (appIntentUri!= null)

        fun resetSimpleViewerMode() {
            appIntentUri = null
            isSimpleViewerMode = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        // Initialize my 7z lib
        FnyLib7z.getInstance().initialize(appContext)
        Log.v(TAG,"7z version : "+ FnyLib7z.get7zVersionInfo())

        physicalConstants = PhysicalConstants.newInstance(appContext)

        packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }

        val applicationInfo: ApplicationInfo = applicationInfo
        val stringId = applicationInfo.labelRes
        appName = if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else getString(stringId)

        // Define a local temp directories to work on images (to unarchive comics)
        thumbnailCacheDirectory = File(cacheDir.absolutePath)
        pageCacheDirectory = File(cacheDir.absolutePath + File.separator + "current")
        if (!pageCacheDirectory.exists()) {
            pageCacheDirectory.mkdirs()
        }

        // Init the ComicLoadingManger
        ComicLoadingManager.getInstance().initialize(this, App.thumbnailCacheDirectory, App.pageCacheDirectory)

        // Init the database
        db = Room.databaseBuilder(this, AppDatabase::class.java, DATABASE_NAME)
//            .allowMainThreadQueries()     // Very bad !!
            .build()
    }
}