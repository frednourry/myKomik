package fr.nourry.mykomik.loader

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import fr.nourry.mykomik.utils.RarUtil
import fr.nourry.mykomik.utils.ZipUtil
import fr.nourry.mykomik.utils.isFilePathAnImage
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
                            Timber.w("  Unzip page=$numPage in $pagePath")

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
                Timber.w("unzipPages :: finally...")
            }
        }
        Timber.v("END unzipPages ${comicFile.name}")
        return true
    }

    private fun unrarPages(comicFile: File, filePath:String, pages:List<Int>):Boolean {
        Timber.v("unrarPages ${comicFile.name} pages=$pages")

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
                        Timber.v("  HEADER $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} ADDED")
                        fileHeaders.add(fileHeader)
                    } else {
                        Timber.v("  HEADER $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} SKIPPED")

                    }
                }
                cpt++
            }

            // Reorder ZipEntry by filename
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

                    // Send a progress event
                    setProgressAsync(Data.Builder().
                        putInt(KEY_CURRENT_INDEX, numPage).
                        putString(KEY_CURRENT_PATH, pagePath).
                        putInt(KEY_NB_PAGES, nbPages).
                        build())
                }
            }
        } catch (e: RarException) {
            Timber.v("unrarPages :: RarException $e")
            return false
        } catch (e: IOException) {
            Timber.v("unrarPages :: IOException $e")
            return false
        }

        Timber.v("unrarPages ${comicFile.name}")
        return true
    }
}

