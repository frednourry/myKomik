package fr.nourry.mynewkomik.loader

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import fr.nourry.mynewkomik.utils.BitmapUtil
import fr.nourry.mynewkomik.utils.clearFilesInDir
import fr.nourry.mynewkomik.utils.isFilePathAnImage
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile


class UncompressAllComicWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_PATH                  = "archivePath"
        const val KEY_DESTINATION_DIRECTORY_PATH    = "destinationDirectoryPath"
        const val KEY_IMAGE_DESTINATION_PATH        = "imageDestinationPath"
        const val KEY_NB_PAGES                      = "nbPages"
    }
    private var nbPages = 0

    override fun doWork(): Result {
        Timber.d("UncompressAllComicWorker.doWork")

        val archivePath = inputData.getString(KEY_ARCHIVE_PATH)
        val destPath = inputData.getString(KEY_DESTINATION_DIRECTORY_PATH)
        Timber.d("UncompressAllComicWorker.doWork :: archivePath = $archivePath  destPath = $destPath")

        if (archivePath != null && destPath!= null) {
            val archiveFile = File(archivePath)
            val ext = archiveFile.extension.lowercase()
            var boolResult = false

            if (ext == "cbz" || ext == "zip") {
                boolResult = unzipInDirectory(archiveFile, destPath)
            } else if (ext == "cbr" || ext == "rar") {
                boolResult = unrarInDirectory(archiveFile, destPath)
            }

            if (!boolResult) {
                val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to destPath,
                    KEY_NB_PAGES to 0)
                return Result.failure(outputData)
            }
        }

        val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to destPath,
                                            KEY_NB_PAGES to nbPages)
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
                val sequenceSize = zip.entries().toList().size
                var cpt = 0
                nbPages = 0

                Timber.v("NB entry = " + zip.entries().toList().size)
                var bitmap: Bitmap?

                for (entry in sequence) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }

                    Timber.v(entry.name + " " + entry.size)

                    // Todo Reorder files...

                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                        val input = zip.getInputStream(entry)
                        bitmap = BitmapUtil.createBitmap(input.readBytes())
                        if (bitmap != null) {

                            // Save the bitmap in cache
                            if (isStopped) {    // Check if the work was cancelled
                                break
                            }

                            val pageName = "page%03d.png".format(cpt)   // pageName = "pagexxx.png"
                            BitmapUtil.saveBitmapInFile(bitmap, dirPath + /*name*/ pageName, true) // Do this in an other thread?
                            bitmap.recycle()
/*                            Thread {
                                BitmapUtil.saveBitmapInFile(bitmap, dirPath + name)
                            }.start()
*/
                        }
                        nbPages++
                    }
                    setProgressAsync(Data.Builder().putInt("currentIndex", cpt).putInt("size", sequenceSize).build())
                    cpt++
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

    private fun unrarInDirectory(comicFile: File, dirPath:String):Boolean {
        Timber.v("BEGIN unrarInDirectory ${comicFile.name}")
        var bitmap: Bitmap?
        var cpt = 0

        // Clear the directory
        val dir = File(dirPath)
        clearFilesInDir(dir)

        // Unrar
        try {
            val rarArchive = Archive(comicFile)
            val nbHeaders = rarArchive.fileHeaders.size

            // TODO Check if not a 5.x RAR ...

            while (true) {
                val fileHeader = rarArchive.nextFileHeader() ?: break

                Timber.v("unrarInDirectory: ${fileHeader.fileName} ${fileHeader.fullUnpackSize}")
                // Check the file
                if (fileHeader.fullUnpackSize > 0) {
                    val fileName = fileHeader.fileName.lowercase()
                    if (isFilePathAnImage(fileName)) {
                        // Extract this file
                        val input = rarArchive.getInputStream(fileHeader)
                        bitmap = BitmapUtil.createBitmap(input.readBytes())
                        if (bitmap != null) {
                            // Save the bitmap in cache
                            if (isStopped) {    // Check if the work was cancelled
                                break
                            }

                            val pageName = "page%03d.png".format(cpt)   // pageName = "pagexxx.png"
                            BitmapUtil.saveBitmapInFile(
                                bitmap,
                                dirPath + /*name*/ pageName,
                                true
                            ) // Do this in an other thread?
                            bitmap.recycle()
                        }
                        nbPages++
                    }
                    setProgressAsync(Data.Builder().putInt("currentIndex", cpt).putInt("size", nbHeaders).build())
                    cpt++
                }
            }
        } catch (e: RarException) {
            Timber.v("unrarInDirectory :: RarException $e")
            return false
        } catch (e: IOException) {
            Timber.v("unrarInDirectory :: IOException $e")
            return false
        }

        Timber.v("END unrarInDirectory ${comicFile.name}")
        return true
    }
}