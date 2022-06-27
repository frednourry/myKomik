package fr.nourry.mynewkomik.database

import android.os.Parcelable
import androidx.room.*
import fr.nourry.mynewkomik.utils.concatPath
import fr.nourry.mynewkomik.utils.stringToHash
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File

@Parcelize
@Entity(tableName = "comic_entry",
        indices = [Index(value = ["dir_path"], unique = false)])
data class ComicEntry(
    @Ignore
    var file: File,
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    @ColumnInfo(name = "dir_path")
    val dirPath: String,
    val name: String,
    @ColumnInfo(name = "is_directory")
    val isDirectory:Boolean,
    var hashkey: String="",
    @ColumnInfo(name = "nb_pages")
    var nbPages: Int=0,
    @ColumnInfo(name = "current_page")
    var currentPage: Int=0,
    @Ignore
    var fromDAO: Boolean = false
): Parcelable {
    constructor(f: File) : this(f, 0,  if(f.parentFile!=null) f.parentFile.absolutePath else f.absolutePath, f.name, f.isDirectory, stringToHash(f.absolutePath), 0, 0, false)
    constructor(id:Long, dirPath:String, name:String, isDirectory:Boolean, hashkey:String, nbPages:Int, currentPage:Int):
            this(File(concatPath(dirPath, name)), id, dirPath, name, isDirectory, hashkey, nbPages, currentPage, false)

    override fun equals(other: Any?): Boolean {
        return this.hashkey == (other as ComicEntry).hashkey
    }
}
