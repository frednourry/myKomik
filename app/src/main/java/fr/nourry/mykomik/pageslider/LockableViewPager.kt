package fr.nourry.mykomik.pageslider

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.viewpager.widget.ViewPager
import timber.log.Timber

/**
 * Override ViewPager to be able to lock the scrolling
 *   The possibility of scrolling is locked when the PageSliderAdapter give its authorisation (by calling PageSliderAdapter.allowParentToScroll(...))
 */

class LockableViewPager : ViewPager {

    constructor(context: Context?) : super(context!!) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {}

    private var lastPosX = 0f

    private var lastResultAllowParentToScroll = false

    private fun isScrollAllowedByAdapter(dx:Int = 0):Boolean {
        if (this.adapter is PageSliderAdapter) {
            return (adapter as PageSliderAdapter).allowParentToScroll(dx)
        }
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        Timber.v("onInterceptTouchEvent : ev=$ev")
        if (ev.action == MotionEvent.ACTION_DOWN) {
            lastPosX = ev.rawX
            lastResultAllowParentToScroll = isScrollAllowedByAdapter(0)
        } else if (ev.action == MotionEvent.ACTION_MOVE){
            val deltaX = ev.rawX-lastPosX
            lastPosX = ev.rawX
            val temp = isScrollAllowedByAdapter(deltaX.toInt())
            if (temp && !lastResultAllowParentToScroll) {
//                Timber.v("    *************** IS NOW ALLOWED **********")
                // Until now, we block the scrolling, but now, it will be allowed
                // Needed to reset ViewPager.mLastMotionX here with the current position,
                // or else the scrolling will start from the position we block the scroll, not the current position (so a jerk will be visible)

                // DIRTY HACK : To reset ViewPager.mLastMotionX, call ViewPager.onTouchEvent() with a MotionEvent.ACTION_DOWN event
                val fakeEvent = ev
                fakeEvent.action = MotionEvent.ACTION_DOWN
                super.onTouchEvent(fakeEvent)
                // END HACK

            } /*else if (!temp && lastResultAllowParentToScroll) {
                Timber.v("    *************** IS NOW NOT ALLOWED **********")
            }*/

            lastResultAllowParentToScroll = temp
            return isScrollAllowedByAdapter(deltaX.toInt()) && super.onInterceptTouchEvent(ev)
        }
        return super.onInterceptTouchEvent(ev)
    }
    override fun canScrollHorizontally(direction: Int): Boolean {
//        Timber.v("canScrollHorizontally : direction=$direction")
        return isScrollAllowedByAdapter(direction) && super.canScrollHorizontally(direction)
    }

    override fun canScroll(v: View?, checkV: Boolean, dx: Int, x: Int, y: Int): Boolean {
//        Timber.v("canScroll : checkV=$checkV dx=$dx x=$x y=$y")
        return isScrollAllowedByAdapter(dx) && super.canScroll(v, checkV, dx, x, y)
    }

    /**
     * Call setCurrentItem() but call PageSliderAdapter.onPageChanged BEFORE (needed for the MAGNET RIGHT functionality)
     */
    fun setCurrentItemCustom(num:Int, smooth:Boolean) {
        (this.adapter as PageSliderAdapter).onPageChanged(num)
        super.setCurrentItem(num, smooth)
    }
}