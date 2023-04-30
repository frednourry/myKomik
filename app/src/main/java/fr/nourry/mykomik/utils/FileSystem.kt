package fr.nourry.mykomik.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.os.EnvironmentCompat
import androidx.documentfile.provider.DocumentFile
import fr.nourry.mykomik.App
import fr.nourry.mykomik.database.ComicEntry
import timber.log.Timber
import java.io.*
import java.util.*

val comicExtensionList = listOf("cbr", "cbz", "pdf")

fun concatPath(path1:String, path2:String):String {
    return path1+File.separator+path2
}

fun isFileExists(path: String):Boolean {
    val f = File(path)
    return f.exists()
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

fun deleteDocumentFile(docFile:DocumentFile?) {
    if (docFile == null) {
        Timber.v("deleteDocumentFile : docFile = null")
    } else {
        if (docFile.delete()) {
            Timber.v("deleteDocumentFile OK : $docFile")
        } else {
            Timber.w("deleteDocumentFile not OK : $docFile")
        }
    }
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

fun getSizeInMo(size:Long): Float {
        return size.toFloat()/1048576f     // NOTE: 1048576 = 1024 x 1024
}

fun getLocalDirName(rootTreeUri:Uri?, currentUri:Uri?):String {
    Timber.d("getLocalDirName rootTreeUri=$rootTreeUri currentUri=$currentUri")
    if (currentUri != null && rootTreeUri != null) {
        val rootLastSegment = rootTreeUri.lastPathSegment
        val currentLastSegment = currentUri.lastPathSegment

        if (rootLastSegment != null && currentLastSegment != null) {
            val lastSlash = rootLastSegment.lastIndexOf('/')
            return currentLastSegment.substring(lastSlash)
        }
    }
    return "--"
}



// Get the last modification date of a file
fun getReadableDate(l:Long): String {
    val date = Date(l)
    return date.toString()
}

// Return the extension of a filename
fun getExtension(filename:String): String {
    val lastPointPos = filename.lastIndexOf('.')
    return if (lastPointPos != -1 && filename.length > lastPointPos+1)
        filename.substring(lastPointPos+1).lowercase()
    else
        ""
}

fun deleteExtension(filename:String): String = filename.substring(0, filename.lastIndexOf('.'))

fun getComicEntriesFromDocFile(docFile: DocumentFile, bOnlyFile:Boolean = false): List<ComicEntry> {
    Timber.d("getComicEntriesFromDocFile:: ${docFile.uri} bOnlyFile=$bOnlyFile")
    var resultList:List<ComicEntry> = emptyList()

    val childDocFiles = docFile.listFiles()

    if (childDocFiles.isNotEmpty()) {
        val comicsList:MutableList<ComicEntry> = mutableListOf()

        // Convert DocumentFile in ComicEntry
        for (file in childDocFiles) {
            comicsList.add(ComicEntry.createFromDocFile(file))
        }

        // Filter directories and comics
        val mDirectories:MutableList<ComicEntry> = mutableListOf()
        val mComics : MutableList<ComicEntry> = mutableListOf()
        for (comic in comicsList) {
            if (comic.isDirectory) {
                if (!bOnlyFile)
                    mDirectories.add(comic)
            } else {
                // Test the extension
                if (comic.extension in comicExtensionList)
                    mComics.add(comic)
            }

        }

        // Sort by name, both directories and comics
        val directoryList:List<ComicEntry> = if (!bOnlyFile) {
            mDirectories.sortedWith(compareBy { it.name })
        } else {
            emptyList()
        }

        val comicList = mComics.sortedWith(compareBy { it.name })

        // Assemble result
        resultList = directoryList.plus(comicList)
    }

    return resultList
}

fun getDocumentFileFromUri(context:Context, uri: Uri):DocumentFile? {
    return DocumentFile.fromTreeUri(context, uri)
}

// Find a given uri in a DocumentFile (which is a directory)
// Returns a list of Uri from 'uri' to 'docFile.uri'
fun findUriInDocumentFile(rootDocFile:DocumentFile, uri:Uri):MutableList<Uri> {
    var result = mutableListOf<Uri>()
    if (rootDocFile.isDirectory) {
        for (docFile in rootDocFile.listFiles()) {
            if (docFile.uri.compareTo(uri) == 0) {
                result.add(rootDocFile.uri)
                break
            } else if (docFile.isDirectory) {
                val uriList = findUriInDocumentFile(docFile, uri)
                if (uriList.isNotEmpty()) {
                    uriList.add(rootDocFile.uri)
                    return uriList
                }
            }
        }
    }
    return result
}

// Get a temporary file that doesn't exist
// tempDirectory should exist !
fun getTempFile(tempDirectory:File, name:String, checkExist:Boolean):File {        // "current" = same temp dir than the ComicLoadingManager
    var tempFile =  concatPath(tempDirectory.absolutePath, "$name.tmp")
    var cpt = 0
    var file = File(tempFile)
    if (!checkExist)
        return file

    while (file.exists()) {
        cpt++
        tempFile =  concatPath(tempDirectory.absolutePath, "$name-$cpt.tmp")
        file = File(tempFile)
    }
    return file
}
fun readTextFromUri(context:Context, uri: Uri, file:File): File? {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    } catch (e:Exception) {
        Timber.e(e.stackTraceToString())
        return null
    }
    return file
}

@Throws(IOException::class)
fun File.copyTo(file: File) {
    inputStream().use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}
