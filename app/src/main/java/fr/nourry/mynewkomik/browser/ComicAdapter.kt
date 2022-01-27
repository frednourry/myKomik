package fr.nourry.mynewkomik.browser

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
import timber.log.Timber
import android.text.method.ScrollingMovementMethod




class ComicAdapter(private val comics:List<Comic>, private val listener:OnComicAdapterListener?):RecyclerView.Adapter<ComicAdapter.ViewHolder>(), View.OnClickListener {
    interface OnComicAdapterListener {
        fun onComicClicked(comic: Comic)
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val cardView = itemView.findViewById<CardView>(R.id.cardView)!!
        val imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        val textView = itemView.findViewById<TextView>(R.id.textView)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comic, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comic = comics[position]
        with (holder) {
            cardView.tag = comic
            cardView.setOnClickListener(this@ComicAdapter)
            textView.text = comic.file.nameWithoutExtension

            Glide.with(imageView.context)
                .load(R.drawable.ic_launcher_foreground)
//                .load(comic.file)
                .into(imageView)

            if (comic.file.isFile) {
                Timber.d("----------------- BEGIN onBindViewHolder("+position+") ------------------")
                ComicLoadingManager.getInstance().loadComicInImageView(comic, imageView) { imgPath ->
                    Timber.d("-------------------- imgPath=$imgPath")
                    if (imgPath != "") {
                        Glide.with(imageView.context)
                            .load(imgPath)
                            .into(imageView)
                    }
                }
                Timber.d("----------------- END onBindViewHolder("+position+") ------------------")

            }
        }
    }

    override fun getItemCount(): Int = comics.size

    override fun onClick(v: View) {
        listener?.onComicClicked(v.tag as Comic)
    }

}