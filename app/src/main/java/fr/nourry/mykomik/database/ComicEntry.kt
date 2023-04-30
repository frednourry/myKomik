package fr.nourry.mykomik.database

import android.net.Uri
import android.os.Parcelable
import androidx.documentfile.provider.DocumentFile
import androidx.room.*
import fr.nourry.mykomik.utils.getExtension
import fr.nourry.mykomik.utils.stringToHash
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File
import java.net.URLDecoder

@Parcelize
@Entity(tableName = "comic_entry",
        indices = [Index(value = ["tree_uri"], unique = false)])
data class ComicEntry(
    @Ignore
    @IgnoredOnParcel
    var docFile: DocumentFile? = null,
    @Ignore
    var uri: Uri?,
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    @ColumnInfo(name = "tree_uri")
    val parentTreeUriPath: String,
    val name: String,
    val extension: String,
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
    constructor(id:Long, parentTreeUriPath:String, name:String, extension: String, isDirectory:Boolean, hashkey:String, nbPages:Int, currentPage:Int):
            this(null, null, id, parentTreeUriPath, name, extension, isDirectory, hashkey, nbPages, currentPage, false)

//    constructor(uri: Uri, parentUri:Uri, name:String, isDirectory: Boolean, size:Long) : this(null, uri, 0,  parentUri.toString(), name, getExtension(name), isDirectory, stringToHash(uri.toString()), 0, 0)

    // Note : Too long ! Use createFromDocFile(docFile) instead !
/*    constructor(docFile: DocumentFile) : this(docFile, docFile.uri, 0, if (docFile.parentFile == null) docFile.uri.toString() else docFile.parentFile!!.uri.toString(),
        docFile.name!!, getExtension(docFile.name!!), docFile.isDirectory, stringToHash(docFile.uri.toString()), 0, 0, false)
*/

    val path:String get() = if (uri != null) uri.toString() else "emptyPath://$name"
    val lastModified:Long get() = docFile?.lastModified() ?: 0
    val size:Long get() = docFile?.length() ?: 0

    companion object {
        fun createFromDocFile(docFile: DocumentFile): ComicEntry {
//            Profiling.getInstance().start("createFromDocFile ${docFile.name}")

            val uri: Uri = docFile.uri
//            Profiling.getInstance().intermediate("uri = $uri")

            val uriString = uri.toString()
            val hash = stringToHash(uriString)
//            Profiling.getInstance().intermediate("hash")

            val parentUriTree = if (docFile.parentFile == null) uriString else docFile.parentFile!!.uri.toString()
//            Profiling.getInstance().intermediate("parentUriTree")

            val pos1 = uriString.lastIndexOf("%2F")
            val pos2 = uriString.lastIndexOf(File.separator)
            var name = uriString.substring(Math.max(pos1, pos2)+3)
            name = URLDecoder.decode(name, "utf-8")
//            Profiling.getInstance().intermediate("name = $name")

//            val tempName = docFile.name       // TOO LONG ! sometimes 20 ms...
//            val name = tempName ?: ""
//            Profiling.getInstance().intermediate("name")

            val extension = getExtension(name)
//            Profiling.getInstance().intermediate("extension")

            val isDirectory = docFile.isDirectory                   // VERY LONG ! can be 11 ms sometimes...
//            Profiling.getInstance().intermediate("isDirectory")

            val comic = ComicEntry(docFile, uri, 0,  parentUriTree, name, extension, isDirectory, hash, 0, 0, false)
//            Profiling.getInstance().stop()

            return comic
        }
    }
    fun setDocumentFile(df:DocumentFile?) {
        if (df != null) {
            docFile = df
        }
    }

    override fun equals(other: Any?): Boolean {
        return this.hashkey == (other as ComicEntry).hashkey
    }

    // Generated function...
    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + parentTreeUriPath.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + hashkey.hashCode()
        result = 31 * result + nbPages
        result = 31 * result + currentPage
        result = 31 * result + fromDAO.hashCode()
        return result
    }
}
