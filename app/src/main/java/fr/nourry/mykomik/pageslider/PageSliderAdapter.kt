package fr.nourry.mykomik.pageslider

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.viewpager.widget.PagerAdapter
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.loader.ComicLoadingProgressListener
import fr.nourry.mykomik.utils.BitmapUtil
import android.util.Log
import java.io.File

enum class MovementType {
    NONE,
    CLICK,
    DRAG,
    ZOOM
}

// To work with a androidx.viewpager.widget.ViewPager
class PageSliderAdapter(val context: Context, var comic:ComicEntry, private val isLTR:Boolean, private val isAdaptPageBackgroundAuto:Boolean):PagerAdapter(), MagnifyImageView.Listener {
    companion object {
        const val TAG = "PageSliderAdapter"
    }

    interface Listener {
        fun onPageTap(imageView:MagnifyImageView, currentPage:Int, x:Float, y:Float)
        fun onPageDrag(dx:Float, dy:Float)
    }

    private var imageViewModified: MagnifyImageView? = null
    private var pageSliderAdapterListener:Listener? = null

    private var displayOption = DisplayOption.FULL

    data class InnerComicTag(val comic:ComicEntry, val position:Int, val imageView:MagnifyImageView, val placeHolderView: ImageView)

    inner class MyCardView(private val cardView:CardView):ComicLoadingProgressListener {

        fun getCardView() = cardView

        // Called when the  ComicLoadingManager has an image ready (an image path) for this CardView
        override fun onRetrieved(comic: ComicEntry, currentIndex: Int, size: Int, path: String) {
            Log.d(TAG,"onRetrieved:: currentIndex=$currentIndex size=$size path=$path currentPage=$currentPage")
            if ((path != "")) {
                val magnifyImageView:MagnifyImageView?
                val holderInnerComic = cardView.tag as InnerComicTag
                val holderComic = holderInnerComic.comic
                Log.d(TAG,"     holderInnerComic.position=${holderInnerComic.position}")

                // Check if the target is still waiting this image
                if (holderComic.path == comic.path && currentIndex == holderInnerComic.position) {
                    Log.d(TAG,"     UPDATING IMAGEVIEW... $path")

                    // Load the image
                    val bitmap = BitmapUtil.decodeStream(File(path), App.physicalConstants.metrics.widthPixels, App.physicalConstants.metrics.heightPixels, isSimplifyBitmapConfig = true)
                    if (bitmap != null) {
                        magnifyImageView = holderInnerComic.imageView
                        magnifyImageView.imagePath = path
                        magnifyImageView.setDisplayOption(displayOption)
                        magnifyImageView.setImageBitmap(bitmap)

                        magnifyImageView.visibility = View.VISIBLE

                        // Hide the placeHolder
                        val placeHolder = holderInnerComic.placeHolderView
                        placeHolder.visibility = View.INVISIBLE

                        if (currentIndex == currentPage-1) {
                            Log.v(TAG,"MAGNET !! (onRetrieved) currentIndex=$currentIndex currentPage=$currentPage ")
                            magnifyImageView?.magnetRight()
                        }
                        // Set the background color according a pixel
                        if (isAdaptPageBackgroundAuto) {
                            magnifyImageView.setBackgroundColor(BitmapUtil.getAverageColorAtHBorder(bitmap, 100))
                        }
                    }
                } else {
                    Log.w(TAG,"onRetrieved:: To late. This view no longer requires this image...")
                }
            }
        }
    }

    private val inflater = LayoutInflater.from(context)

    private var currentPage = 0
    fun resetCurrentPage(n:Int) {
        currentPage = n
    }

    private var allowParentToScrollLeft = true
    private var allowParentToScrollRight = true
    public fun allowParentToScroll(dx:Int) : Boolean {
        Log.v(TAG,"allowParentToScroll($dx)   allowParentToScrollLeft=$allowParentToScrollLeft allowParentToScrollRight=$allowParentToScrollRight")
        if (dx > 0) return allowParentToScrollLeft
        if (dx < 0) return allowParentToScrollRight
        return allowParentToScrollLeft || allowParentToScrollRight
    }

    fun setDisplayOption(d:DisplayOption, isLocked:Boolean, updateAll:Boolean) {
        Log.i(TAG,"setDisplayOption($d, $isLocked, $updateAll)")

        if (updateAll) {
            // Update every images already loaded
            Log.i(TAG,"  setDisplayOption($d) => notifyDataSetChanged()")
            displayOption = d
            notifyDataSetChanged()
        } else {
            // Update only this image
            imageViewModified?.setDisplayOption(d)
        }
    }

    fun setNewComic(newComic:ComicEntry) {
        Log.d(TAG,"setNewComic :: newComic=$newComic nbPage=${newComic.nbPages}")
        onPageChanged(newComic.currentPage)
        comic = newComic
        this.notifyDataSetChanged()
    }

    fun setPageSliderAdapterListener(l: Listener?) {
        pageSliderAdapterListener = l
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        Log.d(TAG,"instantiateItem :: position=$position")
        val view = inflater.inflate(R.layout.item_page, container, false)

        if (!isLTR) view.rotationY = 180F

        val magnifyImageView = view.findViewById<MagnifyImageView>(R.id.imageView)
        val placeHolderView = view.findViewById<ImageView>(R.id.placeHolderView)
        val cardView= view.findViewById<CardView>(R.id.cardView)
        cardView.tag = InnerComicTag(comic, position, magnifyImageView, placeHolderView)
        val myCardView = MyCardView(cardView)

        magnifyImageView.setLTR(isLTR)

        cardView.setOnTouchListener { view, motionEvent ->
            imageViewModified = magnifyImageView
           onTouch(view, motionEvent)
        }

        magnifyImageView.setMagnifyImageViewListener(this, cardView)

        placeHolderView.visibility = View.VISIBLE
        magnifyImageView.visibility = View.INVISIBLE

        container.addView(view, 0)

        // Ask the ComicLoadingManager to find this page path
        ComicLoadingManager.getInstance().loadComicPages(comic, myCardView, position, 1)

        return view
    }

    override fun getCount(): Int {
        return comic.nbPages
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun getItemPosition(`object`: Any): Int {
//        return super.getItemPosition(`object`)
        return POSITION_NONE        // TODO? To adapt when changing the comic, because the loaded items are not updated (https://stackoverflow.com/questions/7263291/why-pageradapternotifydatasetchanged-is-not-updating-the-view)
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        return try {
            val cardView:CardView = view as CardView
            val imageView = cardView.findViewById<MagnifyImageView>(R.id.imageView)
            imageView.onTouchImageView(event)
        } catch(e:Exception) {
            Log.d(TAG,"onTouch::  error = ${e.printStackTrace()}")
            false
        }
    }

    fun onPageChanged(newPageIndex:Int) {
        Log.d(TAG,"onPageChanged($newPageIndex) this.currentPage=${this.currentPage}")
        // Reset the last image when quitting it (the last image index is this.currentPage)
        imageViewModified?.resetDisplayOption()

        if (this.currentPage == newPageIndex-1) {
            Log.v(TAG,"MAGNET !! (onPageChanged($newPageIndex)) ${this.currentPage}::${imageViewModified?.imagePath}")
            imageViewModified?.magnetRight()
        }

        // Update this.currentPage
        this.currentPage = newPageIndex
    }

    override fun onMagnifyImageViewClick(param: Any?, x:Float, y:Float) {
        try {
            val cardView = param as CardView
            val innerComic = cardView.tag as InnerComicTag
            innerComic.imageView
            pageSliderAdapterListener?.onPageTap(innerComic.imageView, innerComic.position, x, y)
        } catch (e:Exception) {
            Log.w(TAG,"onMagnifyImageViewClick :: error = "+e.printStackTrace())
        }
    }

    override fun onMagnifyImageDrag(dx: Float, dy: Float, allowParentToScrollLeft: Boolean, allowParentToScrollRight: Boolean) {
        Log.d(TAG,"onMagnifyImageDrag::  dx=$dx dy=$dy allowParentToScrollLeft=$allowParentToScrollLeft allowParentToScrollRight=$allowParentToScrollRight")
        this.allowParentToScrollLeft = allowParentToScrollLeft
        this.allowParentToScrollRight = allowParentToScrollRight
        pageSliderAdapterListener?.onPageDrag(dx, dy)
    }
}
