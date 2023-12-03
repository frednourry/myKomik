package fr.nourry.mykomik.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.frednourry.FnyLib7z
import fr.nourry.mykomik.App
import fr.nourry.mykomik.utils.*
import io.github.frednourry.itemListFnyLib7z
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream

class NoImageException(message:String): Exception(message)

class GetPagesWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val TAG = "GetPagesWorker"

        const val KEY_ARCHIVE_URI                   = "archiveUri"
        const val KEY_PAGES_LIST                    = "pages"
        const val KEY_PAGES_DESTINATION_PATH        = "imageDestinationPath"
        const val KEY_PAGES_CONTENT_LIST_PATH       = "contentListFilePath"
        const val KEY_CURRENT_INDEX                 = "currentIndex"
        const val KEY_CURRENT_PATH                  = "currentPath"
        const val KEY_NB_PAGES                      = "nbPages"
        const val KEY_COMIC_EXTENSION               = "extension"
        const val KEY_ERROR_MESSAGE                 = "errorMessage"

        // Same variables used to cache some datas
        var currentContentListFilePath = ""
        var currentContentList:List<itemListFnyLib7z> = listOf()
    }

    private var nbPages:Int = 0

    override fun doWork(): Result {
        Log.d(TAG,"GetPagesWorker.doWork")

        val archiveUriPath = inputData.getString(KEY_ARCHIVE_URI)
        val destPath = inputData.getString(KEY_PAGES_DESTINATION_PATH)
        val pagesListStr = inputData.getString(KEY_PAGES_LIST)
        val contentListFilePath = inputData.getString(KEY_PAGES_CONTENT_LIST_PATH)
        val extension = inputData.getString(KEY_COMIC_EXTENSION)?.lowercase() ?: ""

        Log.v(TAG,"    archivePath=$archiveUriPath")
        Log.v(TAG,"    extension=$extension")
        Log.v(TAG,"    destPath=$destPath")
        Log.v(TAG,"    pagesListStr=$pagesListStr")
        Log.v(TAG,"    nbPages=$nbPages")
        Log.v(TAG,"    contentListFilePath=$contentListFilePath")

        val pagesList = pagesListStr?.split(',')?.map { it.toInt() }
        Log.d(TAG,"    pagesList=$pagesList")

        if (pagesList != null && archiveUriPath != null && destPath!= null) {
            val archiveUri = Uri.parse(archiveUriPath)
            Log.d(TAG,"    archiveUri=$archiveUri")

            var boolResult = false
            var errorMessage = ""
            try {
/*                if (ext == "cbz" || ext == "zip") {
                    boolResult = unzipPages(archiveUri, destPath, pagesList)
                } else if (ext == "cb7" || ext == "7z") {
                    boolResult = unzipPageIn7ZipFile(archiveUri, destPath, pagesList)
                } else if (ext == "cbr" || ext == "rar") {
                    boolResult = unrarPages(archiveUri, destPath, pagesList)
*/
                if (extension == "cbz" || extension == "zip" || extension == "cb7" || extension == "7z" || extension == "cbr" || extension == "rar") {
                    boolResult = unarchivePages(archiveUri, destPath, pagesList, contentListFilePath!!)
                } else if (extension == "pdf") {
                    boolResult = getPagesInPdfFile(archiveUri, destPath, pagesList)
                }
            } catch (e:Exception) {
                boolResult = false
                errorMessage = e.message ?: ""
                Log.e(TAG,errorMessage)
                e.printStackTrace()
            }

            if (!boolResult) {
                val outputData = workDataOf(KEY_PAGES_DESTINATION_PATH to destPath,
                    KEY_PAGES_LIST to pagesListStr,
                    KEY_NB_PAGES to nbPages,
                    KEY_ERROR_MESSAGE to errorMessage)
                return Result.failure(outputData)
            }
        }

        val outputData = workDataOf(KEY_PAGES_DESTINATION_PATH to destPath,
            KEY_PAGES_LIST to pagesListStr,
            KEY_NB_PAGES to nbPages)
        return Result.success(outputData)
    }

    /**
     * Extract pages from an archive using FnyLib7z
     */
    private fun unarchivePages(fileUri: Uri, filePath:String, indexPages:List<Int>, contentListFilePath:String):Boolean {
        Log.v(TAG,"unarchivePages $fileUri filePath=$filePath pages=$indexPages")

        try {
            // Init temp directory
            val tempPagesDirectoryPath = ComicLoadingManager.getInstance().getTempPagesDirectoryPath()
            val tempPagesDirectory = File(tempPagesDirectoryPath)

            // Get the content list of the archive (in a file)
            val listFile = File(contentListFilePath)

            if (contentListFilePath != currentContentListFilePath) {
                currentContentListFilePath = contentListFilePath
                currentContentList = emptyList()
            }

            if (listFile.exists()) {
                // No need to regenerate it, keep the same file
            } else {
                // Or else, ask FnyLib7z to retrieve it
                val result0 = FnyLib7z.getInstance().listFiles(fileUri, sortList = true, filtersList=ComicLoadingManager.imageExtensionFilterList, stdOutputPath = contentListFilePath)
                if (result0 != FnyLib7z.RESULT_OK) {
                    Log.w(TAG,"  unarchivePages :: can't retrieve the content of $fileUri in $contentListFilePath")
                    return false
                }
            }

            if (!listFile.exists()) {
                Log.w(TAG,"  unarchivePages :: content list file was not created : $contentListFilePath")
                return false
            }

            // Parse this content file, if necessary
            if (currentContentList.isEmpty()) {
                Log.v(TAG,"  unarchivePages:: parsing $contentListFilePath ...")
                currentContentList = FnyLib7z.getInstance().parseListFile(listFile)
            } else {
                Log.v(TAG,"  unarchivePages:: no need to parse $contentListFilePath ...")
            }
            nbPages = currentContentList.size
            Log.v(TAG,"  unarchivePages :: nbPages=$nbPages")

            // Built the list of files to unarchive
/*            val pathsToUnarchive = mutableListOf<String>()
            for (indexPage in indexPages) {
                if (indexPage < nbPages)
                    pathsToUnarchive.add(currentContentList[indexPage].name)
                else
                    Log.v(TAG,"  unarchivePages :: bad page index $indexPage (archive only contains $nbPages file(s))")
            }
*/
            // Extract the pages
            val result = FnyLib7z.getInstance().uncompress(fileUri, dirToExtract=tempPagesDirectory, sortList = true, filtersList=ComicLoadingManager.imageExtensionFilterList, numListToExtract=indexPages)
//            val result = FnyLib7z.getInstance().uncompress(fileUri, dirToExtract=tempPagesDirectory, filtersList=pathsToUnarchive) // Problem if the file contains some specials characters
            if (result == FnyLib7z.RESULT_OK) {
                for (indexPage in indexPages) {
                    if (indexPage < nbPages) {
                        // Get the file path extracted
                        val path = currentContentList[indexPage].name
                        val simplePath = path.substring(path.lastIndexOf(File.separator) + 1)
                        val pagePath = tempPagesDirectoryPath + File.separator + simplePath
                        //Log.v(TAG,"  unarchivePages :: test page file in $pagePath")

                        // Test if the file exists in the cache
                        val pageFile = File(pagePath)
                        if (pageFile.exists()) {
                            val newPagePath = filePath.replace(".999.", ".%03d.".format(indexPage))
                            // Move tempFile into pagePath
                            fileRename(pageFile, File(newPagePath))

                            // Send a progress event
                            setProgressAsync(
                                Data.Builder().putInt(KEY_CURRENT_INDEX, indexPage)
                                    .putString(KEY_CURRENT_PATH, newPagePath)
                                    .putInt(KEY_NB_PAGES, nbPages)
                                    .build()
                            )

                        } else {
                            Log.v(TAG,"  unarchivePages :: no file extracted for index=$indexPage")
                        }
                    }
                }


/*                var arrFiles = getFilesInDirectory(tempPagesDirectory)
                Log.v(TAG,"    unarchivePages arrFiles=${arrFiles.toString()}")

                if (arrFiles.size>0) {
                    if (arrFiles.size != indexPages.size) {
                        Log.w(TAG,"    unarchivePages: not all pages have an image !")
                        // TODO ask FnyLib7z to automatically rename the files ?
                    }

                    var cpt = 0
                    for (numPage in indexPages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))

                        if (cpt< arrFiles.size) {
                            val tempFile = arrFiles[cpt]

                            // Move tempFile into pagePath
                            fileRename(tempFile, File(pagePath))

                            // Send a progress event
                            setProgressAsync(
                                Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                    .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                    .build()
                            )
                        }
                        cpt++
                    }
                }*/
            }
        } catch (e: Exception) {
            Log.w(TAG,"unarchivePages :: IOException $e")
            throw e
        }

        Log.v(TAG,"unarchivePages $fileUri")
        return true
    }

    /**
     * Extract pages from pdf file
     */
    private fun getPagesInPdfFile(fileUri: Uri, filePath:String, pages:List<Int>):Boolean {
        Log.v(TAG,"getPagesInPdfFile $fileUri pages=$pages")

        try {
            val fileDescriptor: ParcelFileDescriptor? = applicationContext.contentResolver.openFileDescriptor(fileUri, "r")
            if (fileDescriptor != null) {
                val renderer = PdfRenderer(fileDescriptor)
                val pageCount: Int = renderer.pageCount

                nbPages = pageCount

                if (!isStopped && pageCount > 0) {
                    for (numPage in pages) {
                        if (isStopped) {    // Check if the work was cancelled
                            break
                        }

                        if (numPage >= nbPages) {
                            Log.w(TAG,"  numPage is bigger than nbPages ! ($numPage>=$nbPages)")
                            continue
                        }

                        val page: PdfRenderer.Page = renderer.openPage(numPage)
                        val tempBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

                        val tempCanvas = Canvas(tempBitmap)
                        tempCanvas.drawColor(Color.WHITE)
                        tempCanvas.drawBitmap(tempBitmap, 0f, 0f, null)
                        page.render(tempBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        if (tempBitmap == null || PdfUtil.isBitmapBlankOrWhite(tempBitmap)) {
                            // Not a valid image
                            Log.w(TAG,"getPagesInPdfFile : image not valid !")
                            //                        continue
                        }

                        val pagePath = filePath.replace(".999.", ".%03d.".format(numPage))
                        Log.i(TAG,"  getPagesInPdfFile page=$numPage in $pagePath")

                        BitmapUtil.saveBitmapInFile(tempBitmap, pagePath)

                        // Send a progress event
                        setProgressAsync(
                            Data.Builder().putInt(KEY_CURRENT_INDEX, numPage)
                                .putString(KEY_CURRENT_PATH, pagePath).putInt(KEY_NB_PAGES, nbPages)
                                .build()
                        )
                    }
                }
                renderer.close()
                fileDescriptor.close()
            }
        } catch (e: IOException) {
            Log.v(TAG,"getPagesInPdfFile :: IOException $e")
            return false
        }

        Log.v(TAG,"getPagesInPdfFile $fileUri")
        return true
    }

}

