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
import com.github.junrar.exception.UnsupportedRarV5Exception
import com.github.junrar.rarfile.FileHeader
import io.github.frednourry.FnyLib7z
import fr.nourry.mykomik.App
import fr.nourry.mykomik.utils.*
import io.github.frednourry.itemListFnyLib7z
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream

class NoImageException(message:String): Exception(message)

class GetPagesWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_URI                   = "archiveUri"
        const val KEY_PAGES_LIST                    = "pages"
        const val KEY_PAGES_DESTINATION_PATH        = "imageDestinationPath"
        const val KEY_PAGES_CONTENT_LIST_PATH       = "contentListFilePath"
        const val KEY_CURRENT_INDEX                 = "currentIndex"
        const val KEY_CURRENT_PATH                  = "currentPath"
        const val KEY_NB_PAGES                      = "nbPages"
        const val KEY_COMIC_EXTENSION               = "extension"
        const val KEY_ERROR_MESSAGE                 = "errorMessage"

        // Same variables used to cache some datas
        var currentContentListFilePath = ""
        var currentContentList:List<itemListFnyLib7z> = listOf()
    }

    private var nbPages:Int = 0

    override fun doWork(): Result {
        Timber.d("GetPagesWorker.doWork")

        val archiveUriPath = inputData.getString(KEY_ARCHIVE_URI)
        val destPath = inputData.getString(KEY_PAGES_DESTINATION_PATH)
        val pagesListStr = inputData.getString(KEY_PAGES_LIST)
        val contentListFilePath = inputData.getString(KEY_PAGES_CONTENT_LIST_PATH)
        val extension = inputData.getString(KEY_COMIC_EXTENSION)?.lowercase() ?: ""

        Timber.v("    archivePath=$archiveUriPath")
        Timber.v("    extension=$extension")
        Timber.v("    destPath=$destPath")
        Timber.v("    pagesListStr=$pagesListStr")
        Timber.v("    nbPages=$nbPages")
        Timber.v("    contentListFilePath=$contentListFilePath")

        val pagesList = pagesListStr?.split(',')?.map { it.toInt() }
        Timber.d("    pagesList=$pagesList")

        if (pagesList != null && archiveUriPath != null && destPath!= null) {
            val archiveUri = Uri.parse(archiveUriPath)
            Timber.d("    archiveUri=$archiveUri")

            var boolResult = false
            var errorMessage = ""
            try {
/*                if (ext == "cbz" || ext == "zip") {
                    boolResult = unzipPages(archiveUri, destPath, pagesList)
                } else if (ext == "cb7" || ext == "7z") {
                    boolResult = unzipPageIn7ZipFile(archiveUri, destPath, pagesList)
                } else if (ext == "cbr" || ext == "rar") {
                    boolResult = unrarPages(archiveUri, destPath, pagesList)
*/
                if (extension == "cbz" || extension == "zip" || extension == "cb7" || extension == "7z" || extension == "cbr" || extension == "rar") {
                    boolResult = unarchivePages(archiveUri, destPath, pagesList, contentListFilePath!!)
                } else if (extension == "pdf") {
                    boolResult = getPagesInPdfFile(archiveUri, destPath, pagesList)
                }
            } catch (e:Exception) {
                boolResult = false
                errorMessage = e.message ?: ""
                Timber.e(e)
                e.printStackTrace()
            }

            if (!boolResult) {
                val outputData = workDataOf(KEY_PAGES_DESTINATION_PATH to destPath,
                    KEY_PAGES_LIST to pagesListStr,
                    KEY_NB_PAGES to nbPages,
                    KEY_ERROR_MESSAGE to errorMessage)
                return Result.failure(outputData)
            }
        }

        val outputData = workDataOf(KEY_PAGES_DESTINATION_PATH to destPath,
            KEY_PAGES_LIST to pagesListStr,
            KEY_NB_PAGES to nbPages)
        return Result.success(outputData)
    }

    /**
     * Extract pages from an archive using FnyLib7z
     */
    private fun unarchivePages(fileUri: Uri, filePath:String, indexPages:List<Int>, contentListFilePath:String):Boolean {
        Timber.v("unarchivePages $fileUri filePath=$filePath pages=$indexPages")

        try {
            // Init temp directory
            val tempPagesDirectoryPath = ComicLoadingManager.getInstance().getTempPagesDirectoryPath()
            val tempPagesDirectory = File(tempPagesDirectoryPath)

            // Get the content list of the archive (in a file)
            val listFile = File(contentListFilePath)

            if (contentListFilePath != currentContentListFilePath) {
                currentContentListFilePath = contentListFilePath
                currentContentList = emptyList()
            }

            if (listFile.exists()) {
                // No need to regenerate it, keep the same file
            } else {
                // Or else, ask FnyLib7z to retrieve it
                val result0 = FnyLib7z.getInstance().listFiles(fileUri, sortList = true, filtersList=ComicLoadingManager.imageExtensionFilterList, stdOutputPath = contentListFilePath)
                if (result0 != FnyLib7z.RESULT_OK) {
                    Timber.w("  unarchivePages :: can't retrieve the content of $fileUri in $contentListFilePath")
                    return false
                }
            }

            if (!listFile.exists()) {
                Timber.w("  unarchivePages :: content list file was not created : $contentListFilePath")
                return false
            }

            // Parse this content file, if necessary
            if (currentContentList.isEmpty()) {
                Timber.v("  unarchivePages:: parsing $contentListFilePath ...")
                currentContentList = FnyLib7z.getInstance().parseListFile(listFile)
            } else {
                Timber.v("  unarchivePages:: no need to parse $contentListFilePath ...")
            }
            nbPages = currentContentList.size
            Timber.w("  unarchivePages :: nbPages=$nbPages")

            // Built the list of files to unarchive
            val pathsToUnarchive = mutableListOf<String>()
            for (indexPage in indexPages) {
                if (indexPage < nbPages)
                    pathsToUnarchive.add(currentContentList[indexPage].name)
                else
                    Timber.v("  unarchivePages :: bad page index $indexPage (archive only contains $nbPages file(s))")
            }

            // Extract the pages
            val result = FnyLib7z.getInstance().uncompress(fileUri, dirToExtract=tempPagesDirectory, filtersList=pathsToUnarchive)
            if (result == FnyLib7z.RESULT_OK) {
                for (indexPage in indexPages) {
                    if (indexPage < nbPages) {
                        // Get the file path extracted
                        val path = currentContentList[indexPage].name
                        val simplePath = path.substring(path.lastIndexOf(File.separator) + 1)
                        val pagePath = tempPagesDirectoryPath + File.separator + simplePath
                        //Timber.v("  unarchivePages :: test page file in $pagePath")

                        // Test if the file exists in the cache
                        val pageFile = File(pagePath)
                        if (pageFile.exists()) {
                            val newPagePath = filePath.replace(".999.", ".%03d.".format(indexPage))
                            // Move tempFile into pagePath
                            fileRename(pageFile, File(newPagePath))

                            // Send a progress event
                            setProgressAsync(
                                Data.Builder().putInt(KEY_CURRENT_INDEX, indexPage)
                                    .putString(KEY_CURRENT_PATH, newPagePath)
                                    .putInt(KEY_NB_PAGES, nbPages)
                                    .build()
                            )

                        } else {
                            Timber.v("  unarchivePages :: no file extracted for index=$indexPage")
                        }
                    }
                }


/*                var arrFiles = getFilesInDirectory(tempPagesDirectory)
                Timber.v("    unarchivePages arrFiles=${arrFiles.toString()}")

                if (arrFiles.size>0) {
                    if (arrFiles.size != indexPages.size) {
                        Timber.w("    unarchivePages: not all pages have an image !")
                        // TODO ask FnyLib7z to automatically rename the files ?
                    }

                    var cpt = 0
                    for (numPage in indexPages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))

                        if (cpt< arrFiles.size) {
                            val tempFile = arrFiles[cpt]

                            // Move tempFile into pagePath
                            fileRename(tempFile, File(pagePath))

                            // Send a progress event
                            setProgressAsync(
                                Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                    .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                    .build()
                            )
                        }
                        cpt++
                    }
                }*/
            }
        } catch (e: Exception) {
            Timber.w("unarchivePages :: IOException $e")
            throw e
        }

        Timber.v("unarchivePages $fileUri")
        return true
    }


    /**
     * Extract pages from a zip file - but will try if it's a 7zip in case of error...
     */
    private fun unzipPages(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        var exceptionToRemember : Exception? = null
        var result = false
        try {
            result = unzipPageInTrueZipFile(fileUri, filePath, pages)
        } catch (noImageException:NoImageException) {
            Timber.w("unzipPages (zip try):: no image=$noImageException")
            throw noImageException
        } catch (zipE:Exception) {
            Timber.w("unzipPages (zip try):: exception=$zipE")
            exceptionToRemember = zipE
        }

        if (!result) {
            // Try if it's a 7zip file (just in case...)
            try {
                Timber.v("unzipPages: trying 7z format")
                result =  unzipPageIn7ZipFile(fileUri, filePath, pages)
                Timber.v("unzipPages: trying 7z format => success")
            } catch (noImageException:NoImageException) {
                Timber.w("unzipPages (7z try):: no image=$noImageException")
                throw noImageException
            } catch (sevenZipE:Exception) {
                Timber.i("unzipPages (7z try):: exception=$sevenZipE")
            }
        }
        if (result)
            return true

        if (exceptionToRemember != null)
            throw exceptionToRemember

        return false
    }

    /**
     * Extract pages from a true zip file
     */
    private fun unzipPageInTrueZipFile(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unzipPageInTrueZipFile $filePath pages=$pages")

        // Unzip
        try {
            // Get a ZipFile object to unzip
            var zipFile: org.apache.commons.compress.archivers.zip.ZipFile? = null
            val tmpFile = getTempFile(App.pageCacheDirectory, fileUri.toString().md5(), false)

            var inputStream: InputStream? = null
            var channel: SeekableInMemoryByteChannel? = null

            // NOTE : There is 2 method to open a ZipFile: a fast one (but can cause OutOfMemory exception) and a slower one (but need to copy the Uri into the drive - not efficient)

            // Test if the tmpFile exists
            if (tmpFile.exists()) {
                // It exists (which mean we already use the slow method)
                Timber.v("unzipTrueZipFile : tmpFile already exists ${tmpFile.path}")
                zipFile = org.apache.commons.compress.archivers.zip.ZipFile(tmpFile)
            } else {
                // Try the fast method
                try {
                    inputStream = applicationContext.contentResolver.openInputStream(fileUri)
                    channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream))
                    zipFile = org.apache.commons.compress.archivers.zip.ZipFile(channel)
                } catch (e:OutOfMemoryError) {
                    // The file is too big
                    Timber.w("unzipTrueZipFile : file too big : $e")
                } catch (e:java.util.zip.ZipException) {
                    Timber.w("unzipTrueZipFile : java.util.zip.ZipException : $e ")
                } catch (e:Exception) {
                    Timber.w("unzipTrueZipFile : fast method aborted : $e")
                }

                Timber.w("unzipTrueZipFile : fast method zipFile = $zipFile")

                // If zipFile is still null, try the slow method...
                if (zipFile == null) {
                    if (copyFileFromUri(App.appContext, fileUri, tmpFile) != null) {
                        Timber.v("unzipTrueZipFile : tmpFile created ${tmpFile.path}")
                        try {
                            zipFile = org.apache.commons.compress.archivers.zip.ZipFile(tmpFile)
                        } catch (e:Exception) {
                            Timber.w("unzipTrueZipFile : slow method aborted : $e")
                        }
                    } else {
                        Timber.v("unzipTrueZipFile : error in readTextFromUri")
                    }
                }
            }

            Timber.v("unzipTrueZipFile : slow  method zipFile = $zipFile")

            if (zipFile != null) {
                // Run through the zipArchiveEntries
                var cpt = 0
                var zipArchiveEntries : MutableList<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> = mutableListOf()
                Timber.v("unzipTrueZipFile  zipFile.entries=${zipFile.entries}")
                for (entry in zipFile.entries) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }
                    if (!entry.isDirectory && ComicLoadingManager.isFilePathAnImage(entry.name)) {
//                              Timber.v("unzipTrueZipFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                        zipArchiveEntries.add(entry)
                    } else {
//                              Timber.v("unzipTrueZipFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                    }
                    cpt++
                }

                // Reorder sevenZEntries by filename
                zipArchiveEntries = ZipUtil.sortZipArchiveEntry(zipArchiveEntries)
                nbPages = zipArchiveEntries.size
                if (nbPages == 0) throw NoImageException("No image")

                // Catch the wished pages
                if (!isStopped && zipArchiveEntries.isNotEmpty()) {
                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }
                        val zipEntry = zipArchiveEntries[numPage]
                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Timber.v("  unZip page=$numPage in $pagePath")
                        File(pagePath).outputStream().use { output ->
                            zipFile.getInputStream(zipEntry).use{ inputStream ->
                                IOUtils.copy(inputStream, output)
                            }
                        }

                        // Send a progress event
                        setProgressAsync(
                            Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                .build()
                        )
                    }
                }
                // Close everything
                zipFile.close()
                channel?.close()
                inputStream?.close()
            } else {
                // Can't open the file, so exit !
                channel?.close()
                inputStream?.close()
                return false
            }
        } catch (e: IOException) {
            Timber.w("unzipPageInTrueZipFile :: IOException $e")
            throw e
        }
        return true
    }

   /**
     * Extract pages from a 7z file
     */
    private fun unzipPageIn7ZipFile(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unzipPageIn7ZipFile fileUri=$fileUri filePath=$filePath pages=$pages")

        // Un7zip
        try {
            // Get a ZipFile object to unzip
            var sevenZFile: SevenZFile? = null
            val tmpFile = getTempFile(App.pageCacheDirectory, fileUri.toString().md5(), false)

            var inputStream:InputStream? = null
            var channel: SeekableInMemoryByteChannel? = null

            // NOTE : There is 2 method to open a 7zip: a fast one (but can cause OutOfMemory exception) and a slower one (but need to copy the Uri into the drive - not efficient)

            // Test if the tmpFile exists
            if (tmpFile.exists()) {
                // It exists (which mean we already use the slow method)
                Timber.v("unzipPageIn7ZipFile : tmpFile already exists ${tmpFile.path}")
                sevenZFile = SevenZFile(tmpFile)
            } else {
                // Try the fast method
                try {
                    inputStream = applicationContext.contentResolver.openInputStream(fileUri)
                    channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream))
                    sevenZFile = SevenZFile(channel)
                } catch (e:OutOfMemoryError) {
                    // The file is too big
                    Timber.w("unzipPageIn7ZipFile : file too big : $e")
                } catch (e:Exception) {
                    Timber.w("unzipPageIn7ZipFile : fast method aborted : $e")
                }

                Timber.v("unzipPageIn7ZipFile : fast method sevenZFile = $sevenZFile")

                // If zipFile is still null, try the slow method...
                if (sevenZFile == null) {
                    if (copyFileFromUri(App.appContext, fileUri, tmpFile) != null) {
                        Timber.v("unzipPageIn7ZipFile : tmpFile created ${tmpFile.path}")
                        try {
                            sevenZFile = SevenZFile(tmpFile)
                        } catch (e:Exception) {
                            Timber.w("unzipPageIn7ZipFile : slow method aborted : $e")
                        }
                    } else {
                        Timber.v("unzipPageIn7ZipFile : error in readTextFromUri")
                    }
                }
            }
            if (sevenZFile != null) {
                // Run through the zipArchiveEntries
                var cpt = 0
                var sevenZEntries: MutableList<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry> =
                    mutableListOf()
                Timber.v("unzipPageIn7ZipFile  sevenZFile.entries=${sevenZFile.entries}")

                for (entry in sevenZFile.entries) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }
                    if (!entry.isDirectory && ComicLoadingManager.isFilePathAnImage(entry.name)) {
                        // Timber.v("unzipSevenZFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                        sevenZEntries.add(entry)
                    } else {
                        // Timber.v("unzipSevenZFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                    }
                    cpt++
                }

                // Reorder sevenZEntries by filename
                sevenZEntries = ZipUtil.sortSevenZArchiveEntry(sevenZEntries)
                nbPages = sevenZEntries.size
                if (nbPages == 0) throw NoImageException("No image")

                // Catch the wished pages
                if (!isStopped && sevenZEntries.isNotEmpty()) {
                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }
                        val sevenZEntry = sevenZEntries[numPage]
                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Timber.v("  un7Zip page=$numPage in $pagePath")
                        File(pagePath).outputStream().use { output ->
                            IOUtils.copy(sevenZFile.getInputStream(sevenZEntry), output)
                        }

                        // Send a progress event
                        setProgressAsync(
                            Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                .putString(KEY_CURRENT_PATH, pagePath)
                                .putInt(KEY_NB_PAGES, nbPages)
                                .build()
                        )
                    }
                }

                // Close everything
                channel?.close()
                inputStream?.close()
                sevenZFile.close()
            } else {
                // Can't open the file, so exit !
                channel?.close()
                inputStream?.close()
                return false
            }
        } catch (e: IOException) {
            Timber.w("unzipPageIn7ZipFile :: IOException $e")
            throw e
        }
        return true
    }

    /**
     * Extract pages from a rar file
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
                        if (!fileHeader.isDirectory && ComicLoadingManager.isFilePathAnImage(fileHeader.fileName)) {
//                            Timber.v("  HEADER $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} ADDED")
                            fileHeaders.add(fileHeader)
                        } else {
//                            Timber.v("  HEADER $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} SKIPPED")
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
                        Timber.v("  Unrar page=$numPage in $pagePath")
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
        } catch (e: UnsupportedRarV5Exception) {
            Timber.w("unrarPages :: UnsupportedRarV5Exception $e ${e.message}")
            throw Exception("RAR5 format not supported")
        } catch (e: OutOfMemoryError) {
            Timber.w("unrarPages :: OutOfMemoryError $e ${e.message}")
            throw e
        } catch (e: RarException) {
            Timber.w("unrarPages :: RarException $e ${e.message}")
            throw e
        } catch (e: IOException) {
            Timber.w("unrarPages :: IOException $e")
            throw e
        }

        Timber.v("unrarPages $fileUri")
        return true
    }

    /**
     * Extract pages from pdf file
     */
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
                        val tempBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

                        val tempCanvas = Canvas(tempBitmap)
                        tempCanvas.drawColor(Color.WHITE)
                        tempCanvas.drawBitmap(tempBitmap, 0f, 0f, null)
                        page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        if (tempBitmap == null || PdfUtil.isBitmapBlankOrWhite(tempBitmap)) {
                            // Not a valid image
                            Timber.w("getPagesInPdfFile : image not valid !")
                            //                        continue
                        }

                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Timber.i("  getPagesInPdfFile page=$numPage in $pagePath")

                        BitmapUtil.saveBitmapInFile(tempBitmap, pagePath)

                        // Send a progress event
                        setProgressAsync(
                            Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                .build()
                        )
                    }
                }
                renderer.close()
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

