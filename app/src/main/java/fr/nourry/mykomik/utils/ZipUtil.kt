package fr.nourry.mykomik.utils

import java.util.*
import java.util.zip.ZipEntry

class ZipUtil {
    class ZipEntryComparatorByName : Comparator<ZipEntry> {
        override fun compare(zip1: ZipEntry, zip2: ZipEntry): Int {
            return zip1.name.compareTo(zip2.name)
        }
    }

    class SevenZArchiveEntryComparatorByName : Comparator<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry> {
        override fun compare(zip1: org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry, zip2: org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry): Int {
            return zip1.name.compareTo(zip2.name)
        }
    }

    class ZipArchiveEntryComparatorByName : Comparator<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> {
        override fun compare(zip1: org.apache.commons.compress.archivers.zip.ZipArchiveEntry, zip2: org.apache.commons.compress.archivers.zip.ZipArchiveEntry): Int {
            return zip1.name.compareTo(zip2.name)
        }
    }

    companion object {
        fun sortZipEntryList(zipEntries: MutableList<ZipEntry>): MutableList<ZipEntry> {
            Collections.sort(zipEntries, ZipEntryComparatorByName())
            return zipEntries
        }

        fun sortZipArchiveEntry(zipEntries: MutableList<org.apache.commons.compress.archivers.zip.ZipArchiveEntry>): MutableList<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> {
            Collections.sort(zipEntries, ZipArchiveEntryComparatorByName())
            return zipEntries
        }

        fun sortSevenZArchiveEntry(zipEntries: MutableList<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>): MutableList<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry> {
            Collections.sort(zipEntries, SevenZArchiveEntryComparatorByName())
            return zipEntries
        }

    }
}