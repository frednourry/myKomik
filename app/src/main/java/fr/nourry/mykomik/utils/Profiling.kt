package fr.nourry.mykomik.utils

import android.util.Log

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
            Log.i(TAG,"last measure aborted : $label")
        }

        start = System.currentTimeMillis()
        Log.i(TAG,"START $label")
    }

    fun intermediate(l:String="") {
        val i = System.currentTimeMillis()
        if (l == "")
            Log.i(TAG,"  INTERMEDIATE $label - elapse time : ${i - start} ms")
        else
            Log.i(TAG,"  INTERMEDIATE $l - elapse time : ${i - start} ms")
    }

    fun stop() {
        end = System.currentTimeMillis()
        Log.i(TAG,"END $label - elapse time : ${end - start} ms")
        start = 0L
        end = 0L
    }
}