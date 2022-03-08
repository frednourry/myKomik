package fr.nourry.mynewkomik.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.nourry.mynewkomik.utils.BitmapUtil
import timber.log.Timber
import java.io.File

/**
 * Worker to generate a directory thumbnail with some comic thumbnail
 */
class ImageDirWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val MAX_COVER_IN_THUMBNAIL             = 3

        const val KEY_COMIC_PATH_0                  = "comicPath0"
        const val KEY_COMIC_PATH_1                  = "comicPath1"
        const val KEY_COMIC_PATH_2                  = "comicPath2"
        const val KEY_COMIC_PATH_3                  = "comicPath3"
        const val KEY_COMIC_PATH_4                  = "comicPath4"
        const val KEY_DESTINATION_DIRECTORY_PATH    = "destinationDirectoryPath"
    }

    override fun doWork(): Result {
        Timber.d("ImageDirWorker.doWork")
        val comicPath0 = inputData.getString(KEY_COMIC_PATH_0)
        val comicPath1 = inputData.getString(KEY_COMIC_PATH_1)
        val comicPath2 = inputData.getString(KEY_COMIC_PATH_2)
        val comicPath3 = inputData.getString(KEY_COMIC_PATH_3)
        val comicPath4 = inputData.getString(KEY_COMIC_PATH_4)
        val destPath = inputData.getString(KEY_DESTINATION_DIRECTORY_PATH)
        Timber.d("UnzipFirstImageWorker.doWork :: comicPath0=$comicPath0 comicPath1=$comicPath1 comicPath2=$comicPath2 comicPath3=$comicPath3 comicPath4=$comicPath4 destPath=$destPath")

        val pathList = mutableListOf<String>()
        if (comicPath0 != null && comicPath0 != "")  pathList.add(comicPath0)
        if (comicPath1 != null && comicPath1 != "")  pathList.add(comicPath1)
        if (comicPath2 != null && comicPath2 != "")  pathList.add(comicPath2)
        if (comicPath3 != null && comicPath3 != "")  pathList.add(comicPath3)
        if (comicPath4 != null && comicPath4 != "")  pathList.add(comicPath4)

        val bitmapList = mutableListOf<Bitmap>()
        var cpt=MAX_COVER_IN_THUMBNAIL
        for (path in pathList) {
            if (File(path).exists()) {
                bitmapList.add(BitmapFactory.decodeFile(path))
                cpt--
                if (cpt<=0) break
            }
        }

        // Generate the thumbnail
        if (bitmapList.size>0) {
            val bitmap = BitmapUtil.createDirectoryThumbnailBitmap(bitmapList)
            if (destPath != null) {
                BitmapUtil.saveBitmapInFile(bitmap, destPath)
            }
        }

        val outputData = workDataOf(UncompressFirstImageWorker.KEY_IMAGE_DESTINATION_PATH to destPath,
                                    UncompressFirstImageWorker.KEY_NB_PAGES to bitmapList.size)

        return Result.success(outputData)
    }

}