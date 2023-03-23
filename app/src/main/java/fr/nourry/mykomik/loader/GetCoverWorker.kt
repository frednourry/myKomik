package fr.nourry.mykomik.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import fr.nourry.mykomik.utils.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


class GetCoverWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_PATH                  = "archivePath"
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

        val archivePath = inputData.getString(KEY_ARCHIVE_PATH)
        val imageDestinationPath = inputData.getString(KEY_IMAGE_DESTINATION_PATH)
        val thumbnailWidth = inputData.getInt(KEY_THUMBNAIL_WIDTH, 200)
        val thumbnailHeight = inputData.getInt(KEY_THUMBNAIL_HEIGHT, 220)
        val thumbnailInnerImageWidth = inputData.getInt(KEY_THUMBNAIL_INNER_IMAGE_WIDTH, 100)
        val thumbnailInnerImageHeight = inputData.getInt(KEY_THUMBNAIL_INNER_IMAGE_HEIGHT, 155)
        val thumbnailFrameSize = inputData.getInt(KEY_THUMBNAIL_FRAME_SIZE, 5)

        if (archivePath != null && imageDestinationPath!= null) {
            val archiveFile = File(archivePath)
            val ext = archiveFile.extension.lowercase()
            var boolResult = false

            when (ext) {
                "cbz", "zip" -> {
                    boolResult = unzipCoverInFile(archiveFile, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                }
                "cbr", "rar" -> {
                    boolResult = unrarCoverInFile(archiveFile, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                }
                "pdf" -> {
                    boolResult = getCoverInPdfFile(archiveFile, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                }
            }

            if (!boolResult)
                return Result.failure()
        }

        val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to imageDestinationPath,
                                            KEY_NB_PAGES to nbPages)

        return Result.success(outputData)
    }

    private fun unzipCoverInFile(
        comicFile: File,
        imagePath: String,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailInnerImageWidth: Int,
        thumbnailInnerImageHeight: Int,
        thumbnailFrameSize: Int
    ): Boolean {
        Timber.v("unzipCoverInFile")

        var bitmap: Bitmap? = null

        ZipFile(comicFile.absoluteFile).use { zip ->
            val sequences = zip.entries().asSequence()
            var zipEntries: MutableList<ZipEntry> = mutableListOf()

            var cpt = 0
            for (entry in sequences) {
                if (isStopped) {    // Check if the work was cancelled
                    break
                }

                if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                    Timber.v("  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                    zipEntries.add(entry)
                } else {
                    Timber.v("  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                }
                cpt++
            }

            // Reorder ZipEntry by filename
            zipEntries = ZipUtil.sortZipEntryList(zipEntries)
            nbPages = zipEntries.size

            // Get the cover (the first image)
            if (nbPages>0) {
                val entry = zipEntries[0]
                val input = zip.getInputStream(entry)
                bitmap = BitmapUtil.createFramedBitmap(input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                if (bitmap != null) {
                    // Reduce the bitmap if needed
                    val bitmapToSave = Bitmap.createScaledBitmap(bitmap!!, bitmap!!.width, bitmap!!.height,false)

                    // Save the bitmap in cache and return
                    BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                }
            }
        }
        return (bitmap!=null)
    }

    private fun unrarCoverInFile(
        comicFile: File,
        imagePath: String,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailInnerImageWidth: Int,
        thumbnailInnerImageHeight: Int,
        thumbnailFrameSize: Int
    ): Boolean {
        Timber.v("unrarCoverInFile")
        var bitmap: Bitmap? = null

        // Unrar
        try {
            val rarArchive = Archive(comicFile)

            // Check if not a 5.x RAR ...?

            var fileHeaders : MutableList<FileHeader> = mutableListOf()
            var cpt = 0
            while (true) {
                val fileHeader = rarArchive.nextFileHeader() ?: break
                if (fileHeader.fullUnpackSize > 0) {
                    if (!fileHeader.isDirectory && isFilePathAnImage(fileHeader.fileName)) {
                        Timber.v("  ENTRY $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} ADDED")
                        fileHeaders.add(fileHeader)
                    } else {
                        Timber.v("  ENTRY $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} SKIPPED")

                    }
                }
                cpt++
            }

            // Reorder ZipEntry by filename
            fileHeaders = RarUtil.sortFileHeaderList(fileHeaders)
            nbPages = fileHeaders.size

            // Get the cover (the first image)
            if (nbPages>0) {
                val entry = fileHeaders[0]
                val input = rarArchive.getInputStream(entry)
                bitmap = BitmapUtil.createFramedBitmap(input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                if (bitmap != null) {
                    // Reduce the bitmap if needed
                    val bitmapToSave = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height,false)

                    // Save the bitmap in cache and return
                    BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                }
            }
        } catch (e: RarException) {
            Timber.v("unrarCoverInFile :: RarException $e")
            return false
        } catch (e: IOException) {
            Timber.v("unrarCoverInFile :: IOException $e")
            return false
        }

        return (bitmap!=null)
    }

    private fun getCoverInPdfFile(
        comicFile: File,
        imagePath: String,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailInnerImageWidth: Int,
        thumbnailInnerImageHeight: Int,
        thumbnailFrameSize: Int
    ): Boolean {
        Timber.v("pdfCoverInFile")
        var bitmap: Bitmap? = null

        // Unrar
        try {
            val fileDescriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(comicFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            val pageCount: Int = renderer.pageCount
            for (i in 0 until pageCount) {
                val page: PdfRenderer.Page = renderer.openPage(i)
                val tempBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

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
                    val bitmapToSave =
                        Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, false)

                    // Save the bitmap in cache and return
                    BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)

                    break;  // For(...)
                }

            }
            fileDescriptor.close()

            if (bitmap == null) {
                Timber.w("pdfCoverInFile:: pdf without image ${comicFile.path}")
            }

        } catch (e: IOException) {
            Timber.v("pdfCoverInFile :: IOException $e")
            return false
        }

        return (bitmap!=null)
    }

}