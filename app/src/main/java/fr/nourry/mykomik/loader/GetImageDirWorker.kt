package fr.nourry.mykomik.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.nourry.mykomik.utils.BitmapUtil
import android.util.Log
import java.io.File

/**
 * Worker to generate a directory thumbnail with some comic thumbnail
 */
class GetImageDirWorker (context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
    companion object {
        const val TAG = "GetImageDirWorker"

        const val MAX_COVER_IN_THUMBNAIL            = 3

        const val KEY_THUMBNAIL_WIDTH               = "thumbnailWidth"
        const val KEY_THUMBNAIL_HEIGHT              = "thumbnailHeight"
        const val KEY_COMIC_PATH_0                  = "comicPath0"
        const val KEY_COMIC_PATH_1                  = "comicPath1"
        const val KEY_COMIC_PATH_2                  = "comicPath2"
        const val KEY_COMIC_PATH_3                  = "comicPath3"
        const val KEY_COMIC_PATH_4                  = "comicPath4"
        const val KEY_DESTINATION_DIRECTORY_PATH    = "destinationDirectoryPath"
    }

    override fun doWork(): Result {
        Log.d(TAG,"GetImageDirWorker.doWork")
        val thumbnailWidth = inputData.getInt(KEY_THUMBNAIL_WIDTH, 200)
        val thumbnailHeight = inputData.getInt(KEY_THUMBNAIL_HEIGHT, 220)

        val comicPath0 = inputData.getString(KEY_COMIC_PATH_0)
        val comicPath1 = inputData.getString(KEY_COMIC_PATH_1)
        val comicPath2 = inputData.getString(KEY_COMIC_PATH_2)
        val comicPath3 = inputData.getString(KEY_COMIC_PATH_3)
        val comicPath4 = inputData.getString(KEY_COMIC_PATH_4)
        val destPath = inputData.getString(KEY_DESTINATION_DIRECTORY_PATH)
        Log.d(TAG,"GetImageDirWorker.doWork :: comicPath0=$comicPath0 comicPath1=$comicPath1 comicPath2=$comicPath2 comicPath3=$comicPath3 comicPath4=$comicPath4 destPath=$destPath")

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
            val bitmap = BitmapUtil.createDirectoryThumbnailBitmap(bitmapList, thumbnailWidth, thumbnailHeight)
            if (destPath != null) {
                BitmapUtil.saveBitmapInFile(bitmap, destPath)
            }
        }

        val outputData = workDataOf(GetCoverWorker.KEY_IMAGE_DESTINATION_PATH to destPath,
                                    GetCoverWorker.KEY_NB_PAGES to bitmapList.size)

        return Result.success(outputData)
    }

}