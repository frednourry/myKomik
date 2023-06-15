package fr.nourry.mykomik.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.TypedValue
import fr.nourry.mykomik.App
import timber.log.Timber
import java.io.File

class PhysicalConstants(context:Context) {
    var cacheDir: File
    var metrics: DisplayMetrics
    var configuration: Configuration
    var pixelDxRatio: Float
    var density: Float
    var screenlayout:Int

    companion object {
        private lateinit var mInstance: PhysicalConstants

        fun getInstance(): PhysicalConstants = mInstance

        fun newInstance(context:Context):PhysicalConstants {
            if (! ::mInstance.isInitialized) {
                mInstance = PhysicalConstants(context)
            }
            return mInstance
        }
    }

    init {
        val r = context.resources
        cacheDir = context.cacheDir
        metrics = r.displayMetrics
        configuration = r.configuration
        pixelDxRatio = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, metrics)
        density = metrics.density
        screenlayout = configuration.screenLayout
    }

    fun updateMetrics (context:Context) {
        val r = context.resources
        metrics = r.displayMetrics
    }

}