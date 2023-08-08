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
        // User click
        fun onMagnifyImageViewClick(param:Any?, x:Float, y:Float)

        // User drag
        fun onMagnifyImageDrag(dx:Float, dy:Float, allowParentToScrollLeft:Boolean, allowParentToScrollRight:Boolean)
    }

    companion object {
        private const val defaultMinZoom = 1f        // Min total zoom out
        private const val defaultMaxZoom = 3f        // Max total zoom in
        private const val defaultClickDelay = 200    // Delay between ACTION_DOWN and ACTION_UP to consider this as a click

        // For debug purpose
        fun printFloatArray(f:FloatArray, label:String="printMatrix") {
            Timber.v("$label${f[0]} ${f[1]} ${f[2]} ${f[3]} ${f[4]} ${f[5]} ${f[6]} ${f[7]} ${f[8]}")
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
            } catch (e:Exception) { Timber.e(e.stackTraceToString())
            } finally {
                recycle()
            }
        }
    }

    var imagePath = ""      // For readable debug...

    private var firstDrawDone = false
    private var shouldMagnetRightAfterFirstDraw = false

    private var minZoom = defaultMinZoom
    private var maxZoom = defaultMaxZoom
    private var clickDelay = defaultClickDelay
    private var displayOption = DisplayOption.FULL
    private var hasDisplayOptionChanged = false

    private var magnifyImageViewListener:Listener? = null
    private var magnifyImageViewListenerParam:Any? = null

    private var lastEventX:Float = 0f                   // To remember the last event.x
    private var lastEventY:Float = 0f                   // To remember the last event.y
    private var initialWidth = 0f                       // To remember the real width of the image before any modification
    private var initialHeight = 0f                      // To remember the real height of the image before any modification

    private var fingersDistance0 = 0f                   // To remember the initial distance between the fingers when zooming
    private var oldScaleType = ScaleType.FIT_CENTER     // To remember the initial scaleType
    private var currentScale = 1f                       // The current zoom level
    private var lastActionDownDate = SystemClock.elapsedRealtime()  // To remember when was the last MotionEvent.ACTION_DOWN

    private var isLTR = true                            // To know if we reading Left-To-Right or Right-To-Left

    private var movementMode:MovementType = MovementType.NONE

    var allowParentToScrollLeft = true
    var allowParentToScrollRight = true

    private fun printMatrix(label:String="printMatrix :: ") {
        val f = FloatArray(9)
        imageMatrix.getValues(f)

        printFloatArray(f, label)
    }

    private fun checkParentScrollability() {
        val f = FloatArray(9)
        imageMatrix.getValues(f)
        allowParentToScrollLeft = f[Matrix.MTRANS_X]>=0
        val minTransX = (initialWidth*currentScale) - width
        allowParentToScrollRight = (f[Matrix.MTRANS_X]+minTransX) <= 0
//        Timber.v("checkParentScrollability:: allowParentToScrollLeft=$allowParentToScrollLeft allowParentToScrollRight=$allowParentToScrollRight MTRANS_X=${f[Matrix.MTRANS_X]} iWidth=$initialWidth width=$width scale=$currentScale calc=$minTransX")
    }

    /**
     * Apply given modifications, by checking its values and modify them to respect conditions (MinZoom <= zoom <= MaxZoom) and no unnecessary borders
     *  (deltaX,deltaY): Translation
     *  (deltaScale, centerX, centerY): Zoom on the point defined by (centerX, centerY)
     */
    private fun checkAndUpdateImageByDelta(deltaX:Float, deltaY:Float, deltaScale:Float, centerX: Float, centerY: Float) {
        Timber.v("checkAndUpdateImageByDelta deltaX=$deltaX deltaY=$deltaY deltaScale=$deltaScale centerX=$centerX centerY=$centerY")
        Timber.v("    imagePath=$imagePath")
        var dx = deltaX
        var dy = deltaY

        val f = FloatArray(9)
        imageMatrix.getValues(f)

        val currentWidth = initialWidth*f[Matrix.MSCALE_X]
        val currentHeight = initialHeight*f[Matrix.MSCALE_Y]
        val marginX = width - currentWidth
        val marginY = height - currentHeight

//        Timber.d("     width=${width} height=${height} currentWidth=${currentWidth} currentScale=${currentScale}     cas")
//        Timber.d("     initialWidth=${initialWidth} initialHeight=${initialHeight} currentWidth=${currentWidth}(${currentScale*initialWidth}) currentHeight=${currentHeight}(${currentScale*initialHeight})      cas")
//        Timber.d("     dx=$dx dy=$dy deltaScale=$deltaScale    marginX=$marginX marginY=$marginY    currentWidth=$currentWidth currentHeight=$currentHeight     cas")

        // Check Y borders
        if (f[Matrix.MTRANS_Y]>0 && height>currentHeight) {
            // Center vertically
            val halfMarginY = marginY / 2
            val oldDy = dy
            dy = halfMarginY - f[Matrix.MTRANS_Y]
//            Timber.v("     => cas 1")
        } else if (f[Matrix.MTRANS_Y] + dy > 0) {
            // Check if the top side is really visible
            dy = -f[Matrix.MTRANS_Y]
//            Timber.v("     => cas 2")
        } else if (f[Matrix.MTRANS_Y] + dy < marginY) {
            dy = marginY - f[Matrix.MTRANS_Y]
//            Timber.v("     => cas 3")
        }

        // Check X borders
        if (f[Matrix.MTRANS_X]>0 && width>currentWidth) {
            // Center horizontally
            val halfMarginX = marginX / 2
            val oldDx = dx
            dx = halfMarginX - f[Matrix.MTRANS_X]
//            Timber.v("     => cas 4")
        } else if (f[Matrix.MTRANS_X] + dx > 0) {
            // Check if the top side is really visible
            dx = -f[Matrix.MTRANS_X]
//            Timber.v("     => cas 5")
        } else if (f[Matrix.MTRANS_X] + dx < marginX) {
            dx = marginX - f[Matrix.MTRANS_X]
//            Timber.v("     => cas 6")
        }

        if (dx != 0f || dy != 0f || deltaScale!= 1f) {
            Timber.v("     => dx=$dx dy=$dy deltaScale=$deltaScale <==")
            scaleType = ScaleType.MATRIX
            imageMatrix = Matrix().apply {
                // Scale
                if (deltaScale != 1f) {
                    postScale(deltaScale, deltaScale, centerX, centerY)
                    currentScale *= deltaScale
                }

                // Translate
                if (dx != 0f || dy != 0f) {
                    preTranslate(dx, dy)
                }
                preConcat(imageMatrix)
            }

            invalidate()
        }

        checkParentScrollability()

//        printMatrix("checkAndUpdateImageByDelta :: ")

        hasDisplayOptionChanged = false
    }

    private fun checkAndUpdateImageByValues(posX:Float, posY:Float, scale:Float, centerX: Float, centerY: Float, alignVertically:Boolean = false, alignHorizontally:Boolean = false) {
        Timber.d("checkAndUpdateImageByValues posX=$posX posY=$posY scale=$scale centerX=$centerX centerY=$centerY alignVertically=$alignVertically alignHorizontally=$alignHorizontally")

        scaleType = ScaleType.MATRIX
        imageMatrix = Matrix().apply {
            postScale(scale, scale, centerX, centerY)

            preTranslate(posX, posY)
        }

        if (alignVertically || alignHorizontally) {
            val f = FloatArray(9)
            imageMatrix.getValues(f)
            val dy = if (alignVertically && f[Matrix.MTRANS_Y] != 0f) -f[Matrix.MTRANS_Y] else 0f
            var dx = if (alignHorizontally && f[Matrix.MTRANS_X] != 0f) -f[Matrix.MTRANS_X] else 0f
            if (!isLTR) dx = -dx // Reverse if reading Right-To-Left

            Timber.d("   dx=$dx dy=$dy")

            if (dx != 0f || dy != 0f) {
                imageMatrix = Matrix().apply {
                    // Translate
                    preTranslate(dx, dy)
                    preConcat(imageMatrix)
                }
            }
        }

        invalidate()

        checkParentScrollability()

//        printMatrix("checkAndUpdateImageByValues :: ")
    }


    fun magnetRight() {
        Timber.d("magnetRight $imagePath")

        if (firstDrawDone) {

            var dx = 0f

            val f = FloatArray(9)
            imageMatrix.getValues(f)

            // If this image is larger than width
            if ((initialWidth * currentScale) > width) {
                dx = ((width - (initialWidth * currentScale)))
                if (!isLTR) dx = -dx // Reverse if reading Right-To-Left
            }

            Timber.d("   magnetRight dx=$dx")

            if (dx != 0f) {
                imageMatrix = Matrix().apply {
                    // Translate
                    preTranslate(dx, 0f)
                    preConcat(imageMatrix)
                }
            }

            invalidate()

            checkParentScrollability()

            shouldMagnetRightAfterFirstDraw = false
        } else {
            shouldMagnetRightAfterFirstDraw = true
        }
    }

    /**
     * Ask the imageView to proceed a MotionEvent.
     * Returns TouchResult(isCatch, isClick) where 'isCatch' tells if the event was caught and 'isClick' if the event can be consider as a click
     */
    fun onTouchImageView(event: MotionEvent):Boolean {
        Timber.v("onTouchImageView $displayOption :: initialWidth = $initialWidth initialHeight = $initialHeight currentScale = $currentScale")
/*        Timber.v("onTouchImageView current width=$width height=$height")
        Timber.v("onTouchImageView imagePath=$imagePath")
        Timber.v("onTouchImageView drawable:: ${drawable.dirtyBounds} ${drawable.intrinsicWidth} ${drawable.intrinsicHeight} ${drawable.minimumWidth} ${drawable.minimumHeight}")
        Timber.v("onTouchImageView :: $measuredWidth $measuredHeight")
        Timber.v("onTouchImageView :: App.physicalConstants.metrics :: ${App.physicalConstants.metrics.widthPixels} ${App.physicalConstants.metrics.heightPixels}")
        printMatrix("onTouchImageView :: ")
*/
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
                true
            }
            MotionEvent.ACTION_MOVE -> {
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
                        if (currentScale >= minZoom) {
                            checkAndUpdateImageByDelta(dx, dy, 1f, 0f, 0f)
                        }
                    }
                    lastEventX = event.x
                    lastEventY = event.y

                    magnifyImageViewListener?.onMagnifyImageDrag(dx, dy, allowParentToScrollLeft, allowParentToScrollRight)
                } else if (movementMode == MovementType.ZOOM){
                    lastActionDownDate = 0

                    val dx = event.getX(1) - event.getX(0)
                    val dy = event.getY(1) - event.getY(0)
                    val fingersDistance1 = sqrt(dx*dx + dy*dy)
                    var ratio = if (fingersDistance0 != 0f) fingersDistance1/fingersDistance0 else 0f

                    val newScale = currentScale*ratio
                    Timber.d("       ratio=$ratio  newScale=$newScale  currentScale=$currentScale fingersDistance1=$fingersDistance1")
                    if (newScale>maxZoom) {
                        ratio = maxZoom/currentScale
                    }

                    if (newScale<minZoom) {
                        ratio = minZoom/currentScale
                    }

                    if (ratio != 1f) {
                        checkAndUpdateImageByDelta(0f, 0f, ratio, event.getX(0) + dx / 2, event.getY(0) + dy / 2)
                        fingersDistance0 = fingersDistance1
                    }

//                    Timber.d("  onTouchImageView ACTION_MOVE :: ZOOM $fingersDistance0 to $fingersDistance1 => $ratio    $currentScale $newScale")
                }
                true
            }
            else -> false
        }
    }

    fun setLTR(b:Boolean) {
        isLTR = b
    }

    fun setDisplayOption(d:DisplayOption) {
        Timber.v("setDisplayOption($d)")

        if (displayOption != d) {
            hasDisplayOptionChanged = true
            displayOption = d
        }


        // Update image
        if (firstDrawDone) {
            applyDisplayOption()
        }
    }

    fun resetDisplayOption() {
        Timber.v("resetDisplayOption() $imagePath")
        applyDisplayOption()
        hasDisplayOptionChanged = false
    }

    private fun applyDisplayOption() {
        Timber.v("applyDisplayOption() $displayOption imagePath=$imagePath")
/*        Timber.v("    initialWidth=$initialWidth initialHeight=${initialHeight}")
        Timber.v("    width=$width height=${height} currentScale=$currentScale")
        Timber.v("    App.physicalConstants.metrics.widthPixels=${App.physicalConstants.metrics.widthPixels} App.physicalConstants.metrics.heightPixels=${App.physicalConstants.metrics.heightPixels}")
*/
        var ratio = 1f
        var dx = 0f
        var dy = 0f

        when (displayOption) {
            DisplayOption.FULL -> {
                // Determine the best ratio (according to X or Y)
                val ratioX = width / initialWidth
                val ratioY = height / initialHeight
                ratio = Math.min(ratioX, ratioY)
                Timber.v("    ratioX=$ratioX ratioY=${ratioY} ratio=$ratio")

                dx = (width - (initialWidth*ratio)) / 2f
                dy = (height - (initialHeight*ratio)) / 2f
            }
            DisplayOption.MAXIMIZE_WIDTH -> {
                ratio = width / initialWidth
                dx = 0f
                dy = (height - (initialHeight*ratio)) / 2f
            }
            DisplayOption.MAXIMIZE_HEIGHT -> {
                ratio = height / initialHeight
                dx = (width - (initialWidth*ratio)) / 2f
                dy = 0f
            }
        }

        // Correction of dx and dy according to the ratio
        dx /= ratio
        dy /= ratio

        Timber.v("    initialWidth*ratio=${initialWidth*ratio}   initialHeight*ratio=${initialHeight*ratio}")
        Timber.v("    dx=$dx dy=${dy}")

        currentScale = ratio
        fingersDistance0 = 0f

        // Will the new image height be taller than height?
        val isImageTallerThanHeight = (initialHeight*ratio)>height

        // Will the new image width be larger than width?
        val isImageLargerThanWidth = (initialWidth*ratio)>width

        checkAndUpdateImageByValues(dx, dy, ratio, 0f, 0f, isImageTallerThanHeight, isImageLargerThanWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Timber.v("onSizeChanged:: width=$w ($oldw) height=$h ($oldh) hasDisplayOptionChanged=$hasDisplayOptionChanged $imagePath")

        // This view was rescaled, so need to get the new values
        // Surely the ActionBar that disappeared (when it's appear, it resize the image automatically...)
        //  so need to resize the image if necessary
        if (h > oldh && oldh!= 0) {
            if (hasDisplayOptionChanged) {
                Timber.v("    DO SOMETHING !!")
                val widthF = w.toFloat()
                val heightF = h.toFloat()
                val oldHeightF = oldh.toFloat()
                val deltaScale = heightF / oldHeightF
                val imageSize = initialHeight * currentScale

                Timber.v("    deltaScale = $deltaScale ($heightF/$oldHeightF)  imageSize=$imageSize")

                val f = FloatArray(9)   // Get matrix
                imageMatrix.getValues(f)

                val deltaHeight = (h-oldh).toFloat()
                val posX = f[Matrix.MTRANS_X]
                val posY = f[Matrix.MTRANS_Y]
                val scale = f[Matrix.MSCALE_X]
                Timber.v("    posX=$posX posY=$posY scale=$scale      deltaHeight=$deltaHeight")

                when (displayOption) {
                    DisplayOption.FULL -> {
                        if (posX == 0f) {
                            // Translate the view down
                            checkAndUpdateImageByDelta(0f, deltaHeight, 1f, 0f, 0f)
                        } else {
                            checkAndUpdateImageByDelta(0f, 0f, deltaScale, widthF/2, heightF)
                        }
                    }

                    DisplayOption.MAXIMIZE_WIDTH -> {
                        if (posX == 0f) {
                            // Translate the view down
                            checkAndUpdateImageByDelta(0f, deltaHeight, 1f, 0f, 0f)
                        } else {
                            checkAndUpdateImageByDelta(0f, 0f, deltaScale, widthF/2, heightF)
                        }
                    }
                    DisplayOption.MAXIMIZE_HEIGHT -> {
                        if (imageSize.toInt() != h) {
                            checkAndUpdateImageByDelta(0f, 0f, deltaScale, widthF / 2, heightF)
                        } else {
                            Timber.v("    Do nothing (right height already)")
                        }
                    }
                }
                hasDisplayOptionChanged = false
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply the DisplayOption the first time this image is drawn
        if (!firstDrawDone) {
            // Pass here the first time this view is drawn this view
            Timber.v("onDraw:: 1st width=$width height=$height $imagePath ")

            // Save values
            oldScaleType = scaleType

            // Get the image dimensions
            val f = FloatArray(9)   // Get matrix
            imageMatrix.getValues(f)
            printMatrix("onDraw:: 1st ")

            initialWidth = (width - (2 * f[Matrix.MTRANS_X])) / f[Matrix.MSCALE_X]
            initialHeight = (height - (2 * f[Matrix.MTRANS_Y])) / f[Matrix.MSCALE_Y]
            currentScale = f[Matrix.MSCALE_X]

            // Update minZoom and maxZoom (to be able to return to its values if asked)
            if (minZoom > currentScale) {
                minZoom = currentScale
            }

            if (maxZoom < currentScale)
                maxZoom = currentScale

            firstDrawDone = true

            // Apply the displayOption
            setDisplayOption(displayOption)

            if (shouldMagnetRightAfterFirstDraw)
                magnetRight()
        }
    }

    fun setMagnifyImageViewListener(l: Listener?, param:Any?=null) {
        magnifyImageViewListener = l
        magnifyImageViewListenerParam = param
    }
}
