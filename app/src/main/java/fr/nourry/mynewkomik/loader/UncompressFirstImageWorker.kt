package fr.nourry.mynewkomik.loader

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import fr.nourry.mynewkomik.utils.BitmapUtil
import fr.nourry.mynewkomik.utils.isFilePathAnImage
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile


class UncompressFirstImageWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
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
        Timber.d("UncompressFirstImageWorker.doWork")

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

            if (ext == "cbz" || ext == "zip") {
                boolResult = unzipFirstImageInFileAndImageView(archiveFile, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
            } else if (ext == "cbr" || ext == "rar") {
                boolResult = unrarFirstImageInFileAndImageView(archiveFile, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
            }

            if (!boolResult)
                return Result.failure()
        }

        val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to imageDestinationPath,
                                            KEY_NB_PAGES to nbPages)

        return Result.success(outputData)
    }

    private fun unzipFirstImageInFileAndImageView(
        comicFile: File,
        imagePath: String,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailInnerImageWidth: Int,
        thumbnailInnerImageHeight: Int,
        thumbnailFrameSize: Int
    ): Boolean {
        ZipFile(comicFile).use { zip->
            val sequence = zip.entries().asSequence()
            Timber.v("NB entry = "+zip.entries().toList().size)
            var bitmap: Bitmap?
            var bitmapSaved = false

            nbPages = 0

            for (entry in sequence) {
                Timber.v(entry.name + " " + entry.size)
                if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                    if (!bitmapSaved) {
                        // If not bitmap generated
                        val input = zip.getInputStream(entry)
                        bitmap = BitmapUtil.createFramedBitmap(input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                        if (bitmap != null) {
                            // Reduce the bitmap if needed
                            val bitmapToSave = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height,false)

                            // Save the bitmap in cache and return
                            bitmapSaved = BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                        }
                    }
                    // Count nb images
                    nbPages++
                }
            }
            return bitmapSaved
        }
    }

    private fun unrarFirstImageInFileAndImageView(
        comicFile: File,
        imagePath: String,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailInnerImageWidth: Int,
        thumbnailInnerImageHeight: Int,
        thumbnailFrameSize: Int
    ): Boolean {
        var bitmap: Bitmap?
        var bitmapSaved = false

        try {

            val rarArchive = Archive(comicFile)

            // TODO Check if not a 5.x RAR ...

            while (!bitmapSaved) {
                val fileHeader = rarArchive.nextFileHeader() ?: break

                Timber.d("unrarFirstImageInFileAndImageView: ${fileHeader.fileName} ${fileHeader.fullUnpackSize}")
                // Check the file
                if (fileHeader.fullUnpackSize > 0) {
                    val fileName = fileHeader.fileName.lowercase()
                    if (isFilePathAnImage(fileName)) {
                        // Extract this file
                        val input = rarArchive.getInputStream(fileHeader)
                        bitmap = BitmapUtil.createFramedBitmap(input.readBytes(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                        if (bitmap != null) {
                            // Reduce the bitmap if needed
                            val bitmapToSave = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height,false)

                            // Save the bitmap in cache and return
                            bitmapSaved = BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                        }
                    }
                }
            }
        } catch (e: RarException) {
            Timber.v("unrarInDirectory :: RarException $e")
            return false
        } catch (e: IOException) {
            Timber.v("unrarInDirectory :: IOException $e")
            return false
        }

        return bitmapSaved
    }

}