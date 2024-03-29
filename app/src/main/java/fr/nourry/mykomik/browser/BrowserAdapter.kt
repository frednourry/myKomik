package fr.nourry.mykomik.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import fr.nourry.mykomik.App
import fr.nourry.mykomik.R
import fr.nourry.mykomik.database.ComicEntry
import fr.nourry.mykomik.databinding.ItemComicBinding
import fr.nourry.mykomik.loader.ComicLoadingManager
import fr.nourry.mykomik.loader.ComicLoadingProgressListener
import fr.nourry.mykomik.utils.BitmapUtil
import android.util.Log
import java.io.File


class BrowserAdapter(private val comics:List<ComicEntry>, private val listener:OnComicAdapterListener?):RecyclerView.Adapter<BrowserAdapter.ViewHolder>(), View.OnClickListener, View.OnLongClickListener {
    companion object {
        const val TAG = "BrowserAdapter"
    }

    inner class ViewHolder(binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root), ComicLoadingProgressListener {
        val cardView = binding.cardView
        val imageView = binding.imageView
        val textView = binding.textView
        val imageIconView = binding.imageIconView
        val checkBox = binding.checkBox
        val percentView = binding.percentView

        override fun onRetrieved(comic: ComicEntry, currentIndex: Int, size: Int, path: String) {
            Log.d(TAG,"onRetrieved currentIndex=$currentIndex size=$size path=$path")
            if (path != "" && File(path).exists()) {
                // Check if the target is still waiting this image
                val holderInnerComic = cardView.tag as InnerComicEntry
                val holderComic = holderInnerComic.comic

                if (holderComic.uri == comic.uri) {
                    val bitmap = BitmapUtil.decodeStream(File(path), App.physicalConstants.metrics.widthPixels, App.physicalConstants.metrics.heightPixels)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                } else {
                    Log.i(TAG,"onRetrieved:: To late. This view no longer requires this image...")
                }
            } else {
                Log.i(TAG,"onRetrieved:: empty path ! (do nothing)")
            }
        }
    }

    interface OnComicAdapterListener {
        fun onComicEntryClicked(comic: ComicEntry, position:Int)
        fun onComicEntryLongClicked(comic: ComicEntry, position:Int)
        fun onComicEntrySelected(list:ArrayList<Int>)
    }

    private var showFilterMode = false
    private var arrCheckedItems:MutableList<Int> = ArrayList(0)

    data class InnerComicEntry(val comic:ComicEntry, val position:Int, var checked:Boolean)

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent,false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comic = comics[position]
        val innerComic = InnerComicEntry(comic, position, false)

        with (holder) {
            cardView.tag = innerComic
            cardView.setOnClickListener(this@BrowserAdapter)
            cardView.setOnLongClickListener(this@BrowserAdapter)
            textView.text = comic.name
            if (showFilterMode) {
                checkBox.isChecked = arrCheckedItems.indexOf(position)>=0
                checkBox.visibility = View.VISIBLE
            } else
                checkBox.visibility = View.INVISIBLE

            if (!comic.isDirectory) {
                if (comic.nbPages>0 && !App.isGuestMode) {
                    val percent = (comic.currentPage+1)*100/comic.nbPages
                    percentView.visibility = View.VISIBLE
                    imageIconView.visibility = View.VISIBLE
                    percentView.text = "$percent%"
                    imageIconView.setImageResource(if (percent == 100) R.drawable.ic_baseline_book_closed_24 else R.drawable.ic_baseline_book_open_24)
                } else {
                    percentView.visibility = View.INVISIBLE
                    imageIconView.visibility = View.INVISIBLE
                }
            } else {
                percentView.visibility = View.INVISIBLE
                imageIconView.visibility = View.INVISIBLE
            }

            imageView.setImageResource(R.drawable.ic_launcher_foreground)

            ComicLoadingManager.getInstance().loadComicEntryCover(comic, holder)
        }
    }

    override fun getItemCount(): Int = comics.size

    override fun onClick(v: View) {
        Log.v(TAG,"onClick showFilterMode=$showFilterMode")

        val innerComic = v.tag as InnerComicEntry
        if (showFilterMode) {
            val checkBox = v.findViewById<CheckBox>(R.id.checkBox)!!

            checkBox.isChecked = !checkBox.isChecked
            if (checkBox.isChecked)
                arrCheckedItems.add(innerComic.position)
            else
                arrCheckedItems.remove(innerComic.position)
            listener?.onComicEntrySelected(arrCheckedItems as ArrayList<Int>)

            Log.v(TAG,"  arrCheckedItems = $arrCheckedItems")
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
        val innerComic = v.tag as InnerComicEntry
        listener?.onComicEntryLongClicked(innerComic.comic, innerComic.position)
        return true
    }

}