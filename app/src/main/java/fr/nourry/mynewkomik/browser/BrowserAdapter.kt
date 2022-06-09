package fr.nourry.mynewkomik.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import fr.nourry.mynewkomik.R
import fr.nourry.mynewkomik.database.ComicEntry
import fr.nourry.mynewkomik.databinding.ItemComicBinding
import fr.nourry.mynewkomik.loader.ComicLoadingManager
import fr.nourry.mynewkomik.loader.ComicLoadingProgressListener
import fr.nourry.mynewkomik.loader.ComicLoadingResult
import timber.log.Timber
import java.io.File


class BrowserAdapter(private val comics:List<ComicEntry>, private val listener:OnComicAdapterListener?):RecyclerView.Adapter<BrowserAdapter.ViewHolder>(), View.OnClickListener, View.OnLongClickListener, ComicLoadingProgressListener {
    interface OnComicAdapterListener {
        fun onComicEntryClicked(comic: ComicEntry, position:Int)
        fun onComicEntryLongClicked(comic: ComicEntry, position:Int)
        fun onComicEntrySelected(list:ArrayList<Int>)
    }

    private var showFilterMode = false
    private var arrCheckedItems:MutableList<Int> = ArrayList(0)

    data class InnerComic(val comic:ComicEntry, val position:Int, var checked:Boolean)

    fun setFilterMode(bFilter:Boolean, selectedPosition:ArrayList<Int>?) {
        if (bFilter != showFilterMode) {
            showFilterMode = bFilter

            // Reset each time !
            if (showFilterMode) {
                arrCheckedItems.clear()
                if (selectedPosition != null)
                    arrCheckedItems.addAll(selectedPosition)
                listener?.onComicEntrySelected(arrCheckedItems as ArrayList<Int>)
            }

            this.notifyDataSetChanged()
        }
    }

    inner class ViewHolder(binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root) {
        val cardView = binding.cardView
        val imageView = binding.imageView
        val textView = binding.textView
        val checkBox = binding.checkBox
        val percentView = binding.percentView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent,false))
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

            if (!comic.isDirectory) {
                if (comic.nbPages>0) {
                    percentView.visibility = View.VISIBLE
                    percentView.text = ((comic.currentPage+1)*100/comic.nbPages).toString()+"%"
                } else {
                    percentView.visibility = View.INVISIBLE
                }
            } else {
                percentView.visibility = View.INVISIBLE
            }

            if (!comic.isDirectory) {
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
            val checkBox = v.findViewById<CheckBox>(R.id.checkBox)!!

            checkBox.isChecked = !checkBox.isChecked
            if (checkBox.isChecked)
                arrCheckedItems.add(innerComic.position)
            else
                arrCheckedItems.remove(innerComic.position)
            listener?.onComicEntrySelected(arrCheckedItems as ArrayList<Int>)

            Timber.v("  arrCheckedItems = $arrCheckedItems")
        } else {
            listener?.onComicEntryClicked(innerComic.comic, innerComic.position)
        }
    }

    fun selectAll() {
        arrCheckedItems.clear()
        for (cpt in comics.indices) {
            arrCheckedItems.add(cpt)
        }
        notifyDataSetChanged()
        listener?.onComicEntrySelected(arrCheckedItems as ArrayList<Int>)
    }

    fun selectNone() {
        arrCheckedItems.clear()
        notifyDataSetChanged()
        listener?.onComicEntrySelected(arrCheckedItems as ArrayList<Int>)
    }

    override fun onLongClick(v: View): Boolean {
        val innerComic = v.tag as InnerComic
        listener?.onComicEntryLongClicked(innerComic.comic, innerComic.position)
        return true
    }


    override fun onProgress(currentIndex: Int, size: Int) {
    }

    override fun onFinished(result: ComicLoadingResult, target:Any?, comic:ComicEntry, path: File?) {
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