package fr.nourry.mynewkomik.browser

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import fr.nourry.mynewkomik.Comic
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import timber.log.Timber
import java.io.File


class BrowserAdapter(private val comics:List<Comic>, private val listener:OnComicAdapterListener?):RecyclerView.Adapter<BrowserAdapter.ViewHolder>(), View.OnClickListener, ComicLoadingProgressListener {
    interface OnComicAdapterListener {
        fun onComicClicked(comic: Comic)
    }

    class ViewHolder(var itemView: View): RecyclerView.ViewHolder(itemView) {
        val cardView = itemView.findViewById<CardView>(R.id.cardView)!!
        var imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        val textView = itemView.findViewById<TextView>(R.id.textView)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comic, parent, false)
//            .inflate(R.layout.file_browser_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comic = comics[position]
        val comicAdapter = this
        with (holder) {
            cardView.tag = comic
            cardView.setOnClickListener(this@BrowserAdapter)
            textView.text = comic.file.name

            if (comic.file.isFile) {
                Glide.with(imageView.context)
                    .load(R.drawable.ic_launcher_foreground)
                    .into(imageView)
                ComicLoadingManager.getInstance().loadComicInImageView(comic, holder, comicAdapter)
            } else {
                Glide.with(imageView.context)
//                    .load(ColorDrawable(Color.RED))
//                    .load("/data/user/0/fr.nourry.mynewkomik/cache/3a180874576fe0cbbc9f02697049d60c.png")
//                    .placeholder(ColorDrawable(Color.RED))
                    .load(R.drawable.ic_launcher_foreground)
//                    .load(R.drawable.ic_library_temp)
                    .into(imageView)
                ComicLoadingManager.getInstance().loadComicDirectoryInImageView(comic, holder, comicAdapter)

            }
        }
    }

    override fun getItemCount(): Int = comics.size

    override fun onClick(v: View) {
        listener?.onComicClicked(v.tag as Comic)
    }

    override fun onProgress(currentIndex: Int, size: Int) {
    }

    override fun onFinished(result: ComicLoadingResult, target:Any?, comic:Comic, path: File?) {
        Timber.d("onFinished ${comic.file} $path" )
        if (result == ComicLoadingResult.SUCCESS && target!= null && path != null && path.absolutePath != "" && path.exists()) {
            // Check if the target is still waiting this image
            val holder = target as ViewHolder
            val cardView = holder.cardView
            val holderComic = cardView.tag as Comic

            if (holderComic.file.absolutePath == comic.file.absolutePath) {
                val image = holder.imageView
/*                val d = Drawable.createFromPath(path.absolutePath)
                image.setImageDrawable(d)*/
                Glide.with(image.context)
                    .load(path)
//                    .apply( RequestOptions().override(50, 50)
                    .into(image)
            } else {
                Timber.w("onFinished:: To late. This view no longer requires this image...")
            }
        } else {
            Timber.w("onFinished:: $result")
        }
    }

}