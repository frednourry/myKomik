package fr.nourry.mykomik.utils

import timber.log.Timber

class Profiling {
    companion object {
        private var mInstance: Profiling? = null
        private var start = 0L
        private var end = 0L
        private var label : String = ""

        private const val TAG = "Profiling"

        fun getInstance(): Profiling =
            mInstance ?: synchronized(this) {
                val newInstance = mInstance ?: Profiling().also { mInstance = it }
                newInstance
            }
    }

    fun start(l : String = "") {
        label = l

        if (start != 0L) {
            Timber.tag(TAG).i("last measure aborted : $label")
        }

        start = System.currentTimeMillis()
        Timber.tag(TAG).i("START $label")
    }

    fun intermediate(l:String="") {
        val i = System.currentTimeMillis()
        if (l == "")
            Timber.tag(TAG).i("  INTERMEDIATE $label - elapse time : ${i - start} ms")
        else
            Timber.tag(TAG).i("  INTERMEDIATE $l - elapse time : ${i - start} ms")
    }

    fun stop() {
        end = System.currentTimeMillis()
        Timber.tag(TAG).i("END $label - elapse time : ${end - start} ms")
        start = 0L
        end = 0L
    }
}