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
import timber.log.Timber
import java.io.*
import java.util.zip.ZipEntry
import fr.nourry.mykomik.utils.getTempFile
import fr.nourry.mykomik.utils.readTextFromUri
import java.util.zip.ZipFile


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

        const val BUFFER_SIZE = 2048
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

            when (ext) {
                "cbz", "zip" -> {
                    boolResult = unzipCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                }
                "cbr", "rar" -> {
                    boolResult = unrarCoverInFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                }
                "pdf" -> {
                    boolResult = getCoverInPdfFile(archiveUri, imageDestinationPath, thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                }
            }

            if (!boolResult)
                return Result.failure()
        }

        val outputData = workDataOf(KEY_IMAGE_DESTINATION_PATH to imageDestinationPath,
                                            KEY_NB_PAGES to nbPages)

        return Result.success(outputData)
    }

    // SLOW METHOD DUE TO TWO PASS
/*    private fun unzipCoverInFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int,thumbnailFrameSize: Int): Boolean {
        Timber.v("unzipCoverInFile fileUri={$fileUri} imagePath=$imagePath")

//        Profiling.getInstance().start("unzipCoverInFile")
        var bitmap: Bitmap? = null

        try {
            var zipEntries : MutableList<ZipEntry> = arrayListOf()

            // 1st pass : Get and order the zip entries
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.use {
                    val bufferedInputStream = BufferedInputStream(inputStream)
                    val zipInputStream = ZipInputStream(bufferedInputStream)

                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
//                            Timber.i("  entry = ${entry.name} - ${entry.size}")
//                        zipInputStream.closeEntry()   // No need ? and time consumer

                        if (!entry.isDirectory && isFilePathAnImage2(entry.name)) {
//                                Timber.v("  ENTRY :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                            zipEntries.add(entry)
                        }
                        else {
                            Timber.v("  ENTRY :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                        }

                        entry = zipInputStream.nextEntry
                    }
                    bufferedInputStream.close()
                }
            }
//            Profiling.getInstance().intermediate("getEntries")

            zipEntries = ZipUtil.sortZipEntryList(zipEntries)
            val nbPages = zipEntries.size
//            Profiling.getInstance().intermediate("sortEntries")
            Timber.i("  nbPages = $nbPages")

            // Get the cover (the first image)
            if (nbPages>0) {
                val firstEntry = zipEntries[0]

                // 2nd pass : Unzip the first page (need to reopen the stream...)
                applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    inputStream.use {
                        val bufferedInputStream = BufferedInputStream(inputStream)
                        val zipInputStream = ZipInputStream(bufferedInputStream)

                        var entry: ZipEntry? = zipInputStream.nextEntry
                        while (entry != null) {
                            if (firstEntry.name == entry!!.name) {
                                // Read the inputStream into an outputStream

                                val outputStream = ByteArrayOutputStream()
                                try {
                                    val data = ByteArray(BUFFER_SIZE)

                                    var cpt: Int = zipInputStream.read(data, 0, BUFFER_SIZE)
                                    while (cpt != -1) {
                                        outputStream.write(data, 0, cpt)
                                        cpt = zipInputStream.read(data, 0, BUFFER_SIZE)
                                    }
                                    outputStream.flush()
                                } catch (e:Exception) {
                                    Timber.w("error while reading zip ! ${outputStream.size()}")
                                }
                                zipInputStream.closeEntry()

                                // Create an image with this stream
                                bitmap = BitmapUtil.createFramedBitmap(outputStream.toByteArray(), thumbnailWidth, thumbnailHeight, thumbnailInnerImageWidth, thumbnailInnerImageHeight, thumbnailFrameSize)
                                if (bitmap != null) {

                                    Timber.w("bitmap null !! outputStream=${outputStream.size()}")
                                    // Reduce the bitmap if needed
                                    val bitmapToSave = Bitmap.createScaledBitmap(bitmap!!, bitmap!!.width, bitmap!!.height,false)

                                    // Save the bitmap in cache and return
                                    BitmapUtil.saveBitmapInFile(bitmapToSave, imagePath)
                                }
                                break
                            }

                            entry = zipInputStream.nextEntry
                        }
                        bufferedInputStream.close()
                    }
                }
            }
        } catch (ioError:IOException) {
            Timber.w( ioError.stackTraceToString())

        } catch (error:Exception) {
            Timber.w( error.stackTraceToString())
        }
//        Profiling.getInstance().stop()
        return (bitmap!=null)
    }
*/

    // FAST METHOD BUT NEED TO COPY THE FILE...
    private fun unzipCoverInFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unzipCoverInFile fileUri={$fileUri} imagePath=$imagePath")
        var bitmap: Bitmap? = null

//        Profiling.getInstance().start("unzipCoverInFile")

        // Copy the uri file in a temp File
        val tmpFile = getTempFile(App.pageCacheDirectory, fileUri.toString().md5(), true)
        Timber.v("unzipCoverInFile tmpFile=${tmpFile.absoluteFile}")
        if (readTextFromUri(App.appContext, fileUri, tmpFile) == null) {
            Timber.v("unzipCoverInFile error in readTextFromUri")
            return false
        }

        ZipFile(tmpFile.absoluteFile).use { zip ->
            val sequences = zip.entries().asSequence()
            var zipEntries: MutableList<ZipEntry> = mutableListOf()

            var cpt = 0
            for (entry in sequences) {
                if (isStopped) {    // Check if the work was cancelled
                    break
                }

                if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
//                    Timber.v("  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} ADDED")
                    zipEntries.add(entry)
                } else {
                    Timber.v("  ENTRY $cpt :: name=${entry.name} isDirectory=${entry.isDirectory} SKIPPED")
                }
                cpt++
            }
//            Profiling.getInstance().intermediate("getEntries")

            // Reorder ZipEntry by filename
            zipEntries = ZipUtil.sortZipEntryList(zipEntries)
            nbPages = zipEntries.size
//            Profiling.getInstance().intermediate("sortEntries")

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

        // Delete tmpFile
        deleteFile(tmpFile)

//        Profiling.getInstance().stop()

        return (bitmap!=null)
    }

    private fun unrarCoverInFile(fileUri: Uri, imagePath: String, thumbnailWidth: Int, thumbnailHeight: Int, thumbnailInnerImageWidth: Int, thumbnailInnerImageHeight: Int, thumbnailFrameSize: Int): Boolean {
        Timber.v("unrarCoverInFile")
        var bitmap: Bitmap? = null

        // Unrar
        try {
            var fileHeaders : MutableList<FileHeader> = mutableListOf()

            // Unrar
            applicationContext.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val rarArchive = Archive(inputStream)

                // Check if not a 5.x RAR ...?

                var cpt = 0
                while (true) {
                    val fileHeader = rarArchive.nextFileHeader() ?: break
                    if (fileHeader.fullUnpackSize > 0) {
                        if (!fileHeader.isDirectory && isFilePathAnImage(fileHeader.fileName)) {
//                            Timber.v("  ENTRY $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} ADDED")
                            fileHeaders.add(fileHeader)
                        } else {
                            Timber.v("  ENTRY $cpt :: name=${fileHeader.fileName} isDirectory=${fileHeader.isDirectory} SKIPPED")

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
        } catch (e: IOException) {
            Timber.v("unrarCoverInFile :: IOException $e")
            return false
        }

        return (bitmap!=null)
    }

    private fun getCoverInPdfFile(
        fileUri: Uri,
        imagePath: String,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailInnerImageWidth: Int,
        thumbnailInnerImageHeight: Int,
        thumbnailFrameSize: Int,
    ): Boolean {
        Timber.v("pdfCoverInFile")
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