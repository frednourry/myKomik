package fr.nourry.mynewkomik.utils

import android.content.Context
import android.os.Environment
import timber.log.Timber
import java.io.File

fun getComicsDirectory(appContext: Context): File {
    val storageDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
/*    val storageDir2 = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    val r = Environment.getRootDirectory()
    val d = Environment.getDataDirectory()
    val m = appContext.getExternalMediaDirs()
    val f = appContext.getExternalFilesDir(null)
    val fd = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
//    val s = Environment.getStorageDirectory()
    val e = Environment.getExternalStorageDirectory()
    return File(e, "")*/
    return storageDir!!
}

fun isDirExists(path: String):Boolean {
    val dir = File(path)
    return dir.isDirectory
}

fun isValidDir(file:File):Boolean {
    return file.exists() && file.canRead()  // NOTE: /storage/emulated (and before) returns false
}

fun getDirectoriesList(dir: File): List<File> {
    Timber.d("getDirList:: ${dir.absolutePath}")
    val l = dir.listFiles()

    if (l != null) {
        l.sortedWith(compareBy{it.name})
        val temp = l.filter { f-> (f.isDirectory) }
        Timber.d(temp.toString())
        return temp
    }
    return emptyList()
}

fun getComicsFromDir(dir: File): List<File> {
    Timber.d("getComicFilesFromDir:: ${dir.absolutePath}")
    val l = dir.listFiles()
    if (l != null) {
        val list = l.sortedWith(compareBy{it.name})
        val directory = list.filter { f-> (f.isDirectory) }
        val comics = list.filter { f-> ((f.extension=="cbz" || f.extension=="cbr") && f.isFile) } //.sorted()
        return directory.plus(comics)
    }
    return emptyList()
}

fun isFilePathAnImage(filename:String) : Boolean {
    val name = filename.lowercase()
    return name.endsWith("jpg")||name.endsWith("gif")||name.endsWith("png")
}

fun getImageFilesFromDir(dir: File): List<File> {
    Timber.d("getImageFilesFromDir:: ${dir.absolutePath}")
    val l = dir.listFiles()
    if (l != null) {
        val list = l.sortedWith(compareBy { it.name })
        return list.filter { f -> ( (f.extension == "jpg" || f.extension == "gif"|| f.extension == "png") && f.isFile) }.sorted()
    }
    return emptyList()
}

fun clearFilesInDir(dir:File) {
    if (dir.exists() && dir.isDirectory) {
        val list = dir.listFiles()
        if (list != null) {
            for (file in list) {
                if (file.isFile) {
                    file.delete()
                }
            }
        }
    }
}

/*
/// https://stackoverflow.com/questions/57116335/environment-getexternalstoragedirectory-deprecated-in-api-level-29-java
@RequiresApi(Build.VERSION_CODES.N)
fun myGetExternalStorageDir(appContext: Context): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getPrimaryStorageVolumeForAndroid11AndAbove(appContext) else getPrimaryStorageVolumeBeforeAndroid11(appContext)
}

@TargetApi(Build.VERSION_CODES.R)
private fun getPrimaryStorageVolumeForAndroid11AndAbove(appContext:Context): String? {
    val myStorageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val mySV = myStorageManager.primaryStorageVolume
    return mySV.directory!!.path
}

@RequiresApi(Build.VERSION_CODES.N)
private fun getPrimaryStorageVolumeBeforeAndroid11(appContext:Context): String? {
    var volumeRootPath = ""
    val myStorageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val mySV = myStorageManager.primaryStorageVolume
    var storageVolumeClazz: Class<*>? = null
    try {
        storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
        val getPath: Method = storageVolumeClazz.getMethod("getPath")
        volumeRootPath = getPath.invoke(mySV) as String
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return volumeRootPath
}*/
