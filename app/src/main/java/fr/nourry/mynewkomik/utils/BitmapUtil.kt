package fr.nourry.mynewkomik.utils

import android.graphics.*
import fr.nourry.mynewkomik.App
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*


class BitmapUtil {
    companion object {
        // Juxtapose bitmap with some rotation
        fun createDirectoryThumbnailBitmap(bitmapCovers:List<Bitmap>, frameMaxWidth:Int, frameMaxHeight:Int):Bitmap {
            val pixelDxRatio = App.physicalConstants.pixelDxRatio
            val bitmapToReturn:Bitmap = Bitmap.createBitmap( (frameMaxWidth * pixelDxRatio).toInt(), (frameMaxHeight * pixelDxRatio).toInt(),Bitmap.Config.ARGB_8888)
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
                mRotate.preRotate(deltaDegrees)
                val rotatedBitmap = Bitmap.createBitmap(comicCover, 0, 0, comicCover.width, comicCover.height, mRotate, true)

                val mTranslate = Matrix()

                mTranslate.postTranslate( (centerX - (rotatedBitmap.width / 2).toFloat()), (centerY - (rotatedBitmap.height / 2).toFloat()) )
                c.drawBitmap(rotatedBitmap, mTranslate, paint)
                rotatedBitmap.recycle()
            }
            return bitmapToReturn
        }

        // Resize an image and add a little frame around
        // byteArray: byte array of the image that will be resized (with ratio respect) and framed
        // thumbnailMaxWidth: width of the return image
        // thumbnailMaxHeight: height of the return image
        // innerImageMaxWidth: maximum width of the resize image
        // innerImageMaxHeight: maximum height of the resize image
        // borderSize: size (thickness) of the frame
        fun createFramedBitmap(byteArray:ByteArray, thumbnailWidth:Int, thumbnailHeight:Int, innerImageMaxWidth: Int, innerImageMaxHeight:Int, borderSize:Int) : Bitmap?{
            var bitmapToReturn:Bitmap? = null

            val pixelDxRatio = App.physicalConstants.pixelDxRatio
            val trueBorderSize = (borderSize*pixelDxRatio).toInt()
            var trueThumbnailWidth = (thumbnailWidth*pixelDxRatio).toInt()
            val trueThumbnailHeight = (thumbnailHeight*pixelDxRatio).toInt()
            var TrueInnerImageMaxWidth = (innerImageMaxWidth*pixelDxRatio).toInt()
            var trueInnerImageMaxHeight = (innerImageMaxHeight*pixelDxRatio).toInt()

            // Check inputs
            if (trueThumbnailWidth < 0) trueThumbnailWidth = 150
            if (trueThumbnailHeight < 0) trueThumbnailWidth = 200
            if (TrueInnerImageMaxWidth < 0) TrueInnerImageMaxWidth = 100
            if (trueInnerImageMaxHeight < 0) trueInnerImageMaxHeight = 150

            Timber.v("trueFrameWidth=$trueThumbnailWidth trueThumbnailHeight=$trueThumbnailHeight TrueInnerImageMaxWidth=$TrueInnerImageMaxWidth trueInnerImageMaxHeight=$trueInnerImageMaxHeight")

            try {
                // Transform the ByteArray in Bitmap
                var bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                if (bitmap == null) return bitmap
                Timber.v("bitmap.width=${bitmap.width} bitmap.height=${bitmap.height}")
                val shouldRotate = bitmap.width>bitmap.height

/*                if (shouldRotate) {
                    val rotateMatrix = Matrix()
                    rotateMatrix.preRotate(270f)
                    bitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        rotateMatrix,
                        true
                    )
                }*/

                // Adjust the desired size of the rescale image
                var imageRatio = bitmap.width.toFloat()/bitmap.height.toFloat()
                if (bitmap.width>bitmap.height) {
                    imageRatio = 1/imageRatio
                }
                if (imageRatio*trueInnerImageMaxHeight>TrueInnerImageMaxWidth) {
                    trueInnerImageMaxHeight = (TrueInnerImageMaxWidth/imageRatio).toInt()
                }
                Timber.v("imageRatio=$imageRatio  TrueInnerImageMaxWidth=$TrueInnerImageMaxWidth trueInnerImageMaxHeight=$trueInnerImageMaxHeight")

                // Rescale it
                var desiredScale = 0f
                val transformMatrix = Matrix()

                if (!shouldRotate) {
                    val scaleByWidth = TrueInnerImageMaxWidth.toFloat() / bitmap.width.toFloat()
                    val scaleByHeight = trueInnerImageMaxHeight.toFloat() / bitmap.height.toFloat()
                    desiredScale = Math.min(scaleByWidth,scaleByHeight)
                    Timber.v("desiredScale=$desiredScale scaleByWidth=$scaleByWidth scaleByHeight=$scaleByHeight")
                } else {
                    val scaleByWidth = trueInnerImageMaxHeight.toFloat() / bitmap.width.toFloat()
                    val scaleByHeight = TrueInnerImageMaxWidth.toFloat() / bitmap.height.toFloat()
                    desiredScale = Math.min(scaleByWidth,scaleByHeight)
                    Timber.v("desiredScale=$desiredScale scaleByWidth=$scaleByWidth scaleByHeight=$scaleByHeight")

                    transformMatrix.preRotate(270f)
//                    transformMatrix.postRotate(270f, bitmap.width.toFloat()/2, bitmap.height.toFloat()/2)
                }
                transformMatrix.preScale(desiredScale, desiredScale)

                val rescaledBitmap = Bitmap.createBitmap(bitmap,0,0, bitmap.width, bitmap.height, transformMatrix,true)
                Timber.v("rescaleMatrix=$transformMatrix rescaledBitmap.width=${rescaledBitmap.width} rescaledBitmap.height=${rescaledBitmap.height}")
                bitmap.recycle()

                val trueFrameWidth = rescaledBitmap.width + 2*trueBorderSize
                val trueFrameHeight = rescaledBitmap.height + 2*trueBorderSize
                Timber.v("trueFrameWidth=$trueFrameWidth trueFrameHeight=$trueFrameHeight")

                bitmapToReturn = Bitmap.createBitmap(trueThumbnailWidth, trueThumbnailHeight, rescaledBitmap.config)
                val framedPaint = Paint()
                framedPaint.flags = Paint.ANTI_ALIAS_FLAG
                framedPaint.color = Color.WHITE

                val left = (trueThumbnailWidth-trueFrameWidth).toFloat() / 2
                val top = (trueThumbnailHeight-trueFrameHeight).toFloat() / 2
                val right = left + trueFrameWidth
                val bottom = top + trueFrameHeight
                Timber.v("left=$left top=$top right=$right bottom=$bottom")
                val frameRect = RectF(left, top, right, bottom)


                // Draw the frame
                val canvas = Canvas(bitmapToReturn)
                canvas.drawRect(frameRect, framedPaint)

                // Insert the rescaled image in its center
                val rectDest = RectF(left+trueBorderSize, top+trueBorderSize, right-trueBorderSize, bottom-trueBorderSize)
                canvas.drawBitmap(rescaledBitmap, null, rectDest, framedPaint)
                rescaledBitmap.recycle()

            } catch (e: IllegalArgumentException) {
                Timber.e("Error creating framed bitmap")
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
        fun saveBitmapInFile(bitmap: Bitmap?, dstPath: String, boolScreensized:Boolean = false):Boolean {
            if (bitmap!= null) {
                var bitmapResized:Bitmap = bitmap
                if (boolScreensized) {
                    bitmapResized = Bitmap.createScaledBitmap(
                        bitmapResized,
                        App.physicalConstants.metrics.widthPixels,
                        App.physicalConstants.metrics.heightPixels,
                        false)
                }

                Timber.d( "saveBitmapInFile:: Writing new file : $dstPath (${bitmapResized.width} x ${bitmapResized.height})")

                try {
                    val fOut = File(dstPath)
                    val os = FileOutputStream(fOut)
                    val ext = fOut.extension.lowercase(Locale.getDefault())
                    val format = when (ext) {
                        "jpg" -> Bitmap.CompressFormat.JPEG
                        "png" -> Bitmap.CompressFormat.PNG
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    bitmapResized.compress(format, 95, os)
                    os.flush()
                    os.close()
                    bitmapResized.recycle()
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
