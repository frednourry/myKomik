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
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext


class BitmapUtil {
    companion object {
        const val TAG = "BitmapUtil"

        val maxTextureSize = getDeviceMaxTextureSize()
        val maxHalfTextureSize = maxTextureSize/2

        init {
            Log.i(TAG,"BitmapUtil.maxTextureSize=$maxTextureSize")
        }

        // Get the maximum texture size for this device
        //   see this at https://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit
        private fun getDeviceMaxTextureSize(): Int {
            // Safe minimum default size
            val IMAGE_MAX_BITMAP_DIMENSION = 2048

            // Get EGL Display
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)

            // Initialise
            val version = IntArray(2)
            egl.eglInitialize(display, version)

            // Query total number of configurations
            val totalConfigurations = IntArray(1)
            egl.eglGetConfigs(display, null, 0, totalConfigurations)

            // Query actual list configurations
            val configurationsList = arrayOfNulls<EGLConfig>(
                totalConfigurations[0]
            )
            egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations)
            val textureSize = IntArray(1)
            var maximumTextureSize = 0

            // Iterate through all the configurations to located the maximum texture size
            for (i in 0 until totalConfigurations[0]) {
                // Only need to check for width since opengl textures are always squared
                egl.eglGetConfigAttrib(
                    display,
                    configurationsList[i],
                    EGL10.EGL_MAX_PBUFFER_WIDTH,
                    textureSize
                )

                // Keep track of the maximum texture size
                if (maximumTextureSize < textureSize[0]) maximumTextureSize = textureSize[0]
            }

            // Release
            egl.eglTerminate(display)

            // Return largest texture size found, or default
            return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION)
        }

        /**
         * Like BitmapFactory.decodeStream but trying to avoid OOM by setting maximum width and height (see https://developer.android.com/topic/performance/graphics/load-bitmap)
         */
        fun decodeStream(f: File?, width: Int=-1, height: Int=-1, isSimplifyBitmapConfig:Boolean = false): Bitmap? {
            Log.v(TAG,"decodeStream(${f?.absolutePath ?: ""}, width=$width, height=$height, isSimplifyBitmapConfig=$isSimplifyBitmapConfig)")
            if (f == null) {
                Log.w(TAG,"decodeStream :: File null ! Returns null")
                return null
            }
            if (!f.exists()) {
                Log.w(TAG,"decodeStream :: File doesn't exist ! Returns null (${f.absolutePath})")
                return null
            }

            // Set the maximum width and height (if given)
            val maxWidth = Math.min(if (width == -1) App.physicalConstants.metrics.widthPixels else width, maxHalfTextureSize)
            val maxHeight = Math.min(if (height == -1) App.physicalConstants.metrics.widthPixels else height, maxHalfTextureSize)

            try {
                // Get the image size
                val optionsTest = BitmapFactory.Options()
                optionsTest.inJustDecodeBounds = true
                FileInputStream(f).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, optionsTest)
                }

                var halfOutWidth: Int = optionsTest.outWidth / 2
                var halfOutHeight: Int = optionsTest.outHeight / 2
                var inSampleSize = 1

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfOutHeight >= maxHeight || halfOutWidth >= maxWidth) {
                    inSampleSize *= 2
                    halfOutHeight /=2
                    halfOutWidth /=2
                }
                // Decode bitmap with inSampleSize set
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                options.inScaled = false
                if (isSimplifyBitmapConfig)
                    options.inPreferredConfig = Bitmap.Config.RGB_565

                Log.v(TAG,"  decodeStream => options.inSampleSize=$inSampleSize")

                FileInputStream(f).use {inputStream ->
                    return BitmapFactory.decodeStream(inputStream, null, options)
                }
            } catch (e: OutOfMemoryError) {
                Log.w(TAG,"decodeStream :: OutOfMemoryError ! f=${f.absolutePath}")
            } catch (e: Error) {
                Log.w(TAG,"decodeStream :: error ! f=${f.absolutePath}")
                e.printStackTrace()
            }
            return null
        }

        /**
         * Like BitmapFactory.decodeByteArray but trying to avoid OOM by setting maximum width and height (see https://developer.android.com/topic/performance/graphics/load-bitmap)
         */
        private fun decodeByteArray(data: ByteArray?, offset:Int, length:Int, width: Int=-1, height: Int=-1):Bitmap? {
            Log.v(TAG,"decodeStream(ByteArray, offset=$offset, length=$length, width=$width, height=$height)")

            // Set the maximum width and height (if given)
            val maxWidth = Math.min(if (width == -1) App.physicalConstants.metrics.widthPixels else width, maxHalfTextureSize)
            val maxHeight = Math.min(if (height == -1) App.physicalConstants.metrics.widthPixels else height, maxHalfTextureSize)

            try {
                // Get the image size
                val optionsTest = BitmapFactory.Options()
                optionsTest.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(data, offset, length, optionsTest)

                var halfOutWidth: Int = optionsTest.outWidth / 2
                var halfOutHeight: Int = optionsTest.outHeight / 2
                var inSampleSize = 1

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfOutHeight >= maxHeight || halfOutWidth >= maxWidth) {
                    inSampleSize *= 2
                    halfOutHeight /=2
                    halfOutWidth /=2
                }

                // Decode bitmap with inSampleSize set
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                options.inScaled = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                Log.v(TAG,"  decodeStream => options.inSampleSize=$inSampleSize")

                return BitmapFactory.decodeByteArray(data, offset, length, options)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG,"decodeStream :: OutOfMemoryError !")
            } catch (e: Error) {
                Log.w(TAG,"decodeStream :: error !")
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
        fun createFramedBitmap(byteArray:ByteArray, thumbnailWidth:Int, thumbnailHeight:Int, innerImageMaxWidth: Int, innerImageMaxHeight:Int, borderSize:Int) : Bitmap? {
            Log.v(TAG,"createFramedBitmap(byteArray, $thumbnailWidth, $thumbnailHeight, $innerImageMaxWidth, $innerImageMaxHeight, $borderSize)")
            // Transform the ByteArray in Bitmap
            val bitmap = decodeByteArray(byteArray, 0, byteArray.size)
            if (bitmap == null) return bitmap

            return createFramedBitmap(bitmap, thumbnailWidth, thumbnailHeight, innerImageMaxWidth, innerImageMaxHeight, borderSize)
        }

        /**
         * Resize an image and add a little frame around
         *  bitmap: the image that will be resized (with ratio respect) and framed
         *  thumbnailMaxWidth: width of the return image
         *  thumbnailMaxHeight: height of the return image
         *  innerImageMaxWidth: maximum width of the resize image
         *  innerImageMaxHeight: maximum height of the resize image
         *  borderSize: size (thickness) of the frame
         */
        fun createFramedBitmap(bitmap:Bitmap, thumbnailWidth:Int, thumbnailHeight:Int, innerImageMaxWidth: Int, innerImageMaxHeight:Int, borderSize:Int) : Bitmap?{
            Log.v(TAG,"createFramedBitmap(bitmap, $thumbnailWidth, $thumbnailHeight, $innerImageMaxWidth, $innerImageMaxHeight, $borderSize)")

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

            Log.v(TAG,"trueFrameWidth=$trueThumbnailWidth trueThumbnailHeight=$trueThumbnailHeight trueInnerImageMaxWidth=$trueInnerImageMaxWidth trueInnerImageMaxHeight=$trueInnerImageMaxHeight")

            try {
                Log.v(TAG,"bitmap.width=${bitmap.width} bitmap.height=${bitmap.height}")
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
                Log.v(TAG,"imageRatio=$imageRatio  trueInnerImageMaxWidth=$trueInnerImageMaxWidth trueInnerImageMaxHeight=$trueInnerImageMaxHeight")

                // Rescale it
                var desiredScale = 0f
                val transformMatrix = Matrix()

                if (!shouldRotate) {
                    val scaleByWidth = trueInnerImageMaxWidth.toFloat() / bitmap.width.toFloat()
                    val scaleByHeight = trueInnerImageMaxHeight.toFloat() / bitmap.height.toFloat()
                    desiredScale = Math.min(scaleByWidth,scaleByHeight)
                    Log.v(TAG,"desiredScale=$desiredScale scaleByWidth=$scaleByWidth scaleByHeight=$scaleByHeight")
                } else {
                    val scaleByWidth = trueInnerImageMaxHeight.toFloat() / bitmap.width.toFloat()
                    val scaleByHeight = trueInnerImageMaxWidth.toFloat() / bitmap.height.toFloat()
                    desiredScale = Math.min(scaleByWidth,scaleByHeight)
                    Log.v(TAG,"desiredScale=$desiredScale scaleByWidth=$scaleByWidth scaleByHeight=$scaleByHeight")

                    transformMatrix.preRotate(270f)
//                    transformMatrix.postRotate(270f, bitmap.width.toFloat()/2, bitmap.height.toFloat()/2)
                }
                transformMatrix.preScale(desiredScale, desiredScale)

                val rescaledBitmap = Bitmap.createBitmap(bitmap,0,0, bitmap.width, bitmap.height, transformMatrix,true)
                Log.v(TAG,"rescaleMatrix=$transformMatrix rescaledBitmap.width=${rescaledBitmap.width} rescaledBitmap.height=${rescaledBitmap.height}")
                bitmap.recycle()

                val trueFrameWidth = rescaledBitmap.width + 2*trueBorderSize
                val trueFrameHeight = rescaledBitmap.height + 2*trueBorderSize
                Log.v(TAG,"trueFrameWidth=$trueFrameWidth trueFrameHeight=$trueFrameHeight")

                bitmapToReturn = Bitmap.createBitmap(trueThumbnailWidth, trueThumbnailHeight, rescaledBitmap.config!!)
                val framedPaint = Paint()
                framedPaint.flags = Paint.ANTI_ALIAS_FLAG
                framedPaint.color = Color.WHITE

                val left = (trueThumbnailWidth-trueFrameWidth).toFloat() / 2
                val top = (trueThumbnailHeight-trueFrameHeight).toFloat() / 2
                val right = left + trueFrameWidth
                val bottom = top + trueFrameHeight
                Log.v(TAG,"left=$left top=$top right=$right bottom=$bottom")
                val frameRect = RectF(left, top, right, bottom)


                // Draw the frame
                val canvas = Canvas(bitmapToReturn)
                canvas.drawRect(frameRect, framedPaint)

                // Insert the rescaled image in its center
                val rectDest = RectF(left+trueBorderSize, top+trueBorderSize, right-trueBorderSize, bottom-trueBorderSize)
                canvas.drawBitmap(rescaledBitmap, null, rectDest, framedPaint)
                rescaledBitmap.recycle()

            } catch (e: IllegalArgumentException) {
                Log.e(TAG,"Error creating framed bitmap")
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
                Log.v(TAG,"createBitmap maxWidth=$maxWidth maxHeight=$maxHeight   bitmap.width=${bitmap.width} bitmap.height=${bitmap.height} width=$width height=$height")
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height,false)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG,"createBitmap: Error creating bitmap")
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

                Log.d(TAG, "saveBitmapInFile:: Writing new file : $dstPath (${bitmapResized.width} x ${bitmapResized.height})")

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
                    Log.d(TAG, "Error writing file $dstPath")
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
            Log.v(TAG,"getAverageColor :: averageRed=$averageRed averageGreen=$averageGreen averageBlue=$averageBlue pixelCount=$pixelCount")

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
            Log.v(TAG,"getAverageColorAtBorder :: averageRed=$averageRed averageGreen=$averageGreen averageBlue=$averageBlue pixelCount=$pixelCount")

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
                pixelColor = bitmap.getPixel(x, 0)
                averageRed += Color.red(pixelColor)
                averageGreen += Color.green(pixelColor)
                averageBlue += Color.blue(pixelColor)
                pixelCount++
            }

            for(x in 0..limitWidth step definition) {
                pixelColor = bitmap.getPixel(x, limitHeight)
                averageRed += Color.red(pixelColor)
                averageGreen += Color.green(pixelColor)
                averageBlue += Color.blue(pixelColor)
                pixelCount++
            }
            Log.v(TAG,"getAverageColorAtBorder :: averageRed=$averageRed averageGreen=$averageGreen averageBlue=$averageBlue pixelCount=$pixelCount")

            return if (pixelCount != 0) {
                Color.rgb(averageRed/pixelCount, averageGreen/pixelCount, averageBlue/pixelCount)
            }
            else
                0
        }
    }
}
