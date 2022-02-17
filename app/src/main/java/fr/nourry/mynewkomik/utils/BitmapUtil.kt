package fr.nourry.mynewkomik.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class BitmapUtil {
    companion object {
        fun createBitmap(byteArray:ByteArray, maxWidth:Int=0, maxHeight:Int=0) : Bitmap?{
            var bitmap: Bitmap? = null
            try {
                bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                val ratioWidth:Float = if (maxWidth != 0) (bitmap.width.toFloat()/maxWidth.toFloat()) else 1.0f
                val ratioHeight:Float = if (maxHeight != 0) (bitmap.height.toFloat()/maxHeight.toFloat()) else 1.0f
                val ratio = if (ratioWidth>ratioHeight) ratioWidth else ratioHeight
                val width: Int = (bitmap.width.toFloat()/ratio).toInt()
                val height:Int = (bitmap.height.toFloat()/ratio).toInt()
                Timber.v("createBitmap maxWidth=$maxWidth maxHeight=$maxHeight   bitmap.width=${bitmap.width} bitmap.height=${bitmap.height} width=$width height=$height")
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height,false)
            } catch (e: IllegalArgumentException) {
                Timber.e("Error creating bitmap")
                e.printStackTrace()
            }
            return bitmap
        }

        // Save a bitmap in a file
        fun saveBitmapInFile(bitmap: Bitmap?, dstPath: String):Boolean {
            if (bitmap!= null) {
                Timber.d( "saveBitmapInFile:: Writing new file : $dstPath")
                try {
                    val fOut = File(dstPath)
                    val os = FileOutputStream(fOut)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, os)
                    os.flush()
                    os.close()
                    return true
                } catch (e: IOException) {
                    Timber.d( "Error writing file $dstPath")
                    e.printStackTrace()
                }
            }
            return false
        }
    }
}
