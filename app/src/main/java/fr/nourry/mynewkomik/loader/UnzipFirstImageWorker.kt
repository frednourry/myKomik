package fr.nourry.mynewkomik.loader

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.nourry.mynewkomik.utils.BitmapUtil
import fr.nourry.mynewkomik.utils.isFilePathAnImage
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

class UnzipFirstImageWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ZIP_PATH                  = "zipPath"
        const val KEY_IMAGE_DESTINATION_PATH    = "imageDestinationPath"

    }

    override fun doWork(): Result {
        Timber.d("UnzipFirstImageWorker.doWork")

        val zipPath = inputData.getString(KEY_ZIP_PATH)
        val imageDestinationPath = inputData.getString(KEY_IMAGE_DESTINATION_PATH)

        if (zipPath != null && imageDestinationPath!= null) {
            val zipFile = File(zipPath)
            unzipFirstImageInFileAndImageView(zipFile, imageDestinationPath/*, imageView*/)
        }

        val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to imageDestinationPath)

        return Result.success(outputData)
    }

    private fun unzipFirstImageInFileAndImageView(comicFile: File, path:String/*, imageView: ImageView*/): Boolean {
        ZipFile(comicFile).use { zip->
            val sequence = zip.entries().asSequence()
            Timber.v("NB entry = "+zip.entries().toList().size)
            var bitmap: Bitmap?

            for (entry in sequence) {
                Timber.v(entry.name + " " + entry.size)
                if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                    val input = zip.getInputStream(entry)
                    bitmap = BitmapUtil.createBitmap(input.readBytes(), 300,300)
                    if (bitmap != null) {
                        // Reduce the bitmap if needed
                        val bitmapToSave = Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, false)

                        // Save the bitmap in cache and return
                        return BitmapUtil.saveBitmapInFile(bitmapToSave, path)
                    }
                }
            }
        }
        return false
    }

}