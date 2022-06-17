package fr.nourry.mynewkomik.utils

import java.util.*
import java.util.zip.ZipEntry

class ZipUtil {
    class ZipEntryComparatorByName : Comparator<ZipEntry> {
        override fun compare(zip1: ZipEntry, zip2: ZipEntry): Int {
            return zip1.name.compareTo(zip2.name)
        }
    }

    companion object {
        fun sortZipEntryList(zipEntries: MutableList<ZipEntry>): MutableList<ZipEntry> {
            Collections.sort(zipEntries, ZipEntryComparatorByName())
            return zipEntries
        }
    }
}