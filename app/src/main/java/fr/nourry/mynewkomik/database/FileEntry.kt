package fr.nourry.mynewkomik.database

import androidx.room.*
import fr.nourry.mynewkomik.utils.stringToHash
import java.io.File

@Entity(tableName = "file_entry",
        indices = [Index(value = ["path"], unique = true)])
data class FileEntry(
//    @Ignore
//    val file: File,
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val parent_id: Long,
    val path: String,
    val name: String,
    @ColumnInfo(name = "is_directory")
    val isDirectory:Boolean,
    var hashkey: String="",
    @ColumnInfo(name = "nb_pages")
    var nbPages: Int=0,
    @ColumnInfo(name = "current_page")
    var currentPage: Int=0
) {
/*    constructor(f: File) : this(f, 0, 0, f.absolutePath, f.name, f.isDirectory, stringToHash(f.absolutePath)) {
        // TODO : find parent id here?
    }
    constructor(f: File, parentId:Long) : this(f, 0, parentId, f.absolutePath, f.name, f.isDirectory, stringToHash(f.absolutePath)) {
    }*/
    init {
//        hashkey = stringToHash(path)
    }
}
