package fr.nourry.mynewkomik.pictureslider

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.nourry.mynewkomik.ComicPicture
import fr.nourry.mynewkomik.R
import timber.log.Timber


// To work with a androidx.viewpager.widget.ViewPager
class PictureSliderAdapter(context: Context, private val pictures: List<ComicPicture>):PagerAdapter() {
    private val inflater = LayoutInflater.from(context)

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val picture = pictures[position]
        val view = inflater.inflate(R.layout.item_picture, container, false)

        val imageView = view.findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.imageView)
        Glide.with(imageView)
            .load(picture.file)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(imageView)
/*
        imageView.setOnTouchListener(View.OnTouchListener() { view: View, motionEvent: MotionEvent ->
            Timber.d("--> setOnTouchListener.motionEvent ${motionEvent.action}  motionEvent.pointerCount=${motionEvent.pointerCount}")
            true
        })
*/
        container.addView(view, 0)
        return view
    }

    override fun getCount(): Int {
        return pictures.size
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }
}

private fun ImageView.onTouchEvent(function: () -> Unit) {

}
