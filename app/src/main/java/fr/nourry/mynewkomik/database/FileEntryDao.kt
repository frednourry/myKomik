package fr.nourry.mynewkomik.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FileEntryDao {
    /**
     * Usage:
     * val id = dao.insertFileEntry(comicEntry)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFileEntry(fileEntry:FileEntry):Long

    /**
     * Usage:
     * val ids = dao.insertFileEntries(comic1, comic2, comic3)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFileEntries(vararg fileEntries:FileEntry) : Array<Long>

    @Update
    /**
     * Usage:
     * val nbRows = dao.updateFileEntry(comic1)
     */
    fun updateFileEntry(fileEntry: FileEntry):Int

    @Delete
    /**
     * Usage:
     * val nbRows = dao.deleteFileEntry(comicDB)
     */
    fun deleteFileEntry(fileEntry:FileEntry) : Int

    /**
     * Usage:
     * val nbRows = dao.deleteFileEntries(comic1, comic2, comic3)
     */
    @Delete
    fun deleteFileEntries(vararg fileEntries:FileEntry) : Int

    /**
     * Usage:
     * dao.getAllFileEntries(3).observe(this, Observer{comicEntries-> ... })
     */
    @Query("SELECT * FROM file_entry")
    fun getAllFileEntries():LiveData<List<FileEntry>>

    /**
     * Usage:
     * dao.getFileEntryById(10).observe(this, Observer{comicEntry-> ... })
     */
    @Query("SELECT * FROM file_entry WHERE id =:id")
    fun getFileEntryById(id:Long):LiveData<FileEntry>

    /**
     * Usage:
     * dao.getFileEntryByPath(10).observe(this, Observer{comicEntry-> ... })
     */
    @Query("SELECT * FROM file_entry WHERE path =:path")
    fun getFileEntryByPath(path:String):LiveData<FileEntry>

    /**
     * Usage:
     * dao.getFileEntriesByFolderId(3).observe(this, Observer{fileEntries-> ... })
     */
    @Query("SELECT * FROM file_entry WHERE id=:folderId")
    fun getFileEntriesByFolderId(folderId:Long): LiveData<List<FileEntry>>

    /**
     * Usage:
     * dao.getFileEntriesByParentPath(3).observe(this, Observer{comicEntries-> ... })
     */
    @Query("SELECT f1.* FROM file_entry f1 LEFT OUTER JOIN file_entry f2 ON f1.id=f2.parent_id WHERE f1.path=:path")
    fun getFileEntriesByParentPath(path:String): LiveData<List<FileEntry>>
}