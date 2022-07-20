package fr.nourry.mynewkomik.pageslider

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import timber.log.Timber


enum class MovementType {
    NONE,
    CLICK,
    DRAG,
    ZOOM
}

// To work with a androidx.viewpager.widget.ViewPager
class PageSliderAdapter(context: Context, private val viewModel:PageSliderViewModel, var comic:ComicEntry):PagerAdapter(), MagnifyImageViewListener {

    private var imageViewModified: MagnifyImageView? = null

    data class InnerComicTag(val comic:ComicEntry, val position:Int, val imageView:MagnifyImageView)

    inner class MyCardView(private val cardView:CardView):ComicLoadingProgressListener {

        override fun onRetrieved(comic: ComicEntry, currentIndex: Int, size: Int, path: String) {
            Timber.d("onRetrieved:: currentIndex=$currentIndex size=$size path=$path")
            if ((path != "")) {
                val holderInnerComic = cardView.tag as InnerComicTag
                val holderComic = holderInnerComic.comic
                Timber.d("     holderInnerComic.position=${holderInnerComic.position}")

                // Check if the target is still waiting this image
                if (holderComic.file.absolutePath == comic.file.absolutePath && currentIndex == holderInnerComic.position) {
                    Timber.d("     UPDATING IMAGEVIEW... $path")
                    val image = holderInnerComic.imageView
                    Glide.with(image.context)
                        .load(path)
                        .fitCenter()
                        .into(image)
                } else {
                    Timber.w("onRetrieved:: To late. This view no longer requires this image...")
                }
            }
        }
    }

    private val inflater = LayoutInflater.from(context)

    fun setNewComic(newComic:ComicEntry) {
        Timber.d("setNewComic :: newComic=$newComic nbPage=${newComic.nbPages}")
        onPageChanged()
        comic = newComic
        this.notifyDataSetChanged()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        Timber.d("instantiateItem :: position=$position")
        val view = inflater.inflate(R.layout.item_page, container, false)

        val magnifyImageView = view.findViewById<MagnifyImageView>(R.id.imageView)
        val cardView= view.findViewById<CardView>(R.id.cardView)
        cardView.tag = InnerComicTag(comic, position, magnifyImageView)
        val myCardView = MyCardView(cardView)

        cardView.setOnTouchListener { view, motionEvent ->
            imageViewModified = magnifyImageView
           onTouch(view, motionEvent)
        }

        magnifyImageView.setMagnifyImageViewListener(this, cardView)

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
            imageView.onTouchImageView(event)
        } catch(e:Exception) {
            Timber.d("onTouch::  error = ${e.printStackTrace()}")
            false
        }
    }

    fun onPageChanged() {
        imageViewModified?.resetImage()
        imageViewModified = null
    }

    override fun onMagnifyImageViewClick(param: Any?) {
        try {
            val cardView = param as CardView
            val innerComic = cardView.tag as InnerComicTag
            viewModel.showPageSelector(innerComic.position)
        } catch (e:Exception) {
            Timber.w("onMagnifyImageViewClick :: error = "+e.printStackTrace())
        }
    }
}
