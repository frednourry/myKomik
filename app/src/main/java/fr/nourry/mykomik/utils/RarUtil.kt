package fr.nourry.mykomik.utils

import com.github.junrar.rarfile.FileHeader
import java.util.*


class RarUtil {
    class FileHeaderComparator : Comparator<FileHeader> {
        override fun compare(header1: FileHeader, header2: FileHeader): Int {
            return header1.fileName.compareTo(header2.fileName)
        }
    }
    companion object {
        fun sortFileHeaderList(fileHeaders: MutableList<FileHeader>): MutableList<FileHeader> {
            Collections.sort(fileHeaders, FileHeaderComparator())
            return fileHeaders
        }

    }
}