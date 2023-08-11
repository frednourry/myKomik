package fr.nourry.mykomik.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import fr.nourry.mykomik.App
import fr.nourry.mykomik.utils.*
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import timber.log.Timber
import java.io.*


class GetCoverWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_URI                  = "archivePath"
        const val KEY_IMAGE_DESTINATION_PATH        = "imageDestinationPath"
        const val KEY_NB_PAGES                      = "nbPages"
        const val KEY_THUMBNAIL_WIDTH               = "thumbnailWidth"
        const val KEY_THUMBNAIL_HEIGHT              = "thumbnailHeight"
        const val KEY_THUMBNAIL_INNER_IMAGE_WIDTH   = "thumbnailInnerImageWidth"
        const val KEY_THUMBNAIL_INNER_IMAGE_HEIGHT  = "thumbnailInnerImageHeight"
        const val KEY_THUMBNAIL_FRAME_SIZE          = "thumbnailFrameSize"
    }

    private var nbPages = 0

    override fun doWork(): Result {
        Timber.d("GetCoverWorker.doWork")

        val archivePath = inputData.getString(KEY_ARCHIVE_URI)
        val imageDestinationPath = inputData.getString(KEY_IMAGE_DESTINATION_PATH)
        val thumbnailWidth = inputData.getInt(KEY_THUMBNAIL_WIDTH, 200)
        val thumbnailHeight = inputData.getInt(KEY_THUMBNAIL_HEIGHT, 220)
        val thumbnailInnerImageWidth = inputData.getInt(KEY_THUMBNAIL_INNER_IMAGE_WIDTH, 100)
        val thumbnailInnerImageHeight = inputData.getInt(KEY_THUMBNAIL_INNER_IMAGE_HEIGHT, 155)
        val thumbnailFrameSize = inputData.getInt(KEY_THUMBNAIL_FRAME_SIZE, 5)

        Timber.i("doWork archivePath=$archivePath")

        if (archivePath != null && imageDestinationPath!= null) {
            val archiveUri = Uri.parse(archivePath)
            val ext = getExtension(archivePath)
            var boolResult = false
            var errorMessage:String

            try {
                when (ext) {
                    "cbz", "zip" -> {
                        boolResult = unzipCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }
                    "cb7", "7z" -> {
                        boolResult = unzipCoverIn7ZipFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }
                    "cbr", "rar" -> {
                        boolResult = unrarCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }
                    "pdf" -> {
                        boolResult = getCoverInPdfFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }
                }
            } catch (e:Exception) {
                boolResult = false
                errorMessage = e.message ?: ""
                Timber.w("GetCoverWorker.doWork :: error -> $errorMessage")
            }

            if (!boolResult)
                return Result.failure()
        }

        val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to imageDestinationPath,
                                            KEY_NB_PAGES to nbPages)

        return Result.success(outputData)
    }

    // FAST METHOD BUT NEED TO COPY THE FILE...
    /**
     * Extract the cover from a ZIP file - but will try if it's a 7zip in case of error...
     */
    private fun unzipCoverInFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unzipCoverInFile fileUri={$fileUri} imagePath=$imagePath")

        val result = unzipCoverInTrueZipFile(fileUri, imagePath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
        return if (result)
            true
        else {
            // Try if it's a 7zip file (just in case...)
            Timber.v("unzipCoverInFile: trying 7z format")
            unzipCoverIn7ZipFile(fileUri, imagePath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
        }
    }

    /**
     * Extract the cover from a true zip file
     */
    private fun unzipCoverInTrueZipFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unzipCoverInTrueZipFile $fileUri into $imagePath")
        var bitmap: Bitmap? = null
//        Profiling.getInstance().start("unZipCoverInTrueZipFile")

        // Unzip
        try {
            // Get a ZipFile object to unzip
            var zipFile: org.apache.commons.compress.archivers.zip.ZipFile? = null
            val tmpFile = getTempFile(App.pageCacheDirectory, fileUri.toString().md5(), false)

            var inputStream:InputStream? = null
            var channel: SeekableInMemoryByteChannel? = null

            // NOTE : There is 2 method to open a ZipFile: a fast one (but can cause OutOfMemory exception) and a slower one (but need to copy the Uri into the drive - not efficient)

            // Test if the tmpFile exists
            if (tmpFile.exists()) {
                // It exists (which mean we already use the slow method)
                Timber.v("unzipCoverInTrueZipFile : tmpFile already exists ${tmpFile.path}")
                zipFile = org.apache.commons.compress.archivers.zip.ZipFile(tmpFile)
            } else {
                // Try the fast method
                try {
                    inputStream = applicationContext.contentResolver.openInputStream(fileUri)
                    channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream))
                    zipFile = org.apache.commons.compress.archivers.zip.ZipFile(channel)
                } catch (e:OutOfMemoryError) {
                    // The file is too big
                    Timber.w("unzipCoverInTrueZipFile : file too big : $e")
                } catch (e:Exception) {
                    Timber.w("unzipCoverInTrueZipFile : fast method aborted : $e")
                }

                Timber.v("unzipTrueZipFile : slow  method zipFile = $zipFile")

                // If zipFile is still null, try the slow method...
                if (zipFile == null) {
                    if (copyFileFromUri(App.appContext, fileUri, tmpFile) != null) {
                        Timber.v("unzipCoverInTrueZipFile : tmpFile created ${tmpFile.path}")
                        try {
                            zipFile = org.apache.commons.compress.archivers.zip.ZipFile(tmpFile)
                        } catch (e:Exception) {
                            Timber.w("unzipCoverInTrueZipFile : slow method aborted : $e")
                        }
                    } else {
                        Timber.v("unzipCoverInTrueZipFile : error in readTextFromUri")
                    }
                }
            }
            if (zipFile != null) {
                // Run through the zipArchiveEntries
                var cpt = 0
                var zipArchiveEntries: MutableList<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> = mutableListOf()

                for (entry in zipFile.entries) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }
                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                        // Timber.v("unZipCoverInTrueZipFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                        zipArchiveEntries.add(entry)
                    } else {
                        // Timber.v("unZipCoverInTrueZipFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                    }
                    cpt++
                }

                // Reorder entries by filename
                zipArchiveEntries = ZipUtil.sortZipArchiveEntry(zipArchiveEntries)
                nbPages = zipArchiveEntries.size

                // Get the cover (the first image)
                if (nbPages > 0) {
                    val entry = zipArchiveEntries[0]
                    zipFile.getInputStream(entry).use { input ->
                        bitmap = BitmapUtil.createFramedBitmap(
                            input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                        if (bitmap != null) {
                            // Reduce the bitmap if needed
                            val bitmapToSave = Bitmap.createScaledBitmap( bitmap!!, bitmap!!.width, bitmap!!.height, false )

                            // Save the bitmap in cache and return
                            BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                        }
                    }
                }

                // Close everything
                zipFile.close()
                channel?.close()
                inputStream?.close()

                Timber.v("unzipCoverInTrueZipFile :: bitmap=$bitmap")
            } else {
                // Can't open the file, so exit !
                channel?.close()
                inputStream?.close()
                return false
            }

        } catch (e: IOException) {
            Timber.v("unzipCoverInTrueZipFile :: IOException $e")
            return false
        } catch (e: Exception) {
            Timber.v("unzipCoverInTrueZipFile :: Exception $e")
            return false
        }
//        Profiling.getInstance().stop()

        return (bitmap!=null)
    }

    /**
     * Extract the cover from a 7zip file
     */
    private fun unzipCoverIn7ZipFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unzipCoverIn7ZipFile $fileUri into $imagePath")
        var bitmap: Bitmap? = null
//        Profiling.getInstance().start("unSevenZipCoverInFile")

        // Unzip
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
                Timber.v("unzipCoverIn7ZipFile : tmpFile already exists ${tmpFile.path}")
                sevenZFile = SevenZFile(tmpFile)
            } else {
                // Try the fast method
                try {
                    inputStream = applicationContext.contentResolver.openInputStream(fileUri)
                    channel = SeekableInMemoryByteChannel(IOUtils.toByteArray(inputStream))
                    sevenZFile = SevenZFile(channel)
                } catch (e:OutOfMemoryError) {
                    // The file is too big
                    Timber.w("unzipCoverIn7ZipFile : file too big : $e")
                } catch (e:Exception) {
                    Timber.w("unzipCoverIn7ZipFile : fast method aborted : $e")
                }

                Timber.v("unzipPageIn7ZipFile : fast method sevenZFile = $sevenZFile")

                // If zipFile is still null, try the slow method...
                if (sevenZFile == null) {
                    if (copyFileFromUri(App.appContext, fileUri, tmpFile) != null) {
                        Timber.v("unzipCoverIn7ZipFile : tmpFile created ${tmpFile.path}")
                        try {
                            sevenZFile = SevenZFile(tmpFile)
                        } catch (e:Exception) {
                            Timber.w("unzipCoverIn7ZipFile : slow method aborted : $e")
                        }
                    } else {
                        Timber.v("unzipCoverIn7ZipFile : error in readTextFromUri")
                    }
                }
            }
            if (sevenZFile != null) {
                // Run through the zipArchiveEntries
                var cpt = 0
                var sevenZEntries: MutableList<SevenZArchiveEntry> = mutableListOf()

                for (entry in sevenZFile.entries) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }
                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                        // Timber.v("unzipCoverIn7ZipFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                        sevenZEntries.add(entry)
                    } else {
                        // Timber.v("unzipCoverIn7ZipFile  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                    }
                    cpt++
                }

                // Reorder entries by filename
                sevenZEntries = ZipUtil.sortSevenZArchiveEntry(sevenZEntries)
                nbPages = sevenZEntries.size

                // Get the cover (the first image)
                if (nbPages > 0) {
                    val entry = sevenZEntries[0]
                    sevenZFile.getInputStream(entry).use { input ->
                        bitmap = BitmapUtil.createFramedBitmap(
                            input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                        if (bitmap != null) {
                            // Reduce the bitmap if needed
                            val bitmapToSave = Bitmap.createScaledBitmap( bitmap!!, bitmap!!.width, bitmap!!.height, false )

                            // Save the bitmap in cache and return
                            BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                        }
                    }
                }

                // Close everything
                channel?.close()
                inputStream?.close()
                sevenZFile.close()

                Timber.v("unzipCoverIn7ZipFile :: bitmap=$bitmap")
            } else {
                // Can't open the file, so exit !
                channel?.close()
                inputStream?.close()
                return false
            }

        } catch (e: IOException) {
            Timber.v("unzipCoverIn7ZipFile :: IOException $e")
            return false
        } catch (e: Exception) {
            Timber.v("unzipCoverIn7ZipFile :: Exception $e")
            return false
        }
//        Profiling.getInstance().stop()

        return (bitmap!=null)
    }


    /**
     * Extract the cover from a RAR file
     */
    private fun unrarCoverInFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unrarCoverInFile $fileUri into $imagePath")
        var bitmap: Bitmap? = null

        // Unrar
        try {
            var fileHeaders : MutableList<FileHeader> = mutableListOf()

            // Unrar
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val rarArchive = Archive(inputStream)

                var cpt = 0
                while (true) {
                    val fileHeader = rarArchive.nextFileHeader() ?: break
                    if (fileHeader.fullUnpackSize > 0) {
                        if (!fileHeader.isDirectory && isFilePathAnImage(fileHeader.fileName)) {
//                            Timber.v("  ENTRY $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} ADDED")
                            fileHeaders.add(fileHeader)
                        } else {
//                            Timber.v("  ENTRY $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} SKIPPED")
                        }
                    }
                    cpt++
                }
                // Reorder fileHeaders by filename
                fileHeaders = RarUtil.sortFileHeaderList(fileHeaders)

                nbPages = fileHeaders.size

                // Get the cover (the first image)
                if (nbPages > 0) {
                    val entry = fileHeaders[0]
                    val input = rarArchive.getInputStream(entry)
                    bitmap = BitmapUtil.createFramedBitmap(input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth,  thumbnailInnerImageHeight, thumbnailFrameSize)
                    if (bitmap != null) {
                        // Reduce the bitmap if needed
                        val bitmapToSave = Bitmap.createScaledBitmap(bitmap!!, bitmap!!.width, bitmap!!.height, false)

                        // Save the bitmap in cache and return
                        BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                    }
                }
                rarArchive.close()
            }
        } catch (e: RarException) {
            Timber.v("unrarCoverInFile :: RarException $e")
            return false
        } catch (e: OutOfMemoryError) {
            Timber.w("unrarCoverInFile :: OutOfMemoryError $e ${e.message}")
            return false
        } catch (e: IOException) {
            Timber.v("unrarCoverInFile :: IOException $e")
            return false
        } catch (e: Exception) {
            Timber.v("unrarCoverInFile :: Exception $e")
            return false
        }

        return (bitmap!=null)
    }

    /**
     * Extract the cover from a PDF file
     */
    private fun getCoverInPdfFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("pdfCoverInFile $fileUri into $imagePath")
        var bitmap: Bitmap? = null

        // Run through the pdf
        try {
            val fileDescriptor: ParcelFileDescriptor? = applicationContext.contentResolver.openFileDescriptor(fileUri, "r")
            if (fileDescriptor != null) {
                val renderer = PdfRenderer(fileDescriptor)
                val pageCount: Int = renderer.pageCount
                for (i in 0 until pageCount) {
                    val page: PdfRenderer.Page = renderer.openPage(i)
                    val tempBitmap =
                        Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

                    val tempCanvas = Canvas(tempBitmap)
                    tempCanvas.drawColor(Color.WHITE)
                    tempCanvas.drawBitmap(tempBitmap, 0f, 0f, null)
                    page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    if (tempBitmap == null || PdfUtil.isBitmapBlankOrWhite(tempBitmap)) {
                        // Not a valid image
                        continue
                    }

                    // Transform tempBitmap in byteArray
                    val stream = ByteArrayOutputStream()
                    tempBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray: ByteArray = stream.toByteArray()
                    tempBitmap.recycle()

                    // Reduce the bitmap if needed
                    bitmap = BitmapUtil.createFramedBitmap(byteArray, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    if (bitmap != null) {
                        val bitmapToSave = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, false)

                        // Save the bitmap in cache and return
                        BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)

                        break  // For(...)
                    }
                }
                renderer.close()
                fileDescriptor.close()
            }

            if (bitmap == null) {
                Timber.w("pdfCoverInFile:: pdf without image $fileUri")
            }

        } catch (e: IOException) {
            Timber.v("pdfCoverInFile :: IOException $e")
            return false
        }

        return (bitmap!=null)
    }

}