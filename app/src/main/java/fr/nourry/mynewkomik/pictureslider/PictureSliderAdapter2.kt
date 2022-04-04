package fr.nourry.mynewkomik.pictureslider

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.nourry.mynewkomik.ComicPicture
import fr.nourry.mynewkomik.R

// To work with a androidx.viewpager2.widget.ViewPager2
class PictureSliderAdapter2(context: Context, private val pictures: List<ComicPicture>): RecyclerView.Adapter<PictureSliderAdapter2.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picture, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val picture = pictures[position]

        val imageView = holder.itemPicture.findViewById<ImageView>(R.id.imageView)
        Glide.with(imageView)
            .load(picture.file)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(imageView)

/*        imageView.parent.requestDisallowInterceptTouchEvent(true)
        imageView.setOnTouchListener(View.OnTouchListener() { view: View, motionEvent: MotionEvent ->
            Timber.d("--> setOnTouchListener.motionEvent ${motionEvent.action} motionEvent.pointerCount=${motionEvent.pointerCount}")
            false
        })*/

    }

    class MyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val itemPicture = itemView
    }

    override fun getItemCount(): Int = pictures.size
}
