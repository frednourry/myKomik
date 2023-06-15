package fr.nourry.mykomik.pageslider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import timber.log.Timber
import kotlin.math.sqrt

// Define display option
enum class DisplayOption {
    FULL,                   // All the image is visible
    MAXIMIZE_WIDTH,         // The image is zoomed to maximized its width
    MAXIMIZE_HEIGHT         // The image is zoomed to maximized its height
}

/**
 *
 * A ImageView that can zoom in/out and move on the visible part. The min/max zoom are constants.
 *  You have to call onTouchImageView(event: MotionEvent):TouchResult to transmit OnTouchListener event.
 *
 */
class MagnifyImageView(context: Context, attrs: AttributeSet?=null):AppCompatImageView(context, attrs) {

    interface Listener {
        fun onMagnifyImageViewClick(param:Any?, x:Float, y:Float)
    }

    companion object {
        private const val defaultMinZoom = 1f        // Min total zoom out
        private const val defaultMaxZoom = 3f        // Max total zoom in
        private const val defaultClickDelay = 200    // Delay between ACTION_DOWN and ACTION_UP to consider this as a click

        fun printFloatArray(f:FloatArray, label:String="printMatrix") {
            Timber.i("$label${f[0]} ${f[1]} ${f[2]} ${f[3]} ${f[4]} ${f[5]} ${f[6]} ${f[7]} ${f[8]}")
        }

    }


    init {
        // Create a custom view :: https://developer.android.com/training/custom-views/create-view

        // Read attributes from the xml file
        context.theme.obtainStyledAttributes(attrs, R.styleable.MagnifyImageView,0, 0).apply {
            try {
                minZoom = getFloat(R.styleable.MagnifyImageView_minZoom, defaultMinZoom)
                maxZoom = getFloat(R.styleable.MagnifyImageView_maxZoom, defaultMaxZoom)
                clickDelay = getInt(R.styleable.MagnifyImageView_clickDelay, defaultClickDelay)
            }catch (e:Exception) { Timber.e(e.stackTraceToString())
            } finally {
                recycle()
            }
        }
    }

    var imagePath = ""

    private var firstDrawCompleted = false

    private var minZoom = defaultMinZoom
    private var maxZoom = defaultMaxZoom
    private var clickDelay = defaultClickDelay
    private var displayOption = DisplayOption.FULL

    private var magnifyImageViewListener:Listener? = null
    private var magnifyImageViewListenerParam:Any? = null

    private var isImageModified = false
    private var lastEventX:Float = 0f                   // To remember the last event.x
    private var lastEventY:Float = 0f                   // To remember the last event.y
    private var width0 = 0f                             // To remember the real width of the image before any modification
    private var height0 = 0f                            // To remember the real height of the image before any modification
    private var fingersDistance0 = 0f                   // To remember the initial distance between the fingers when zooming
    private var oldScaleType = ScaleType.FIT_CENTER     // To remember the initial scaleType
    private var currentScale = 1f                       // The current zoom level
    private var lastActionDownDate = SystemClock.elapsedRealtime()  // To remember when was the last MotionEvent.ACTION_DOWN

    private var movementMode:MovementType = MovementType.NONE

    private fun printMatrix(label:String="printMatrix :: ") {
        val f = FloatArray(9)
        imageMatrix.getValues(f)

        printFloatArray(f, label)
    }

    /**
     * Apply given modifications, by checking its values and modify them to respect conditions (MinZoom <= zoom <= MaxZoom) and no unnecessary borders
     *  (deltaX,deltaY): Translation
     *  (deltaScale, centerX, centerY): Zoom on the point defined by (centerX, centerY)
     */
    private fun checkAndUpdateImageByDelta(deltaX:Float, deltaY:Float, deltaScale:Float, centerX: Float, centerY: Float) {
        Timber.w("checkAndUpdateImageByDelta deltaX=$deltaX deltaY=$deltaY deltaScale=$deltaScale centerX=$centerX centerY=$centerY")
        Timber.w("    imagePath=$imagePath")
        var dx = deltaX
        var dy = deltaY

        val f = FloatArray(9)
        imageMatrix.getValues(f)

        val currentWidth = width0*f[Matrix.MSCALE_X]
        val currentHeight = height0*f[Matrix.MSCALE_Y]
        val marginX = width - currentWidth
        val marginY = height - currentHeight

//        Timber.e("    IMAGEVIEW::${imageView.width}x${imageView.height}  width0=$width0 height0=$height0")

//            Timber.d("     marginX=$marginX  marginY=$marginY currentWidth=$currentWidth currentHeight=$currentHeight")

        // Check Y borders
        if (f[Matrix.MTRANS_Y] > 0) {
            val halfMarginY = marginY / 2
            dy = halfMarginY - f[Matrix.MTRANS_Y]
        } else if (f[Matrix.MTRANS_Y] + dy > 0) {
            // Check if the top side is really visible
            dy = -f[Matrix.MTRANS_Y]
        } else if (f[Matrix.MTRANS_Y] + dy < marginY) {
            dy = marginY - f[Matrix.MTRANS_Y]
        }

        // Check X borders
        if (f[Matrix.MTRANS_X] > 0) {
            val halfMarginX = marginX / 2
            dx = halfMarginX - f[Matrix.MTRANS_X]
        } else if (f[Matrix.MTRANS_X] + dx > 0) {
            // Check if the top side is really visible
            dx = -f[Matrix.MTRANS_X]
        } else if (f[Matrix.MTRANS_X] + dx < marginX) {
            dx = marginX - f[Matrix.MTRANS_X]
        }
        Timber.v("     => dx=$dx dy=$dy deltaScale=$deltaScale <=")

        if (dx != 0f || dy != 0f || deltaScale!= 0f) {
            Timber.v("     => dx=$dx dy=$dy deltaScale=$deltaScale <==")
            scaleType = ScaleType.MATRIX
            imageMatrix = Matrix().apply {
                // Scale
                if (deltaScale != 0f) {
                    postScale(deltaScale, deltaScale, centerX, centerY)
                }

                // Translate
                if (dx != 0f || dy != 0f) {
                    postTranslate(dx, dy)
                }
                preConcat(imageMatrix)

            }
            invalidate()
        }

        printMatrix("checkAndUpdateImageByDelta :: ")
    }

    private fun checkAndUpdateImageByValues(posX:Float, posY:Float, scale:Float, centerX: Float, centerY: Float) {
        Timber.w("checkAndUpdateImageByValues posX=$posX posY=$posY scale=$scale centerX=$centerX centerY=$centerY")

        scaleType = ScaleType.MATRIX
        val m = Matrix().apply {
            // Scale
            preScale(scale, scale, centerX, centerY)

            postTranslate(-posX, -posY)

        }
        imageMatrix = m
        invalidate()

        isImageModified = true

        printMatrix("checkAndUpdateImageByValues :: ")
    }

    /**
     * Ask the imageView to proceed a MotionEvent.
     * Returns TouchResult(isCatch, isClick) where 'isCatch' tells if the event was caught and 'isClick' if the event can be consider as a click
     */
    fun onTouchImageView(event: MotionEvent):Boolean {
        Timber.v("onTouchImageView $displayOption :: width0 = $width0 height0 = $height0 currentScale = $currentScale")
        Timber.v("onTouchImageView imagePath=$imagePath")
        Timber.v("onTouchImageView drawable:: ${drawable.dirtyBounds} ${drawable.intrinsicWidth} ${drawable.intrinsicHeight} ${drawable.minimumWidth} ${drawable.minimumHeight}")
        Timber.v("onTouchImageView :: ${measuredWidth} ${measuredHeight}")
        Timber.v("onTouchImageView :: App.physicalConstants.metrics :: ${App.physicalConstants.metrics.widthPixels} ${App.physicalConstants.metrics.heightPixels}")
        printMatrix("onTouchImageView :: ")

        return when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                movementMode = MovementType.CLICK

                lastActionDownDate = SystemClock.elapsedRealtime()

                lastEventX = event.x
                lastEventY = event.y

//                Timber.d("  onTouchImageView ACTION_DOWN $lastEventX $lastEventY")
                true
            }
            MotionEvent.ACTION_UP -> {
                val deltaTime = SystemClock.elapsedRealtime()-lastActionDownDate
                Timber.d("onTouchImageView :: deltaTime=$deltaTime")
                if (deltaTime<clickDelay && movementMode == MovementType.CLICK) {
                    // It's a click !
                    magnifyImageViewListener?.onMagnifyImageViewClick(magnifyImageViewListenerParam, event.x, event.y)
                }

                movementMode = MovementType.NONE
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                movementMode = MovementType.ZOOM
                val dx = event.getX(1) - event.getX(0)
                val dy = event.getY(1) - event.getY(0)
                fingersDistance0 = sqrt(dx*dx + dy*dy)

//                Timber.d("  onTouchImageView ACTION_POINTER_DOWN fingersDistance0=$fingersDistance0")
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
//                val dx = event.getX(1) - event.getX(0)
//                val dy = event.getY(1) - event.getY(0)
//                val fingersDistance1 = sqrt(dx*dx + dy*dy)
//                Timber.d("  onTouchImageView ACTION_POINTER_UP $fingersDistance0 to $fingersDistance1")
                movementMode = MovementType.NONE
                fingersDistance0 = 0f
                true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                Timber.d("  onTouchImageView ACTION_OUTSIDE actionMasked=${event.actionMasked}")
                movementMode = MovementType.NONE
                resetImage()
                true
            }
            MotionEvent.ACTION_MOVE -> {
/*                if (!isImageModified) {
                    // Save important informations before any modification
                    oldScaleType = scaleType

                    val f = FloatArray(9)
                    imageMatrix.getValues(f)

                    width0 = (width - 2*f[Matrix.MTRANS_X])/f[Matrix.MSCALE_X]
                    height0 = (height - 2*f[Matrix.MTRANS_Y])/f[Matrix.MSCALE_Y]
                    currentScale = 1f

                    isImageModified = true
                }*/

                if (movementMode == MovementType.CLICK || movementMode == MovementType.DRAG) {
                    val x1 = event.x
                    val y1 = event.y
                    val dx = x1-lastEventX
                    val dy = y1-lastEventY
                    val dist = sqrt(dx*dx + dy*dy)
//                    Timber.d("   movementMode = $movementMode   dist=$dist dx=$dx dy=$dy")

                    if (movementMode == MovementType.CLICK && dist > 8) {
                        // It's not a CLICK, but a DRAG !
                        movementMode = MovementType.DRAG
                    }

                    if (movementMode == MovementType.DRAG) {
                        if (currentScale > 1) {
                            checkAndUpdateImageByDelta(dx, dy, 0f, 0f, 0f)
                        }
                    }
                    lastEventX = event.x
                    lastEventY = event.y

                } else if (movementMode == MovementType.ZOOM){
                    lastActionDownDate = 0

                    val dx = event.getX(1) - event.getX(0)
                    val dy = event.getY(1) - event.getY(0)
                    val fingersDistance1 = sqrt(dx*dx + dy*dy)
                    var ratio = if (fingersDistance0 != 0f) fingersDistance1/fingersDistance0 else 0f

                    var newScale = currentScale*ratio
//                    Timber.d("       ratio=$ratio  newScale=$newScale  currentScale=$currentScale fingersDistance1=$fingersDistance1   fingerDist0=$fingersDistance0")
                    if (newScale>maxZoom) {
                        ratio = maxZoom/currentScale
                        newScale = maxZoom
                    }

                    if (newScale<minZoom) {
                        ratio = minZoom/currentScale
                        newScale = minZoom
                    }

                    if (ratio != 1f) {
                        checkAndUpdateImageByDelta(0f, 0f, ratio, event.getX(0) + dx / 2, event.getY(0) + dy / 2)
                        currentScale = newScale
                        fingersDistance0 = fingersDistance1
                    }

//                    Timber.d("  onTouchImageView ACTION_MOVE :: ZOOM $fingersDistance0 to $fingersDistance1 => $ratio    $currentScale $newScale")
                }
                true
            }
            else -> false
        }
    }

    fun getDisplayOption() = displayOption
    fun setDisplayOption(d:DisplayOption) {
        Timber.v("setDisplayOption($d)")

//        if (d != displayOption) {
            displayOption = d

            // Update image
            if (firstDrawCompleted)
                applyDisplayOption()
//        }
    }

    private fun applyDisplayOption() {
        Timber.v("applyDisplayOption $displayOption :: ")
        Timber.v("    imagePath=$imagePath")
        Timber.v("    width0=$width0 height0=${height0}")
        Timber.v("    App.physicalConstants.metrics.widthPixels=${App.physicalConstants.metrics.widthPixels} App.physicalConstants.metrics.heightPixels=${App.physicalConstants.metrics.heightPixels}")


        var ratio = 1f
        var dx = 0f
        var dy = 0f

        when (displayOption) {
            DisplayOption.FULL -> {
                ratio = 1f
                dx = (width0-App.physicalConstants.metrics.widthPixels) / 2f
                dy = (height0-App.physicalConstants.metrics.heightPixels) / 2f

                isImageModified = false

            }
            DisplayOption.MAXIMIZE_WIDTH -> {
                ratio = App.physicalConstants.metrics.widthPixels / width0
                dx = 0f
                dy = ((height0*ratio)-App.physicalConstants.metrics.heightPixels) / 2f

                isImageModified = true
            }
            DisplayOption.MAXIMIZE_HEIGHT -> {
                ratio = App.physicalConstants.metrics.heightPixels / height0
                dx = ((width0*ratio)-App.physicalConstants.metrics.widthPixels) / 2f
                dy = 0f

                isImageModified = true
            }
        }
        Timber.v("applyDisplayOption :: temp ratio = $ratio dx=$dx dy=$dy")

        dx = Math.min(dx, 0f)
        dy = Math.min(dy, 0f)

        currentScale = ratio
        fingersDistance0 = 0f

        Timber.v("applyDisplayOption :: ratio = $ratio dx=$dx dy=$dy")
        checkAndUpdateImageByValues(dx, dy, ratio, 0f, 0f)
    }

    /**
     * Reset the image to its initial state
     */
    fun resetImage() {
        Timber.i("resetImage $imagePath")

        imagePath = ""
        firstDrawCompleted = false

        if (isImageModified) {
            imageMatrix = Matrix()
            scaleType = oldScaleType
            currentScale = 1f
            fingersDistance0 = 0f

            isImageModified = false

            displayOption = DisplayOption.FULL
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply the DisplayOption the first time this image is drawn
        if (!firstDrawCompleted && imagePath != "") {
            Timber.v("onDraw :: firstDrawCompleted = true $imagePath")
            firstDrawCompleted = true

            // Save values
            if (!isImageModified) {
                oldScaleType = scaleType

                val f = FloatArray(9)   // Get matrix
                imageMatrix.getValues(f)

                width0 = (width - 2 * f[Matrix.MTRANS_X]) / f[Matrix.MSCALE_X]
                height0 = (height - 2 * f[Matrix.MTRANS_Y]) / f[Matrix.MSCALE_Y]
                currentScale = f[Matrix.MTRANS_X]

                isImageModified = true
            }

            // Apply the displayOption
            setDisplayOption(displayOption)
        }
    }

    fun setMagnifyImageViewListener(l: Listener?, param:Any?=null) {
        magnifyImageViewListener = l
        magnifyImageViewListenerParam = param
    }

}
