package fr.nourry.mynewkomik.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class BitmapUtil {
    companion object {
        fun createBitmap(byteArray:ByteArray, w:Int=0, h:Int=0) : Bitmap?{
            var bitmap: Bitmap? = null
            try {
                bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                val width:Int = if (w > 0) w else bitmap.width
                val height:Int = if (h > 0) h else bitmap.height
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
