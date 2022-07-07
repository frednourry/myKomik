package fr.nourry.mynewkomik.pictureslider

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import timber.log.Timber


// To work with a androidx.viewpager.widget.ViewPager
class PageSliderAdapter(context: Context, val viewModel:PictureSliderViewModel, var comic:ComicEntry):PagerAdapter(), View.OnClickListener {

    data class InnerComicTag(val comic:ComicEntry, val position:Int, val imageView:ImageView)

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
        comic = newComic
        this.notifyDataSetChanged()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        Timber.d("instantiateItem :: position=$position")
        val view = inflater.inflate(R.layout.item_page, container, false)

        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val cardView= view.findViewById<CardView>(R.id.cardView)
        cardView.tag = InnerComicTag(comic, position, imageView)
        val myCardView = MyCardView(cardView)

        cardView.setOnClickListener(this@PageSliderAdapter)


        Glide.with(imageView)
            .load(R.drawable.ic_launcher_foreground)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(imageView)
        container.addView(view, 0)

        // Ask the ComicLoadingManager to find this page path
        ComicLoadingManager.getInstance().loadComicPages(comic, myCardView, position, 1)
/*
        view.setOnClickListener { _ ->
            Timber.v("onClick on $position ${cardView.tag}")
        }*/

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
        return POSITION_NONE        // TODO : to adapte when changing the comic, because the loaded items are not updated (https://stackoverflow.com/questions/7263291/why-pageradapternotifydatasetchanged-is-not-updating-the-view)
    }

    override fun onClick(view: View) {
        Timber.v("onClick")
        val innerComic = view.tag as InnerComicTag
        viewModel.showPageSelector(innerComic.position)
    }
}

private fun ImageView.onTouchEvent(function: () -> Unit) {
    Timber.d("onTouchEvent")
}
