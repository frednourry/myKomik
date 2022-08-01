package fr.nourry.mykomik.pageslider

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.loader.ComicLoadingProgressListener
import timber.log.Timber


// To work with a viewPager2
class PageSelectorSliderAdapter(val viewModel:PageSliderViewModel, var comic: ComicEntry): RecyclerView.Adapter<PageSelectorSliderAdapter.MyViewHolder>(), View.OnClickListener {

    data class InnerComicTag(val comic:ComicEntry, val position:Int, val imageView:ImageView, val textView:TextView)

    inner class MyCardView(private val cardView:CardView):ComicLoadingProgressListener {

        override fun onRetrieved(comic: ComicEntry, currentIndex: Int, size: Int, path: String) {
            Timber.d("onRetrieved:: currentIndex=$currentIndex size=$size path=$path")
            if (path != "") {
                val holderInnerComic = cardView.tag as InnerComicTag
                val holderComic = holderInnerComic.comic
                val pageNumberTextView = holderInnerComic.textView
                Timber.d("     holderInnerComic.position=${holderInnerComic.position}")
                Timber.d("     cardView=${cardView.width} ${cardView.height}")

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

    class MyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val itemPicture = itemView
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_page_selector, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        Timber.d("onBindViewHolder:: position=$position")

        val cardView = holder.itemPicture.findViewById<CardView>(R.id.cardView)
        val imageView = holder.itemPicture.findViewById<ImageView>(R.id.imageView)
        val pageNumberTextView = holder.itemPicture.findViewById<TextView>(R.id.pageNumberTextView)
        cardView.tag = InnerComicTag(comic, position, imageView, pageNumberTextView)
        pageNumberTextView.text = "${position+1}"
        if (position == viewModel.getCurrentPage()) {
            pageNumberTextView.setBackgroundResource(R.drawable.round_shape_blue)
        } else {
            pageNumberTextView.setBackgroundResource(R.drawable.round_shape_black)
        }

        cardView.setOnClickListener(this@PageSelectorSliderAdapter)

        val myCardView = MyCardView(cardView)

        Glide.with(imageView)
            .load(R.drawable.ic_launcher_foreground)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(imageView)

        // Ask the ComicLoadingManager to find this page path
        ComicLoadingManager.getInstance().loadComicPages(comic, myCardView, position, 1)

/*        imageView.parent.requestDisallowInterceptTouchEvent(true)
        imageView.setOnTouchListener(View.OnTouchListener() { view: View, motionEvent: MotionEvent ->
            Timber.d("--> setOnTouchListener.motionEvent ${motionEvent.action} motionEvent.pointerCount=${motionEvent.pointerCount}")
            false
        })*/

    }

    override fun getItemCount(): Int = comic.nbPages

    override fun onClick(v: View) {
        Timber.d("onClick")
        val innerComic = v.tag as InnerComicTag
        viewModel.onClickPageSelector(innerComic.position)
    }

    fun setNewComic(newComic:ComicEntry) {
        Timber.d("setNewComic :: newComic=$newComic")
        comic = newComic
        this.notifyDataSetChanged()
    }
}
