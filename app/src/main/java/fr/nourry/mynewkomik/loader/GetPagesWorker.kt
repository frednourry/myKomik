package fr.nourry.mynewkomik.loader

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import com.google.common.util.concurrent.ListenableFuture
import fr.nourry.mynewkomik.utils.BitmapUtil
import fr.nourry.mynewkomik.utils.RarUtil
import fr.nourry.mynewkomik.utils.ZipUtil
import fr.nourry.mynewkomik.utils.isFilePathAnImage
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class GetPagesWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val KEY_ARCHIVE_PATH                  = "archivePath"
        const val KEY_PAGES_LIST                    = "pages"
        const val KEY_PAGES_DESTINATION_PATH        = "imageDestinationPath"
        const val KEY_CURRENT_INDEX                 = "currentIndex"
        const val KEY_CURRENT_PATH                  = "currentPath"
        const val KEY_NB_PAGES                      = "nbPages"
    }

    private var nbPages:Int = 0

    override fun doWork(): Result {
        Timber.d("GetPagesWorker.doWork")

        val archivePath = inputData.getString(KEY_ARCHIVE_PATH)
        val destPath = inputData.getString(KEY_PAGES_DESTINATION_PATH)
        val pagesListStr = inputData.getString(KEY_PAGES_LIST)

        Timber.d("    archivePath=$archivePath")
        Timber.d("    destPath=$destPath")
        Timber.d("    pagesListStr=$pagesListStr")
        Timber.d("    nbPages=$nbPages")

        val pagesList = pagesListStr?.split(',')?.map { it.toInt() }
        Timber.d("    pagesList=$pagesList")

        if (pagesList != null && archivePath != null && destPath!= null) {
            val archiveFile = File(archivePath)
            val ext = archiveFile.extension.lowercase()
            var boolResult = false

            if (ext == "cbz" || ext == "zip") {
                boolResult = unzipPages(archiveFile, destPath, pagesList)
            } else if (ext == "cbr" || ext == "rar") {
                boolResult = unrarPages(archiveFile, destPath, pagesList)
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

    private fun unzipPages(comicFile: File, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unzipPages ${comicFile.name} pages=$pages")

        var listenableFuture : ListenableFuture<Void>? = null

        // Unzip
        ZipFile(comicFile.absoluteFile).use { zip ->
            try {
                val sequences = zip.entries().asSequence()
                var zipEntries : MutableList<ZipEntry> = mutableListOf()

                var cpt=0
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

                if (!isStopped && zipEntries.isNotEmpty()) {
                    var bitmap: Bitmap?

                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }

                        val entry = zipEntries[numPage]
                        val input = zip.getInputStream(entry)
                        bitmap = BitmapUtil.createBitmap(input.readBytes())
                        if (bitmap != null) {
                            // Save the bitmap in cache
                            val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))

                            Timber.v("unzipPages num_pages=$numPage pagePath=$pagePath")
                            BitmapUtil.saveBitmapInFile(
                                bitmap,
                                pagePath,
                                true
                            ) // Do this in an other thread?
                            bitmap.recycle()

//                            Timber.w("  Prepare setProgressAsync !!!")
                            listenableFuture = setProgressAsync(Data.Builder().
                                                                    putInt(KEY_CURRENT_INDEX, numPage).
                                                                    putString(KEY_CURRENT_PATH, pagePath).
                                                                    putInt(KEY_NB_PAGES, nbPages).
                                                                    build())
//                            Timber.w("  Prepare setProgressAsync DONE !!!")
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
        }
        Timber.v("END unzipPages ${comicFile.name}")

        // Be sure that the last setProgressAsync was received, if any
        if (listenableFuture != null) {
            while (true) {
                Thread.sleep(50)    // Some sleep to be sure the last RUNNING signal (onProgress) is send before the SUCCESS signal
                Timber.d("NNNNN ${listenableFuture!!.isDone} ${listenableFuture!!.isCancelled}")
                if (listenableFuture!!.isDone || listenableFuture!!.isCancelled) {
                    break
                }
            }
        }
        return true
    }

    private fun unrarPages(comicFile: File, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unrarPages ${comicFile.name} pages=$pages")
        var listenableFuture : ListenableFuture<Void>? = null

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

            if (!isStopped && fileHeaders.isNotEmpty()) {
                var bitmap: Bitmap?

                for (numPage in pages) {
                    if (isStopped) {    // Check if the work was cancelled
                        break
                    }

                    if (numPage >= nbPages) {
                        Timber.w("  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                        continue
                    }
                    val fileHeader = fileHeaders[numPage]
                    // Extract this file
                    val input = rarArchive.getInputStream(fileHeader)
                    bitmap = BitmapUtil.createBitmap(input.readBytes())
                    if (bitmap != null) {
                        // Save the bitmap in cache
                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Timber.v("unrarPages num_pages=$numPage pagePath=$pagePath")
                        BitmapUtil.saveBitmapInFile(bitmap,pagePath, true) // Do this in an other thread?
                        bitmap.recycle()

//                        Timber.w("  Prepare setProgressAsync !!!")
                        listenableFuture = setProgressAsync(Data.Builder().
                                                                putInt(KEY_CURRENT_INDEX, numPage).
                                                                putString(KEY_CURRENT_PATH, pagePath).
                                                                putInt(KEY_NB_PAGES, nbPages).
                                                                build())
//                        Timber.w("  Prepare setProgressAsync DONE !!!")

                    }
                }
            }
        } catch (e: RarException) {
            Timber.v("unrarPages :: RarException $e")
            return false
        } catch (e: IOException) {
            Timber.v("unrarPages :: IOException $e")
            return false
        }

        // Be sure that the last setProgressAsync was received, if any
        if (listenableFuture != null) {
            while (true) {
                Thread.sleep(50)    // Some sleep to be sure the last RUNNING signal (onProgress) is send before the SUCCESS signal
                Timber.d("NNNNN ${listenableFuture.isDone} ${listenableFuture.isCancelled}")
                if (listenableFuture.isDone || listenableFuture.isCancelled) {
                    break
                }
            }
        }

        Timber.v("unrarPages ${comicFile.name}")
        return true
    }
}

