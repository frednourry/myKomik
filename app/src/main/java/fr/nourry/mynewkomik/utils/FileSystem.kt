package fr.nourry.mynewkomik.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.EnvironmentCompat
import timber.log.Timber
import java.io.File


data class VolumeLabel (val name: String, val path:String, val isPrimary:Boolean)

fun initVolumeDetection(appContext: Context): ArrayList<VolumeLabel>? {
    return getSdCardPaths(appContext, true)
}

//////////////// https://stackoverflow.com/questions/11281010/how-can-i-get-the-external-sd-card-path-for-android-4-0/27197248#27197248
/**
 * returns a list of all available sd cards paths, or null if not found.
 *
 * @param includePrimaryExternalStorage set to true if you wish to also include the path of the primary external storage
 */
fun getSdCardPaths(context: Context, includePrimaryExternalStorage: Boolean): ArrayList<VolumeLabel>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        if (!storageVolumes.isNullOrEmpty()) {
            val primaryVolume = storageManager.primaryStorageVolume
            val result = ArrayList<VolumeLabel>(storageVolumes.size)
            for (storageVolume in storageVolumes) {
                val volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        storageVolume.mediaStoreVolumeName!!
                    else
                        storageVolume.getDescription(context)+if (storageVolume.uuid != null) ": ("+storageVolume.uuid.toString()+")" else ""
                val volumePath = getVolumePath(storageVolume) ?: continue
                if (storageVolume.uuid == primaryVolume.uuid || storageVolume.isPrimary) {
                    if (includePrimaryExternalStorage)
                        result.add(VolumeLabel(volumeName, volumePath, storageVolume.isPrimary))
                    continue
                }
                result.add(VolumeLabel(volumeName, volumePath, storageVolume.isPrimary))
            }
            return if (result.isEmpty()) null else result
        }
    }
    val externalCacheDirs = ContextCompat.getExternalCacheDirs(context)
    if (externalCacheDirs.isEmpty())
        return null
    if (externalCacheDirs.size == 1) {
        if (externalCacheDirs[0] == null)
            return null
        val storageState = EnvironmentCompat.getStorageState(externalCacheDirs[0])
        if (Environment.MEDIA_MOUNTED != storageState)
            return null
        if (!includePrimaryExternalStorage && Environment.isExternalStorageEmulated())
            return null
    }
    val result = ArrayList<VolumeLabel>()
    if (externalCacheDirs[0] != null && (includePrimaryExternalStorage || externalCacheDirs.size == 1))
        result.add(VolumeLabel("No Name", getRootOfInnerSdCardFolder(context, externalCacheDirs[0]).absolutePath, true))
    for (i in 1 until externalCacheDirs.size) {
        val file = externalCacheDirs[i] ?: continue
        val dir = getRootOfInnerSdCardFolder(context, externalCacheDirs[i])
        val name = dir.name
        val storageState = EnvironmentCompat.getStorageState(file)
        if (Environment.MEDIA_MOUNTED == storageState)
            result.add(VolumeLabel(name, dir.absolutePath, false))
    }
    return if (result.isEmpty()) null else result
}

fun getRootOfInnerSdCardFolder(context: Context, inputFile: File): File {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        storageManager.getStorageVolume(inputFile)?.let {
            val result = getVolumePath(it)
            if (result != null)
                return File(result)
        }
    }
    var file: File = inputFile
    val totalSpace = file.totalSpace
    while (true) {
        val parentFile = file.parentFile
        if (parentFile == null || parentFile.totalSpace != totalSpace || !parentFile.canRead())
            return file
        file = parentFile
    }
}

@RequiresApi(Build.VERSION_CODES.N)
fun getVolumePath(storageVolume: StorageVolume): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        return storageVolume.directory?.absolutePath
    try {
        val storageVolumeClazz = StorageVolume::class.java
        val getPath = storageVolumeClazz.getMethod("getPath")
        return getPath.invoke(storageVolume) as String
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
/////////////////////////////////////////////////////////////////



fun getDefaultDirectory(appContext: Context): File {
    val storageDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
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
        val temp = l.sortedWith(compareBy{it.name}).filter { f-> (f.isDirectory) }
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
