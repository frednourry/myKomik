package fr.nourry.mynewkomik.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
import kotlinx.android.synthetic.main.item_comic.view.*
import timber.log.Timber
import java.io.File


class BrowserAdapter(private val comics:List<Comic>, private val listener:OnComicAdapterListener?):RecyclerView.Adapter<BrowserAdapter.ViewHolder>(), View.OnClickListener, View.OnLongClickListener, ComicLoadingProgressListener {
    interface OnComicAdapterListener {
        fun onComicClicked(comic: Comic, position:Int)
        fun onComicLongClicked(comic: Comic, position:Int)
        fun onComicSelected(list:ArrayList<Int>)
    }

    private var showFilterMode = false
    private var arrCheckedItems:MutableList<Int> = ArrayList(0)

    data class InnerComic(val comic:Comic, val position:Int, var checked:Boolean)

    fun setFilterMode(bFilter:Boolean, selectedPosition:ArrayList<Int>?) {
        if (bFilter != showFilterMode) {
            showFilterMode = bFilter

            // Reset each time !
            if (showFilterMode) {
                arrCheckedItems.clear()
                if (selectedPosition != null)
                    arrCheckedItems.addAll(selectedPosition)
                listener?.onComicSelected(arrCheckedItems as ArrayList<Int>)
            }

            this.notifyDataSetChanged()
        }
    }

    class ViewHolder(var itemView: View): RecyclerView.ViewHolder(itemView) {
        val cardView = itemView.findViewById<CardView>(R.id.cardView)!!
        var imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        val textView = itemView.findViewById<TextView>(R.id.textView)!!
        val checkBox = itemView.findViewById<CheckBox>(R.id.checkBox)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comic, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comic = comics[position]
        val innerComic = InnerComic(comic, position, false)

        val comicAdapter = this
        with (holder) {
            cardView.tag = innerComic
            cardView.setOnClickListener(this@BrowserAdapter)
            cardView.setOnLongClickListener(this@BrowserAdapter)
            textView.text = comic.file.name
            if (showFilterMode) {
                checkBox.isChecked = arrCheckedItems.indexOf(position)>=0
                checkBox.visibility = View.VISIBLE
            } else
                checkBox.visibility = View.INVISIBLE

            if (comic.file.isFile) {
                Glide.with(imageView.context)
                    .load(R.drawable.ic_launcher_foreground)
                    .into(imageView)
                ComicLoadingManager.getInstance().loadComicInImageView(comic, holder, comicAdapter)
            } else {
                Glide.with(imageView.context)
                    .load(R.drawable.ic_launcher_foreground)
                    .into(imageView)
                ComicLoadingManager.getInstance().loadComicDirectoryInImageView(comic, holder, comicAdapter)

            }
        }
    }

    override fun getItemCount(): Int = comics.size

    override fun onClick(v: View) {
        Timber.v("onClick showFilterMode=$showFilterMode")

        val innerComic = v.tag as InnerComic
        if (showFilterMode) {
            v.checkBox.isChecked = !v.checkBox.isChecked
            if (v.checkBox.isChecked)
                arrCheckedItems.add(innerComic.position)
            else
                arrCheckedItems.remove(innerComic.position)
            listener?.onComicSelected(arrCheckedItems as ArrayList<Int>)

            Timber.v("  arrCheckedItems = $arrCheckedItems")
        } else {
            listener?.onComicClicked(innerComic.comic, innerComic.position)
        }
    }

    fun selectAll() {
        arrCheckedItems.clear()
        for (cpt in 0..comics.size-1) {
            arrCheckedItems.add(cpt)
        }
        notifyDataSetChanged()
        listener?.onComicSelected(arrCheckedItems as ArrayList<Int>)
    }

    fun selectNone() {
        arrCheckedItems.clear()
        notifyDataSetChanged()
        listener?.onComicSelected(arrCheckedItems as ArrayList<Int>)
    }

    override fun onLongClick(v: View): Boolean {
        val innerComic = v.tag as InnerComic
        listener?.onComicLongClicked(innerComic.comic, innerComic.position)
        return true
    }


    override fun onProgress(currentIndex: Int, size: Int) {
    }

    override fun onFinished(result: ComicLoadingResult, target:Any?, comic:Comic, path: File?) {
        Timber.d("onFinished ${comic.file} $path" )
        if (result == ComicLoadingResult.SUCCESS && target!= null && path != null && path.absolutePath != "" && path.exists()) {
            // Check if the target is still waiting this image
            val holder = target as ViewHolder
            val cardView = holder.cardView
            val holderInnerComic = cardView.tag as InnerComic
            val holderComic = holderInnerComic.comic

            if (holderComic.file.absolutePath == comic.file.absolutePath) {
                val image = holder.imageView
                Glide.with(image.context)
                    .load(path)
                    .into(image)
            } else {
                Timber.w("onFinished:: To late. This view no longer requires this image...")
            }
        } else {
            Timber.w("onFinished:: $result")
        }
    }

}