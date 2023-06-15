package fr.nourry.mykomik.pageslider

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.loader.ComicLoadingProgressListener
import timber.log.Timber


enum class MovementType {
    NONE,
    CLICK,
    DRAG,
    ZOOM
}

// To work with a androidx.viewpager.widget.ViewPager
class PageSliderAdapter(val context: Context, private val viewModel:PageSliderViewModel, var comic:ComicEntry, private val isLTR:Boolean):PagerAdapter(), MagnifyImageView.Listener {
    interface Listener {
        fun onPageTap(imageView:MagnifyImageView, currentPage:Int, x:Float, y:Float)
    }

    private var imageViewModified: MagnifyImageView? = null
    private var pageSliderAdapterListener:Listener? = null

    private var displayOption = DisplayOption.FULL
    private var oldDisplayOptionLocked: Boolean = false
    private var oldDisplayOption = displayOption


    data class InnerComicTag(val comic:ComicEntry, val position:Int, val imageView:MagnifyImageView)

    inner class MyCardView(private val cardView:CardView):ComicLoadingProgressListener {

        // Called when the  ComicLoadingManager has an image ready (an image path) for this CardView
        override fun onRetrieved(comic: ComicEntry, currentIndex: Int, size: Int, path: String) {
            Timber.d("onRetrieved:: currentIndex=$currentIndex size=$size path=$path")
            if ((path != "")) {
                var image:MagnifyImageView? = null
                val holderInnerComic = cardView.tag as InnerComicTag
                val holderComic = holderInnerComic.comic
                Timber.d("     holderInnerComic.position=${holderInnerComic.position}")


                // Check if the target is still waiting this image
                if (holderComic.path == comic.path && currentIndex == holderInnerComic.position) {
                    Timber.d("     UPDATING IMAGEVIEW... $path")
                    image = holderInnerComic.imageView
                    image.imagePath = path
                    image.setDisplayOption(displayOption)
                    Glide.with(image)
                        .load(path)
                        .into(image)
                } else {
                    Timber.w("onRetrieved:: To late. This view no longer requires this image...")
                }
            }
        }
    }

    private val inflater = LayoutInflater.from(context)

    fun setDisplayOption(d:DisplayOption, isLocked:Boolean) {
        Timber.i("setDisplayOption($d)")
//        if (d != displayOption) {
            displayOption = d

        if (isLocked || oldDisplayOptionLocked!= isLocked || oldDisplayOption != displayOption) {
            // Update every images already loaded
            Timber.i("  setDisplayOption($d) => notifyDataSetChanged()")
            notifyDataSetChanged()
        } else {
            // Update only this image
            imageViewModified?.setDisplayOption(d)
        }
        oldDisplayOptionLocked = isLocked
        oldDisplayOption = displayOption
//        }
    }

    fun setNewComic(newComic:ComicEntry) {
        Timber.d("setNewComic :: newComic=$newComic nbPage=${newComic.nbPages}")
        onPageChanged()
        comic = newComic
        this.notifyDataSetChanged()
    }

    fun setPageSliderAdapterListener(l: Listener?) {
        pageSliderAdapterListener = l
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        Timber.d("instantiateItem :: position=$position")
        val view = inflater.inflate(R.layout.item_page, container, false)

        if (!isLTR) view.rotationY = 180F

        val magnifyImageView = view.findViewById<MagnifyImageView>(R.id.imageView)
        val cardView= view.findViewById<CardView>(R.id.cardView)
        cardView.tag = InnerComicTag(comic, position, magnifyImageView)
        val myCardView = MyCardView(cardView)

        cardView.setOnTouchListener { view, motionEvent ->
            imageViewModified = magnifyImageView
           onTouch(view, motionEvent)
        }

        magnifyImageView.setMagnifyImageViewListener(this, cardView)
        magnifyImageView.resetImage()

        Glide.with(magnifyImageView)
            .load(R.drawable.ic_launcher_foreground)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(magnifyImageView)
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
        return POSITION_NONE        // TODO : to adapt when changing the comic, because the loaded items are not updated (https://stackoverflow.com/questions/7263291/why-pageradapternotifydatasetchanged-is-not-updating-the-view)
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        return try {
            val cardView:CardView = view as CardView
            val imageView = cardView.findViewById<MagnifyImageView>(R.id.imageView)
            // TODO use ZoomOption?
            imageView.onTouchImageView(event)
        } catch(e:Exception) {
            Timber.d("onTouch::  error = ${e.printStackTrace()}")
            false
        }
    }

    fun onPageChanged() {
//        imageViewModified?.resetImage()
//        imageViewModified = null
    }
    override fun onMagnifyImageViewClick(param: Any?, x:Float, y:Float) {
        try {
            val cardView = param as CardView
            val innerComic = cardView.tag as InnerComicTag
            innerComic.imageView
            pageSliderAdapterListener?.onPageTap(innerComic.imageView, innerComic.position, x, y)
        } catch (e:Exception) {
            Timber.w("onMagnifyImageViewClick :: error = "+e.printStackTrace())
        }
    }
}
