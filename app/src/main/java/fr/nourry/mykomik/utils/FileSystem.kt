package fr.nourry.mykomik.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.EnvironmentCompat
import fr.nourry.mykomik.database.ComicEntry
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

fun concatPath(path1:String, path2:String):String {
    return path1+File.separator+path2
}


fun getDefaultDirectory(appContext: Context): File {
    val storageDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    return storageDir!!
}

fun isFileExists(path: String):Boolean {
    val f = File(path)
    return f.exists()
}


fun isDirExists(path: String):Boolean {
    val dir = File(path)
    return dir.isDirectory
}

fun isValidDir(file:File):Boolean {
    return file.exists() && file.canRead()  // NOTE: /storage/emulated (and before) returns false
}

fun getDirectoriesList(dir: File): List<File> {
    Timber.d("getDirectoriesList:: ${dir.absolutePath}")
    val l = dir.listFiles()

    if (l != null) {
        val temp = l.sortedWith(compareBy{it.name}).filter { f-> (f.isDirectory) }
        Timber.d(temp.toString())
        return temp
    }
    return emptyList()
}

fun getComicsFromDir(dir: File, bOnlyFile:Boolean = false): List<File> {
    Timber.d("getComicFilesFromDir:: ${dir.absolutePath}")
    val l = dir.listFiles()
    if (l != null) {
        val list = l.sortedWith(compareBy{it.name})
        val directory = if (bOnlyFile) emptyList() else list.filter { f-> (f.isDirectory) }
        val comics = list.filter { f-> ((f.extension=="cbz" || f.extension=="cbr") && f.isFile) } //.sorted()
        return directory.plus(comics)
    }
    return emptyList()
}

fun getComicEntriesFromDir(dir: File, bOnlyFile:Boolean = false): List<ComicEntry> {
    Timber.d("getComicEntriesFromDir:: ${dir.absolutePath}")
    val listFiles = getComicsFromDir(dir, bOnlyFile)
    var resultList:List<ComicEntry> = emptyList()

    for (file in listFiles) {
        resultList = resultList.plusElement(ComicEntry(file))
    }
    return resultList
}


// Return true if and only if the extension is 'jpg', 'gif', 'png' or 'jpeg'
fun isImageExtension(extension:String) : Boolean {
    return (extension == "jpg") || (extension == "gif") ||(extension == "png") ||(extension == "jpeg")
}

// Return true if the given file path is from an image
fun isFilePathAnImage(filename:String) : Boolean {
    val ext = File(filename).extension.lowercase()
    return isImageExtension(ext)
}

// Return a file list from a given dir where all files are images
fun getImageFilesFromDir(dir: File): List<File> {
    Timber.d("getImageFilesFromDir:: ${dir.absolutePath}")
    val l = dir.listFiles()
    if (l != null) {
        val list = l.sortedWith(compareBy { it.name })
        return list.filter { f -> val ext = f.extension.lowercase(); ( f.isFile && isImageExtension (ext)) }.sorted()
    }
    return emptyList()
}

// Delete files in a directory
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

// Delete a file (a simple file or a directory)
fun deleteFile(f:File): Boolean {
    Timber.v("deleteFile(${f.absoluteFile})")
    if (f.exists()) {
        try {
            if (f.isDirectory) {
                // List all the files in the directory and delete them
                val list = f.listFiles()
                for (file in list!!) {
                    deleteFile(file)
                }
            }
            return f.delete()
        } catch (e: SecurityException) {
            Timber.e("Error while deleting a file")
            e.printStackTrace()
        }
    }
    return true
}

// Create a directory if it's not exists
fun createDirectory(path:String) {
    val dir = File(path)
    if (dir.exists()) {
        Timber.v("createDirectory:: $path already exists")
        return
    } else {
        if (dir.mkdirs()) {
            Timber.v("createDirectory:: $path created")
        } else {
            Timber.w("createDirectory:: $path :: error while creating")
        }
    }
}
