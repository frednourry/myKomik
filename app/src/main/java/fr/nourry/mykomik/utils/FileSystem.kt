package fr.nourry.mykomik.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import fr.nourry.mykomik.database.ComicEntry
import timber.log.Timber
import java.io.*
import java.net.URLDecoder
import java.util.*

val comicExtensionList = listOf("cbr", "cbz", "pdf", "rar", "zip")

fun concatPath(path1:String, path2:String):String {
    return path1+File.separator+path2
}

fun isFileExists(path: String):Boolean {
    val f = File(path)
    return f.exists()
}

// Return true if and only if the extension is 'jpg', 'gif', 'png', 'jpeg' or 'webp'
fun isImageExtension(extension:String) : Boolean {
    return (extension == "jpg") || (extension == "gif") || (extension == "png") || (extension == "jpeg") || (extension == "webp")
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

fun deleteComic(context:Context, comic:ComicEntry):Boolean {
    try {
        return DocumentsContract.deleteDocument(context.contentResolver, comic.uri)
    } catch (e:Exception) {
        Timber.w("deleteComic:: error while deleting comic : ${comic.uri}")
        e.printStackTrace()
        return false
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

fun stripExtension(filename:String): String = filename.substring(0, filename.lastIndexOf('.'))

fun getComicFromUri(context: Context, uri:Uri?, bOnlyFile:Boolean = false):ComicEntry? {
        Timber.v("getComicFromUri uri = $uri")

        if (uri == null)
            return null

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        var c:Cursor? = null
        try {
            c = context.contentResolver.query(uri, projection, null, null, null)
            if (c != null) {
                while (c.moveToNext()) {
                    val name = URLDecoder.decode(c.getString(1), "utf-8")
                    val mime = c.getString(2)
                    val size = c.getString(3)
                    val lastModified = c.getString(4)

                    Timber.i("getComicFromUri:: -> name=$name size=$size mime=$mime lastModified=$lastModified")
                    return if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                        if (bOnlyFile)
                            null
                        else
                            ComicEntry(uri, getParentUriPath(uri), name, "", lastModified.toLong(), size.toLong(), true)
                    } else {
                        ComicEntry(uri, getParentUriPath(uri), name, getExtension(name), lastModified.toLong(), size.toLong(), false)
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            Timber.w("getComicFromUri $uri Failed query: $e")
        } finally {
            c?.close()
        }

        Timber.w("getComicFromUri COMIC NOT FOUND ! $uri ")
        return null
    }

// Retrieves a list of comics uri order by its type and name
// Precond: the given uri is a directory
fun getComicEntriesFromUri(context: Context, uri:Uri, bOnlyFile:Boolean = false): List<ComicEntry> {
    Timber.v("getComicEntriesFromDocFile uri = $uri")

    val docId = DocumentsContract.getDocumentId(uri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
    var results: List<ComicEntry> = emptyList()
    val resultDirs: MutableList<ComicEntry> = mutableListOf()
    val resultComics: MutableList<ComicEntry> = mutableListOf()

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    var c: Cursor? = null
    try {
        c = context.applicationContext.contentResolver.query(childrenUri, projection, null, null, null)
        if (c != null) {
            // Get the URIs
            while (c.moveToNext()) {
                val documentId: String = c.getString(0)
                val name = URLDecoder.decode(c.getString(1), "utf-8")
                val mime = c.getString(2)
                val size = c.getString(3)
                val lastModified = c.getString(4)

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                Timber.v("getComicEntriesFromDocFile :: documentUri = $documentUri")

                if (!bOnlyFile && DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    resultDirs.add(ComicEntry(documentUri, uri.toString(), name, "", lastModified.toLong(), 0, true))
                } else {
                    // Filter by file type
                    val extension = getExtension(name)
                    if (extension in comicExtensionList) {
                        resultComics.add(ComicEntry(documentUri, uri.toString(), name, extension, lastModified.toLong(), size.toLong(), false))
                    }
                }
            }

            // Order each list
            val tempResultDirs = resultDirs.sortedBy { it.name }
            val tempResultComics = resultComics.sortedBy { it.name }

            // Concat
            results = tempResultDirs.plus(tempResultComics)
        }
    } catch (e: java.lang.Exception) {
        Timber.w("getComicEntriesFromDocFile :: Failed query: $e")
    } finally {
        c?.close()
    }
    return results
}

// Get the parent uri (if any) by looking "%2F"
fun getParentUriPath(uri:Uri):String {
    val path = uri.toString()
    val i = path.lastIndexOf("%2F")
    return if (i>0) {
        path.substring(0, i)
    } else
        ""
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

//
// Specific to IdleController
//

// Retrieves a list of sub-directory URIs (and their children)
fun getDirectoryUrisFromUri(context: Context, uri:Uri): List<Uri> {
    Timber.v("getDirectoryUrisFromUri uri = $uri")

    val docId = DocumentsContract.getDocumentId(uri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
    val resultChildrenDirs: MutableList<Uri> = mutableListOf()
    var results: List<Uri> = listOf()

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    var c: Cursor? = null
    try {
        c = context.applicationContext.contentResolver.query(childrenUri, projection, null, null, null)
        if (c != null) {
            // 1st PASS : get the URIs in the current directory (ie 'uri')
            while (c.moveToNext()) {
                val documentId: String = c.getString(0)
                val mime = c.getString(2)

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    resultChildrenDirs.add(documentUri)
                } else {
                    // Nothing...
                }
            }

            // Order list
            val sortedResultDirs = resultChildrenDirs.sortedBy { uri.path }

            // Sub-directories children
            for (subDirUri in sortedResultDirs) {
                val tempSubDirResult = getDirectoryUrisFromUri(context, subDirUri)
                results = results.plus(tempSubDirResult)
            }
            results = results.plus(uri)
        }
    } catch (e: java.lang.Exception) {
        Timber.w("getDirectoryUrisFromUri :: Failed query: $e")
    } finally {
        c?.close()
    }
    return results
}
