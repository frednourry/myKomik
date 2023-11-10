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
import io.github.frednourry.FnyLib7z
import fr.nourry.mykomik.utils.*
import fr.nourry.mykomik.utils.BitmapUtil.Companion.decodeStream
import timber.log.Timber
import java.io.*


class GetCoverWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_URI                   = "archivePath"
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
/*                    "cbz", "zip" -> {
                        boolResult = unzipCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }
                    "cb7", "7z" -> {
                        boolResult = unzipCoverIn7ZipFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }
                    "cbr", "rar" -> {
                        boolResult = unrarCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                    }*/
                    "cbz", "zip","cb7", "7z", "cbr", "rar" -> {
                        boolResult = unarchiveCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
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

    /**
     * Extract the cover from an archive file using FnyLib7z
     */
    private fun unarchiveCoverInFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unarchiveCoverInFile fileUri={$fileUri} imagePath=$imagePath")
        var bitmap: Bitmap? = null

        try {
            val tempCoverDirectoryPath = ComicLoadingManager.getInstance().getTempCoverDirectoryPath()
            val tempCoverDirectory = File(tempCoverDirectoryPath)
            clearFilesInDir(tempCoverDirectory)

            // Extract the first file that is an image
            val result = FnyLib7z.getInstance().uncompress(fileUri, tempCoverDirectory, numListToExtract=listOf(0), filtersList=ComicLoadingManager.imageExtensionFilterList, sortList = true)
            if (result == FnyLib7z.RESULT_OK) {
                var arrFiles = getFilesInDirectory(tempCoverDirectory)
                if (arrFiles.size>0) {
                    val tempFile = arrFiles[0]
                    val tempBitmap = decodeStream(tempFile)
                    Timber.v("    unarchiveCoverInFile tempFile=${tempFile.absolutePath}")

                    if (tempBitmap != null) {
                        bitmap = BitmapUtil.createFramedBitmap(tempBitmap, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth,  thumbnailInnerImageHeight, thumbnailFrameSize)
                        if (bitmap != null) {
                            // Reduce the bitmap if needed
                            val bitmapToSave = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, false)

                            // Save the bitmap in cache and return
                            BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                        }
                    } else {
                        Timber.i("unarchiveCoverInFile :: no width found in the first image!")
                        return false
                    }
                } else {
                    Timber.i("unarchiveCoverInFile :: no readable image found in $fileUri")
                    return false
                }

            }
        } catch (e: Exception) {
            Timber.v("unarchiveCoverInFile :: Exception $e")
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