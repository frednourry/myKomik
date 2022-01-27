package fr.nourry.mynewkomik.loader

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.nourry.mynewkomik.utils.BitmapUtil
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.isFilePathAnImage
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

class UnzipAllComicWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ZIP_PATH                      = "zipPath"
        const val KEY_DESTINATION_DIRECTORY_PATH    = "destinationDirectoryPath"
    }

    override fun doWork(): Result {
        Timber.d("UnzipFirstImageWorker.doWork")

        val zipPath = inputData.getString(KEY_ZIP_PATH)
        val destPath = inputData.getString(KEY_DESTINATION_DIRECTORY_PATH)
        Timber.d("UnzipFirstImageWorker.doWork :: zipPath = $zipPath  destPath = $destPath")

        if (zipPath != null && destPath!= null) {
            var zipFile = File(zipPath)
            unzipInDirectory(zipFile, destPath)
        }

        val outputData = workDataOf(UnzipFirstImageWorker.KEY_IMAGE_DESTINATION_PATH to destPath)

        return Result.success(outputData)
    }

    private fun unzipInDirectory(comicFile: File, dirPath:String):Boolean {

        Timber.v("BEGIN unzipInDirectory ${comicFile.name}")

        // Clear the directory
        val dir = File(dirPath)
        clearFilesInDir(dir)

        // Unzip
        val zipFileName = comicFile.absoluteFile
        ZipFile(zipFileName).use { zip ->
            try {
                val sequence = zip.entries().asSequence()
                Timber.v("NB entry = " + zip.entries().toList().size)
                var bitmap: Bitmap?

                for (entry in sequence) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }

                    Timber.v(entry.name + " " + entry.size)

                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                        val input = zip.getInputStream(entry)
                        bitmap = BitmapUtil.createBitmap(input.readBytes())
                        if (bitmap != null) {
                            var name = entry.name
                            val lastSlash = name.lastIndexOf('/')
                            if (lastSlash > 0)
                                name = name.substring(lastSlash + 1)

                            // Save the bitmap in cache
                            if (isStopped) {    // Check if the work was cancelled
                                break
                            }
                            BitmapUtil.saveBitmapInFile(bitmap, dirPath + name)
                        }
                    }
                }
            }
            catch (t: Throwable) {
                Timber.w("unzipInDirectory:: error "+t.message)
            }
            finally {
                Timber.w("unzipInDirectory :: finally...")
            }
        }
        Timber.v("END unzipInDirectory ${comicFile.name}")
        return true
    }


    override fun onStopped() {
        super.onStopped()
        val zipPath = inputData.getString(KEY_ZIP_PATH)
        Timber.d("onStopped for $zipPath")
    }
}