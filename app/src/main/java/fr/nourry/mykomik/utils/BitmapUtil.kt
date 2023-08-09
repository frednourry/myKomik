package fr.nourry.mykomik.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.ColorInt
import fr.nourry.mykomik.App
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale


class BitmapUtil {
    companion object {

        /**
         * Like BitmapFactory.decodeStream but trying to avoid OOM by setting maximum width and height (see https://developer.android.com/topic/performance/graphics/load-bitmap)
         */
        fun decodeStream(f: File?, width: Int=-1, height: Int=-1): Bitmap? {
            if (f == null) {
                Timber.w("decodeStream :: File null ! Returns null")
                return null
            }
            if (!f.exists()) {
                Timber.w("decodeStream :: File doesn't exist ! Returns null (${f.absolutePath})")
                return null
            }

            // Set the maximum width and height (if given)
            val maxWidth = if (width == -1) App.physicalConstants.metrics.widthPixels else width
            val maxHeight = if (height == -1) App.physicalConstants.metrics.widthPixels else height

            try {
                // Get the image size
                val optionTest = BitmapFactory.Options()
                optionTest.inJustDecodeBounds = true
                FileInputStream(f).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, optionTest)
                }

                val halfOutWidth: Int = optionTest.outWidth / 2
                val halfOutHeight: Int = optionTest.outHeight / 2
                var inSampleSize = 1

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfOutHeight / inSampleSize >= maxHeight && halfOutWidth / inSampleSize >= maxWidth) {
                    inSampleSize *= 2
                }

                // Decode bitmap with inSampleSize set
                val option = BitmapFactory.Options()
                option.inJustDecodeBounds = false
                option.inSampleSize = inSampleSize
                FileInputStream(f).use {inputStream ->
                    return BitmapFactory.decodeStream(inputStream, null, option)
                }
            } catch (e: OutOfMemoryError) {
                Timber.w("decodeStream :: OutOfMemoryError ! f=${f.absolutePath}")
            } catch (e: Error) {
                Timber.w("decodeStream :: error ! f=${f.absolutePath}")
                e.printStackTrace()
            }
            return null
        }

        /**
         * Like BitmapFactory.decodeByteArray but trying to avoid OOM by setting maximum width and height (see https://developer.android.com/topic/performance/graphics/load-bitmap)
         */
        private fun decodeByteArray(data: ByteArray?, offset:Int, length:Int, width: Int=-1, height: Int=-1):Bitmap? {
            // Set the maximum width and height (if given)
            val maxWidth = if (width == -1) App.physicalConstants.metrics.widthPixels else width
            val maxHeight = if (height == -1) App.physicalConstants.metrics.widthPixels else height

            try {
                // Get the image size
                val optionTest = BitmapFactory.Options()
                optionTest.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(data, offset, length, optionTest)

                val halfOutWidth: Int = optionTest.outWidth / 2
                val halfOutHeight: Int = optionTest.outHeight / 2
                var inSampleSize = 1

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfOutHeight / inSampleSize >= maxHeight && halfOutWidth / inSampleSize >= maxWidth) {
                    inSampleSize *= 2
                }

                // Decode bitmap with inSampleSize set
                val option = BitmapFactory.Options()
                option.inJustDecodeBounds = false
                option.inSampleSize = inSampleSize
                return BitmapFactory.decodeByteArray(data, offset, length, option)
            } catch (e: OutOfMemoryError) {
                Timber.w("decodeStream :: OutOfMemoryError !")
            } catch (e: Error) {
                Timber.w("decodeStream :: error !")
                e.printStackTrace()
            }
            return null
        }


        /**
         * Juxtapose bitmap with some rotation
         */
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

        /**
         * Resize an image and add a little frame around
         *  byteArray: byte array of the image that will be resized (with ratio respect) and framed
         *  thumbnailMaxWidth: width of the return image
         *  thumbnailMaxHeight: height of the return image
         *  innerImageMaxWidth: maximum width of the resize image
         *  innerImageMaxHeight: maximum height of the resize image
         *  borderSize: size (thickness) of the frame
         */
        fun createFramedBitmap(byteArray:ByteArray, thumbnailWidth:Int, thumbnailHeight:Int, innerImageMaxWidth: Int, innerImageMaxHeight:Int, borderSize:Int) : Bitmap?{
            Timber.v("createFramedBitmap")

            var bitmapToReturn:Bitmap? = null

            val pixelDxRatio = App.physicalConstants.pixelDxRatio
            val trueBorderSize = (borderSize*pixelDxRatio).toInt()
            var trueThumbnailWidth = (thumbnailWidth*pixelDxRatio).toInt()
            val trueThumbnailHeight = (thumbnailHeight*pixelDxRatio).toInt()
            var trueInnerImageMaxWidth = (innerImageMaxWidth*pixelDxRatio).toInt()
            var trueInnerImageMaxHeight = (innerImageMaxHeight*pixelDxRatio).toInt()

            // Check inputs
            if (trueThumbnailWidth < 0) trueThumbnailWidth = 150
            if (trueThumbnailHeight < 0) trueThumbnailWidth = 200
            if (trueInnerImageMaxWidth < 0) trueInnerImageMaxWidth = 100
            if (trueInnerImageMaxHeight < 0) trueInnerImageMaxHeight = 150

            Timber.v("trueFrameWidth=$trueThumbnailWidth trueThumbnailHeight=$trueThumbnailHeight trueInnerImageMaxWidth=$trueInnerImageMaxWidth trueInnerImageMaxHeight=$trueInnerImageMaxHeight")

            try {
                // Transform the ByteArray in Bitmap
                val bitmap = decodeByteArray(byteArray, 0, byteArray.size)
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
                if (imageRatio*trueInnerImageMaxHeight>trueInnerImageMaxWidth) {
                    trueInnerImageMaxHeight = (trueInnerImageMaxWidth/imageRatio).toInt()
                }
                Timber.v("imageRatio=$imageRatio  trueInnerImageMaxWidth=$trueInnerImageMaxWidth trueInnerImageMaxHeight=$trueInnerImageMaxHeight")

                // Rescale it
                var desiredScale = 0f
                val transformMatrix = Matrix()

                if (!shouldRotate) {
                    val scaleByWidth = trueInnerImageMaxWidth.toFloat() / bitmap.width.toFloat()
                    val scaleByHeight = trueInnerImageMaxHeight.toFloat() / bitmap.height.toFloat()
                    desiredScale = Math.min(scaleByWidth,scaleByHeight)
                    Timber.v("desiredScale=$desiredScale scaleByWidth=$scaleByWidth scaleByHeight=$scaleByHeight")
                } else {
                    val scaleByWidth = trueInnerImageMaxHeight.toFloat() / bitmap.width.toFloat()
                    val scaleByHeight = trueInnerImageMaxWidth.toFloat() / bitmap.height.toFloat()
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


        /**
         * Create a bitmap from bytes
         */
        fun createBitmap(byteArray:ByteArray, maxWidth:Int=0, maxHeight:Int=0) : Bitmap?{
            var bitmap: Bitmap? = decodeByteArray(byteArray, 0, byteArray.size)
            if (bitmap == null) return null

            try {
                val ratioWidth:Float = if (maxWidth != 0) (bitmap.width.toFloat()/maxWidth.toFloat()) else 1.0f
                val ratioHeight:Float = if (maxHeight != 0) (bitmap.height.toFloat()/maxHeight.toFloat()) else 1.0f
                val ratio = if (ratioWidth>ratioHeight) ratioWidth else ratioHeight
                val width: Int = (bitmap.width.toFloat()/ratio).toInt()
                val height:Int = (bitmap.height.toFloat()/ratio).toInt()
                Timber.v("createBitmap maxWidth=$maxWidth maxHeight=$maxHeight   bitmap.width=${bitmap.width} bitmap.height=${bitmap.height} width=$width height=$height")
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height,false)
            } catch (e: IllegalArgumentException) {
                Timber.e("createBitmap: Error creating bitmap")
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

        /**
         * Get the average color in an image (don't test all pixel, only 1 in 'definition' pixel - ie 1 in 50 pixel for example)
         */
        @ColorInt
        fun getAverageColor(bitmap:Bitmap, definition:Int = 100):Int {
            var averageRed = 0
            var averageBlue = 0
            var averageGreen = 0
            var pixelCount = 0
            var pixelColor : Int

            val limitWidth = Math.max(bitmap.width - 1, 0)
            val limitHeight = Math.max(bitmap.height - 1, 0)

            for(x in 0..limitWidth step definition) {
                for(y in 0..limitHeight step definition) {
                    pixelColor = bitmap.getPixel(x, y)
                    averageRed += Color.red(pixelColor)
                    averageGreen += Color.green(pixelColor)
                    averageBlue += Color.blue(pixelColor)
                    pixelCount++
                }
            }
            Timber.v("getAverageColor :: averageRed=$averageRed averageGreen=$averageGreen averageBlue=$averageBlue pixelCount=$pixelCount")

            return if (pixelCount != 0) {
                Color.rgb(averageRed/pixelCount, averageGreen/pixelCount, averageBlue/pixelCount)
            }
            else
                0
        }

        @ColorInt
        /**
         * Get the average color in the image vertical border (don't test all pixel, only 1 in 'definition' pixel - ie 1 in 50 pixel for example)
         */
        fun getAverageColorAtVBorder(bitmap:Bitmap, definition:Int = 30):Int {
            var averageRed = 0
            var averageBlue = 0
            var averageGreen = 0
            var pixelCount = 0
            var pixelColor : Int

            val limitWidth = Math.max(bitmap.width - 1, 0)
            val limitHeight = Math.max(bitmap.height - 1, 0)

            for(y in 0..limitHeight step definition) {
                pixelColor = bitmap.getPixel(0, y)
                averageRed += Color.red(pixelColor)
                averageGreen += Color.green(pixelColor)
                averageBlue += Color.blue(pixelColor)
                pixelCount++
            }

            for(y in 0..limitHeight step definition) {
                pixelColor = bitmap.getPixel(limitWidth, y)
                averageRed += Color.red(pixelColor)
                averageGreen += Color.green(pixelColor)
                averageBlue += Color.blue(pixelColor)
                pixelCount++
            }
            Timber.v("getAverageColorAtBorder :: averageRed=$averageRed averageGreen=$averageGreen averageBlue=$averageBlue pixelCount=$pixelCount")

            return if (pixelCount != 0) {
                Color.rgb(averageRed/pixelCount, averageGreen/pixelCount, averageBlue/pixelCount)
            }
            else
                0
        }

        /**
         * Get the average color in the image horizontal border (don't test all pixel, only 1 in 'definition' pixel - ie 1 in 50 pixel for example)
         */
        @ColorInt
        fun getAverageColorAtHBorder(bitmap:Bitmap, definition:Int = 30):Int {
            var averageRed = 0
            var averageBlue = 0
            var averageGreen = 0
            var pixelCount = 0
            var pixelColor : Int

            val limitWidth = Math.max(bitmap.width - 1, 0)
            val limitHeight = Math.max(bitmap.height - 1, 0)

            for(x in 0..limitWidth step definition) {
                pixelColor = bitmap.getPixel(0, 0)
                averageRed += Color.red(pixelColor)
                averageGreen += Color.green(pixelColor)
                averageBlue += Color.blue(pixelColor)
                pixelCount++
            }

            for(x in 0..limitWidth step definition) {
                pixelColor = bitmap.getPixel(0, limitHeight)
                averageRed += Color.red(pixelColor)
                averageGreen += Color.green(pixelColor)
                averageBlue += Color.blue(pixelColor)
                pixelCount++
            }
            Timber.v("getAverageColorAtBorder :: averageRed=$averageRed averageGreen=$averageGreen averageBlue=$averageBlue pixelCount=$pixelCount")

            return if (pixelCount != 0) {
                Color.rgb(averageRed/pixelCount, averageGreen/pixelCount, averageBlue/pixelCount)
            }
            else
                0
        }
    }
}
