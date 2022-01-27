package fr.nourry.mynewkomik.utils

import android.graphics.Bitmap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

class ComicZipUtil {

    companion object {

        fun unzipFirstImageInFileAndImageView(comicFile: File, path:String/*, imageView: ImageView*/): Boolean {
            ZipFile(comicFile).use { zip->
                val sequence = zip.entries().asSequence()
                Timber.v("NB entry = "+zip.entries().toList().size)
                var bitmap:Bitmap?

                for (entry in sequence) {
                    Timber.v(entry.name + " " + entry.size)
                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                        val input = zip.getInputStream(entry)
                        bitmap = BitmapUtil.createBitmap(
                            input.readBytes(),
                            0,0
                        )
                        if (bitmap != null) {
                            // Reduce the bitmap if needed
                            val bitmapToSave = Bitmap.createScaledBitmap(bitmap, 300, 300, false)

                            // Save the bitmap in cache and return
                            return BitmapUtil.saveBitmapInFile(bitmapToSave, path)
                        }
                    }
                }
            }
            return false
        }

        // Unzip a comic in a directory (better be called in a different Work/Coroutine/...)
        fun unzipInDirectory(comicFile: File, dirPath:String):Boolean {

            Timber.v("BEGIN unzipInDirectory ${comicFile.name}")

            // Clear the directory
            val dir = File(dirPath)
            clearFilesInDir(dir)

            // Unzip
            val zipFileName = comicFile.absoluteFile
            ZipFile(zipFileName).use { zip ->
                val sequence = zip.entries().asSequence()
                Timber.v("NB entry = " + zip.entries().toList().size)
                var bitmap: Bitmap?

                for (entry in sequence) {
                    Timber.v(entry.name + " " + entry.size)

                    if (!entry.isDirectory && isFilePathAnImage(entry.name)) {
                        val input = zip.getInputStream(entry)
                        bitmap = BitmapUtil.createBitmap(input.readBytes())
                        if (bitmap != null) {
                            var name = entry.name
                            val lastSlash = name.lastIndexOf('/')
                            if (lastSlash > 0)
                                name = name.substring(lastSlash + 1)

                            // Save the bitmap in cache
                            BitmapUtil.saveBitmapInFile(bitmap, dirPath+name)
                        }
                    }
                }
            }
            Timber.v("END unzipInDirectory ${comicFile.name}")

            return true
        }
    }
}