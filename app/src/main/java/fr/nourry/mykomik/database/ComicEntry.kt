package fr.nourry.mykomik.database

import android.net.Uri
import android.os.Parcelable
import androidx.room.*
import fr.nourry.mykomik.utils.stringToHash
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "comic_entry",
        indices = [Index(value = ["parent_uri_path"], unique = false)])
data class ComicEntry(
    @Ignore
    var uri: Uri,
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    @ColumnInfo(name = "parent_uri_path")
    val parentUriPath: String,
    val name: String,
    val extension: String,
    val lastModified:Long,
    val size:Long,
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
    // Real constructor
    constructor(id:Long, parentUriPath:String, name:String, extension: String, lastModified:Long, size:Long, isDirectory:Boolean, hashkey:String, nbPages:Int, currentPage:Int):
            this(Uri.EMPTY, id, parentUriPath, name, extension, lastModified, size, isDirectory, hashkey, nbPages, currentPage, false)

    constructor(uri:Uri, parentUriPath:String, name:String, extension: String, lastModified:Long, size:Long, isDirectory:Boolean):
            this(uri, 0, parentUriPath, name, extension, lastModified, size, isDirectory, stringToHash(uri.toString()), 0, 0, false)

    val path:String get() = uri.toString()

    override fun equals(other: Any?): Boolean {
        return this.hashkey == (other as ComicEntry).hashkey
    }

    // Generated function...
    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + parentUriPath.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + hashkey.hashCode()
        result = 31 * result + nbPages
        result = 31 * result + currentPage
        result = 31 * result + fromDAO.hashCode()
        return result
    }
}
