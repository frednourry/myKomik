package fr.nourry.mynewkomik.pictureslider

import android.content.Context
import android.database.DataSetObserver
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
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import timber.log.Timber
import java.io.File


// To work with a androidx.viewpager.widget.ViewPager
class PictureSliderAdapter(context: Context, var comic:ComicEntry):PagerAdapter(), ComicLoadingProgressListener {
    private val inflater = LayoutInflater.from(context)

    data class InnerComic(val comic:ComicEntry, val position:Int, val imageView:ImageView)

    fun setNewComic(newComic:ComicEntry) {
        Timber.d("setNewComic :: newComic=$newComic")

        comic = newComic
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        Timber.d("instantiateItem :: position=$position")
        val view = inflater.inflate(R.layout.item_picture, container, false)

        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val cardView = view.findViewById<CardView>(R.id.cardView)
        cardView.tag = InnerComic(comic, position, imageView)

        Glide.with(imageView)
            .load(R.drawable.ic_launcher_foreground)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(imageView)
        container.addView(view, 0)

        ComicLoadingManager.getInstance().loadComicPages(comic, this, position, 1, cardView)

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

    override fun onProgress(currentIndex: Int, size: Int, path:String, target:Any?) {
        Timber.d("onProgress:: currentIndex=$currentIndex size=$size path=$path target=$target")
        if ((target != null) && (path != "")) {
            val cardView = target as CardView
            val holderInnerComic = cardView.tag as InnerComic
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
                Timber.w("onProgress:: To late. This view no longer requires this image...")
            }
        }
    }

    override fun getItemPosition(`object`: Any): Int {
//        return super.getItemPosition(`object`)
        return POSITION_NONE        // TODO : to adapte when changing the comic, because the loaded items are not updated (https://stackoverflow.com/questions/7263291/why-pageradapternotifydatasetchanged-is-not-updating-the-view)
    }

    override fun onFinished(result: ComicLoadingResult, comic: ComicEntry, path: File?, target: Any?) {
        Timber.d("onFinished ${comic.file} $path")
    }
}

private fun ImageView.onTouchEvent(function: () -> Unit) {
    Timber.d("onTouchEvent")
}
