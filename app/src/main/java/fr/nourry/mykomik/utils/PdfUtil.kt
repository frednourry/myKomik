package fr.nourry.mykomik.utils

import android.graphics.Bitmap
import android.graphics.Color


class PdfUtil {
    companion object {
        public fun isBitmapBlankOrWhite(bitmap: Bitmap?): Boolean {
            if (bitmap == null)
                return true

            val w = bitmap.width
            val h = bitmap.height
            for (i in 0 until w) {
                for (j in 0 until h) {
                    val pixel = bitmap.getPixel(i, j)
                    if (pixel != Color.WHITE) {
                        return false
                    }
                }
            }
            return true
        }
    }

}