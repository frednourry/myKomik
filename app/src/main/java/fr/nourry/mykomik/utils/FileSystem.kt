package fr.nourry.mykomik.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import fr.nourry.mykomik.database.ComicEntry
import android.util.Log
import java.io.*
import java.net.URLDecoder
import java.util.*

fun concatPath(path1:String, path2:String):String {
    return path1+File.separator+path2
}

fun isFileExists(path: String):Boolean {
    val f = File(path)
    return f.exists()
}

fun getFilesInDirectory(dir:File) : Array<File> {
    if (dir.exists() && dir.exists()) {
        return dir.listFiles()?: arrayOf()
    }
    return arrayOf()
}

// Delete files in a directory
fun clearFilesInDir(dir:File) {
    Log.v("FileSystem","clearFilesInDir(${dir.absoluteFile})")
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
    Log.v("FileSystem","deleteFile(${f.absoluteFile})")
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
            Log.e("FileSystem","Error while deleting a file")
            e.printStackTrace()
        }
    }
    return true
}

fun deleteComic(context:Context, comic:ComicEntry):Boolean {
    try {
        return DocumentsContract.deleteDocument(context.contentResolver, comic.uri)
    } catch (e:Exception) {
        Log.w("FileSystem","deleteComic:: error while deleting comic : ${comic.uri}")
        e.printStackTrace()
        return false
    }
}

fun fileRename(src:File, dest:File):Boolean {
    return src.renameTo(dest)
}

// Create a directory if it's not exists
fun createDirectory(path:String) {
    val dir = File(path)
    if (dir.exists()) {
        Log.v("FileSystem","createDirectory:: $path already exists")
        return
    } else {
        if (dir.mkdirs()) {
            Log.v("FileSystem","createDirectory:: $path created")
        } else {
            Log.w("FileSystem","createDirectory:: $path :: error while creating")
        }
    }
}

fun getSizeInMo(size:Long): Float {
        return size.toFloat()/1048576f     // NOTE: 1048576 = 1024 x 1024
}

// Build a path from the rootUri (included) to the currentUri (included too)
fun getLocalDirName(rootTreeUri:Uri?, currentUri:Uri?):String {
    Log.d("FileSystem","getLocalDirName rootTreeUri=$rootTreeUri currentUri=$currentUri")
    if (currentUri != null && rootTreeUri != null) {
        val rootName = getLocalName(rootTreeUri)

        val rootLastSegment = rootTreeUri.lastPathSegment ?: ""
        var currentLastSegment = currentUri.lastPathSegment ?: ""

        return if (rootLastSegment != "" && currentLastSegment != "") {
                currentLastSegment = currentLastSegment.replace(rootLastSegment, rootName)
                currentLastSegment
            } else {
                rootName
            }
    }
    return "--"
}

// Get the end of the lastSegment
fun getLocalName(uri:Uri?):String {
    if (uri != null) {
        val str = URLDecoder.decode(uri.lastPathSegment, "utf-8")
        val lastSlash = str.lastIndexOf('/')
        Log.d("FileSystem","    getLocalName return ${str.substring(lastSlash)}")
        return str.substring(lastSlash)
    }
    return ""
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
        Log.v("FileSystem","getComicFromUri uri = $uri")

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
                    val name = URLDecoder.decode(c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)?:1), "utf-8")
                    val mime = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)?:2)
                    val size = c.getLong(c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)?:3)
                    val lastModified = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?:4)

                    Log.v("FileSystem","getComicFromUri:: -> name=$name size=$size mime=$mime lastModified=$lastModified")
                    return if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                        if (bOnlyFile)
                            null
                        else
                            ComicEntry(uri, getParentUriPath(uri), name, "", lastModified.toLong(), size, true)
                    } else {
                        ComicEntry(uri, getParentUriPath(uri), name, getExtension(name), lastModified.toLong(), size, false)
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            Log.w("FileSystem","getComicFromUri $uri Failed query: $e")
        } finally {
            c?.close()
        }

        Log.w("FileSystem","getComicFromUri COMIC NOT FOUND ! $uri ")
        return null
    }
fun getComicFromIntentUri(context: Context, uri:Uri?):ComicEntry? {
    Log.v("FileSystem","getComicFromUri_content uri = $uri")

    if (uri == null)
        return null

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
//        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    var c:Cursor? = null
    try {
        c = context.contentResolver.query(uri, projection, null, null, null)
        if (c != null) {
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)?:1)
                val size = c.getLong(c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)?:3)
                Log.v("FileSystem","getComicFromIntentUri:: -> name=$name size=$size")
                val extension = getExtension(name)
                return ComicEntry(uri, "", name,extension, 0, size, false)
            }
        }
    } catch (e: java.lang.Exception) {
        Log.w("FileSystem","getComicFromIntentUri $uri Failed query: $e")
    } finally {
        c?.close()
    }

    Log.w("FileSystem","getComicFromIntentUri COMIC NOT FOUND ! $uri ")
    return null
}

// Retrieves a list of comics uri order by its type and name
// Precond: the given uri is a directory
fun getComicEntriesFromUri(context: Context, comicExtensionList:List<String>, uri:Uri, bOnlyFile:Boolean = false): List<ComicEntry> {
    Log.v("FileSystem","getComicEntriesFromUri uri = $uri")

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
                val documentId: String = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)?:0)
                var name0 = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)?:1)
                name0 = name0.replace("%(?![0-9a-fA-F]{2})".toRegex(), "%25")
                name0 = name0.replace("\\+".toRegex(), "%2B")
                name0 = name0.replace("!", "\\!")
                val name = URLDecoder.decode(name0, "utf-8")
                val mime = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)?:2)
                val size = c.getLong(c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)?:3)
                val lastModified = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?:4)

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                Log.v("FileSystem","getComicEntriesFromUri :: documentUri = $documentUri")

                if (!bOnlyFile && DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    resultDirs.add(ComicEntry(documentUri, uri.toString(), name, "", lastModified?.toLong() ?: 0L, 0, true))
                } else {
                    // Filter by file type
                    val extension = getExtension(name)
                    if (extension in comicExtensionList) {
                        resultComics.add(ComicEntry(documentUri, uri.toString(), name, extension, lastModified?.toLong() ?: 0L, size, false))
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
        Log.w("FileSystem","getComicEntriesFromUri :: Failed query: $e")
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

/**
 * Copy a file from its Uri into a given file and returns the file if ok
 */
fun copyFileFromUri(context:Context, uri: Uri, file:File): File? {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    } catch (e:Exception) {
        Log.e("FileSystem",e.stackTraceToString())
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
    Log.v("FileSystem","getDirectoryUrisFromUri uri = $uri")

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
                val documentId: String = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)?:0)
                val mime = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)?:2)

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
        Log.w("FileSystem","getDirectoryUrisFromUri :: Failed query: $e")
    } finally {
        c?.close()
    }
    return results
}
