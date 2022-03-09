package fr.nourry.mynewkomik.utils

import android.graphics.*
import fr.nourry.mynewkomik.App
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class BitmapUtil {
    companion object {
        // Juxtapose bitmap with some rotation
        fun createDirectoryThumbnailBitmap(bitmapCovers:List<Bitmap>):Bitmap {
            val pixelDxRatio = App.physicalConstants.pixelDxRatio
            val bitmapToReturn:Bitmap = Bitmap.createBitmap( (199.0f * pixelDxRatio).toInt(), (230.0f * pixelDxRatio).toInt(),Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmapToReturn)
            val centerX = (bitmapToReturn.width / 2)
            val centerY = (bitmapToReturn.height / 2)
            val degrees = 9.0f

            val paint = Paint(Paint.ANTI_ALIAS_FLAG/* and Paint.DITHER_FLAG and Paint.FILTER_BITMAP_FLAG*/)
            for (i in bitmapCovers.indices.reversed()) {
                val comicCover = bitmapCovers[i]
                val comicXCenter = (comicCover.width / 2).toFloat()
                val comicYCenter = (comicCover.height / 2).toFloat()
                val deltaDegrees = i * degrees

                val mRotate = Matrix()
                mRotate.postRotate(deltaDegrees, comicXCenter, comicYCenter)
                val rotatedBitmap = Bitmap.createBitmap(comicCover, 0, 0, comicCover.width, comicCover.height, mRotate, true)

                val mTranslate = Matrix()
                mTranslate.postTranslate( (centerX - (rotatedBitmap.width / 2).toFloat()), (centerY - (rotatedBitmap.height / 2).toFloat()) )
                c.drawBitmap(rotatedBitmap, mTranslate, paint)
                rotatedBitmap.recycle()
            }
            return bitmapToReturn
        }

        // Resize an image and add a little frame around
        fun createFramedBitmap(byteArray:ByteArray, maxWidth:Int=0, maxHeight:Int=0, borderSize:Int=5) : Bitmap?{
            var bitmap: Bitmap
            var bitmapToReturn:Bitmap? = null

            try {
                bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                // rotate if necessary
                if (bitmap.width>bitmap.height) {
                    val mRotate = Matrix()
                    mRotate.postRotate(270f, bitmap.width.toFloat()/2, bitmap.height.toFloat()/2)
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, mRotate, true)
                }

                val pixelDxRatio = App.physicalConstants.pixelDxRatio

                val borderSizeF = borderSize*pixelDxRatio
                val ratioWidth:Float = if (maxWidth != 0) (bitmap.width.toFloat()/maxWidth.toFloat()) else 1.0f
                val ratioHeight:Float = if (maxHeight != 0) (bitmap.height.toFloat()/maxHeight.toFloat()) else 1.0f
                val ratio = if (ratioWidth>ratioHeight) ratioWidth else ratioHeight
                val trueWidth = ((bitmap.width.toFloat()/ratio) * pixelDxRatio).toInt()
                val trueHeight = ((bitmap.height.toFloat()/ratio) * pixelDxRatio).toInt()
                val width = (trueWidth - 2*borderSizeF).toInt()
                val height = (trueHeight - 2*borderSizeF).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height,false)

                bitmapToReturn = Bitmap.createBitmap(trueWidth, trueHeight, bitmap.config)
                bitmapToReturn.eraseColor(Color.WHITE)
                val framePaint = Paint()
                framePaint.flags = Paint.ANTI_ALIAS_FLAG
                framePaint.color = Color.WHITE

                val canvas = Canvas(bitmapToReturn)
                val rectDest = Rect(borderSize, borderSize, trueWidth-borderSize, trueHeight-borderSize)
                canvas.drawBitmap(scaledBitmap, null, rectDest, framePaint)

            } catch (e: IllegalArgumentException) {
                Timber.e("Error creating bitmap")
                e.printStackTrace()
            }
            return bitmapToReturn
        }


        // Create a bitmap from bytes
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
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
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
