package fr.nourry.mykomik.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import fr.nourry.mykomik.App
import fr.nourry.mykomik.utils.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class GetPagesWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_URI                   = "archiveUri"
        const val KEY_PAGES_LIST                    = "pages"
        const val KEY_PAGES_DESTINATION_PATH        = "imageDestinationPath"
        const val KEY_CURRENT_INDEX                 = "currentIndex"
        const val KEY_CURRENT_PATH                  = "currentPath"
        const val KEY_NB_PAGES                      = "nbPages"

        const val BUFFER_SIZE = 2048
    }

    private var nbPages:Int = 0

    override fun doWork(): Result {
        Timber.d("GetPagesWorker.doWork")

        val archiveUriPath = inputData.getString(KEY_ARCHIVE_URI)
        val destPath = inputData.getString(KEY_PAGES_DESTINATION_PATH)
        val pagesListStr = inputData.getString(KEY_PAGES_LIST)

        Timber.d("    archivePath=$archiveUriPath")
        Timber.d("    destPath=$destPath")
        Timber.d("    pagesListStr=$pagesListStr")
        Timber.d("    nbPages=$nbPages")

        val pagesList = pagesListStr?.split(',')?.map { it.toInt() }
        Timber.d("    pagesList=$pagesList")

        if (pagesList != null && archiveUriPath != null && destPath!= null) {
            val archiveUri = Uri.parse(archiveUriPath)
            Timber.d("    archiveUri=$archiveUri")
            val ext = getExtension(archiveUriPath).lowercase()

            var boolResult = false

            if (ext == "cbz" || ext == "zip") {
                boolResult = unzipPages(archiveUri, destPath, pagesList)
            } else if (ext == "cbr" || ext == "rar") {
                boolResult = unrarPages(archiveUri, destPath, pagesList)
            } else if (ext == "pdf") {
                boolResult = getPagesInPdfFile(archiveUri, destPath, pagesList)
            }

            if (!boolResult) {
                val outputData = workDataOf(KEY_PAGES_DESTINATION_PATH to destPath,
                    KEY_PAGES_LIST to pagesListStr,
                    KEY_NB_PAGES to nbPages)
                return Result.failure(outputData)
            }
        }

        val outputData = workDataOf(KEY_PAGES_DESTINATION_PATH to destPath,
            KEY_PAGES_LIST to pagesListStr,
            KEY_NB_PAGES to nbPages)
        return Result.success(outputData)
    }

    // FAST METHOD BUT NEED TO COPY THE FILE...
    private fun unzipPages(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unzipPages $fileUri pages=$pages")

        // Copy the uri file in a temp File
        val tmpFile = getTempFile(App.pageCacheDirectory, fileUri.toString().md5(), false)
        Timber.v("unzipCoverInFile tmpFile=${tmpFile.absoluteFile} tmpFile exists::${tmpFile.exists()}")

        if (!tmpFile.exists() && readTextFromUri(App.appContext, fileUri, tmpFile) == null) {
            Timber.v("unzipCoverInFile error in readTextFromUri")
            return false
        }

        // Unzip
        ZipFile(tmpFile.absoluteFile).use { zip ->
            try {
                val sequences = zip.entries().asSequence()
                var zipEntries : MutableList<ZipEntry> = mutableListOf()

                var cpt=0
                for (entry in sequences) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }

                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
//                        Timber.v("  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                        zipEntries.add(entry)
                    } else {
                        Timber.v("  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                    }
                    cpt++
                }

                // Reorder ZipEntry by filename
                zipEntries = ZipUtil.sortZipEntryList(zipEntries)
                nbPages = zipEntries.size

                if (!isStopped && zipEntries.isNotEmpty()) {
                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }

                        val entry = zipEntries[numPage]
                        zip.getInputStream(entry).use { input ->
                            val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                            Timber.v("  Unzip page=$numPage in $pagePath")

                            File(pagePath).outputStream().use { output ->
                                input.copyTo(output)
                            }

                            // Send a progress event
                            setProgressAsync(Data.Builder().
                            putInt(KEY_CURRENT_INDEX, numPage).
                            putString(KEY_CURRENT_PATH, pagePath).
                            putInt(KEY_NB_PAGES, nbPages).
                            build())
                        }
                    }
                }
            }
            catch (t: Throwable) {
                Timber.w("unzipPages:: error "+t.message)
            }
            finally {
                Timber.i("unzipPages :: finally...")
            }
        }
        Timber.v("END unzipPages ${tmpFile.name}")

        // Do not delete tmpFile!

        return true
    }

/*
    // SLOW METHOD DUE TO TWO PASS
    private fun unzipPages(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unzipPages $fileUri pages=$pages")

        // Unzip
        var zipEntries : MutableList<ZipEntry> = arrayListOf()

        // Make a mutableList from pages
        var wishedPages : MutableList<Int> = mutableListOf()
        for (e in pages)
            wishedPages.add(e)

        Timber.v("unzipPages $fileUri pages=$pages wishedPages=$wishedPages")

        try {
            // 1st pass : Get and order the zip entries
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.use {
                    val bufferedInputStream = BufferedInputStream(inputStream)
                    val zipInputStream = ZipInputStream(bufferedInputStream)

                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
//                            Timber.i("  entry = ${entry.name} - ${entry.size}")
                        zipInputStream.closeEntry()

                        if (!entry.isDirectory && isFilePathAnImage2(entry.name)) {
//                                Timber.v("  ENTRY :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                            zipEntries.add(entry)
                        } else {
                            Timber.v("  ENTRY :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                        }

                        entry = zipInputStream.nextEntry
                    }
                    bufferedInputStream.close()
                }
            }

            zipEntries = ZipUtil.sortZipEntryList(zipEntries)
            nbPages = zipEntries.size
            Timber.i("  nbPages = $nbPages")

            // 2nd pass : Unzip the requested pages (need to reopen the stream...)
            if (!isStopped && zipEntries.isNotEmpty()) {
                applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->

                    inputStream.use {
                        val bufferedInputStream = BufferedInputStream(inputStream)
                        val zipInputStream = ZipInputStream(bufferedInputStream)

                        var entry: ZipEntry? = zipInputStream.nextEntry
                        while (entry != null) {
                            for (numPage in wishedPages) {
                                if (isStopped) {    // Check if the work was cancelled
                                    break
                                }
                                if (entry!!.name == zipEntries[numPage].name) {
                                    // Get inputstream
                                    val pagePath:String = filePath.replace(".999.", ".%03d.".format(numPage))
                                    Timber.v("  Unzip page=$numPage in $pagePath (${entry!!.name} ${entry!!.size})")

                                    // Unzip this entry in a file
                                    File(pagePath).outputStream().use { output ->
//                                             zipInputStream.copyTo(output, entry!!.size.toInt())  // BAD because entry!!.size can returns -1...

                                        try {
                                            val data = ByteArray(BUFFER_SIZE)

                                            var cpt: Int = zipInputStream.read(data, 0, BUFFER_SIZE)
                                            while (cpt != -1) {
                                                output.write(data, 0, cpt)
                                                cpt = zipInputStream.read(data, 0, BUFFER_SIZE)
                                            }
                                            output.flush()

                                        } finally {
                                            output.close()
                                        }
                                    }
                                    zipInputStream.closeEntry()

                                    // Send a progress event
                                    setProgressAsync(Data.Builder().
                                    putInt(KEY_CURRENT_INDEX, numPage).
                                    putString(KEY_CURRENT_PATH, pagePath).
                                    putInt(KEY_NB_PAGES, nbPages).
                                    build())

                                    // Remove numPage from pages
                                    wishedPages.remove(numPage)
                                    break
                                }

                                if (wishedPages.isEmpty())
                                    // No more page to search
                                    break
                            }

                            entry = zipInputStream.nextEntry
                        }
                        bufferedInputStream.close()
                    }
                }
            }

        }
        catch (t: Throwable) {
            Timber.w("unzipPages:: error "+t.message)
        }
        finally {
            Timber.w("unzipPages :: finally...")
        }
        Timber.v("END unzipPages $fileUri")
        return true
    }
*/
    private fun unrarPages(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unrarPages $fileUri pages=$pages")

        // Unrar
        try {
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val rarArchive = Archive(inputStream)

                // Check if not a 5.x RAR ...?

                var fileHeaders: MutableList<FileHeader> = mutableListOf()
                var cpt = 0
                while (true) {
                    val fileHeader = rarArchive.nextFileHeader() ?: break
                    if (fileHeader.fullUnpackSize > 0) {
                        if (!fileHeader.isDirectory && isFilePathAnImage(fileHeader.fileName)) {
//                            Timber.v("  HEADER $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} ADDED")
                            fileHeaders.add(fileHeader)
                        } else {
                            Timber.v("  HEADER $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} SKIPPED")
                        }
                    }
                    cpt++
                }

                // Reorder fileHeader by filename
                fileHeaders = RarUtil.sortFileHeaderList(fileHeaders)
                nbPages = fileHeaders.size

                if (!isStopped && fileHeaders.isNotEmpty()) {
                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }
                        val fileHeader = fileHeaders[numPage]
                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Timber.w("  Unrar page=$numPage in $pagePath")
                        val outputStream = File(pagePath).outputStream()
                        rarArchive.extractFile(fileHeader, outputStream)
                        outputStream.close()

                        // Send a progress event
                        setProgressAsync(
                            Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                .build()
                        )
                    }
                }
                rarArchive.close()
            }
        } catch (e: RarException) {
            Timber.v("unrarPages :: RarException $e")
            return false
        } catch (e: IOException) {
            Timber.v("unrarPages :: IOException $e")
            return false
        }

        Timber.v("unrarPages ${fileUri}")
        return true
    }

    private fun getPagesInPdfFile(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Timber.v("getPagesInPdfFile $fileUri pages=$pages")

        try {
            val fileDescriptor: ParcelFileDescriptor? = applicationContext.contentResolver.openFileDescriptor(fileUri, "r")
            if (fileDescriptor != null) {
                val renderer = PdfRenderer(fileDescriptor)
                val pageCount: Int = renderer.pageCount

                nbPages = pageCount

                if (!isStopped && pageCount > 0) {
                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }

                        val page: PdfRenderer.Page = renderer.openPage(numPage)
                        val tempBitmap =
                            Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

                        val tempCanvas = Canvas(tempBitmap)
                        tempCanvas.drawColor(Color.WHITE)
                        tempCanvas.drawBitmap(tempBitmap, 0f, 0f, null)
                        page.render(
                            tempBitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        page.close()

                        if (tempBitmap == null || PdfUtil.isBitmapBlankOrWhite(tempBitmap)) {
                            // Not a valid image
                            Timber.w("getPagesInPdfFile : image not valid !")
                            //                        continue
                        }

                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Timber.w("  getPagesInPdfFile page=$numPage in $pagePath")

                        BitmapUtil.saveBitmapInFile(tempBitmap, pagePath)

                        // Send a progress event
                        setProgressAsync(
                            Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                .build()
                        )
                    }
                }
                fileDescriptor.close()
            }
        } catch (e: IOException) {
            Timber.v("getPagesInPdfFile :: IOException $e")
            return false
        }

        Timber.v("getPagesInPdfFile $fileUri")
        return true
    }

}

