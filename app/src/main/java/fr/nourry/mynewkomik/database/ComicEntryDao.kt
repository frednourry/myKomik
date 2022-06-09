package fr.nourry.mynewkomik.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ComicEntryDao {
    /**
     * Usage:
     * val id = dao.insertComicEntry(comicEntry)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertComicEntry(ComicEntry:ComicEntry):Long

    @Update
    /**
     * Usage:
     * val nbRows = dao.updateComicEntry(comic1)
     */
    fun updateComicEntry(ComicEntry: ComicEntry):Int

    @Delete
    /**
     * Usage:
     * val nbRows = dao.deleteComicEntry(comicDB)
     */
    fun deleteComicEntry(ComicEntry:ComicEntry) : Int

    /**
     * Usage:
     * val nbRows = dao.deleteComicEntries(comic1, comic2, comic3)
     */
    @Delete
    fun deleteComicEntries(vararg ComicEntries:ComicEntry) : Int

    /**
     * Usage:
     * dao.getAllComicEntries().observe(this, Observer{ComicEntries-> ... })
     */
    @Query("SELECT * FROM comic_entry")
    fun getAllComicEntries():LiveData<List<ComicEntry>>

    /**
     * Usage:
     * dao.getComicEntriesByDirPath("/sdcard/Download/Comics").observe(this, Observer{ComicEntries-> ... })
     */
    @Query("SELECT * FROM comic_entry WHERE dir_path =:path")
    fun getComicEntriesByDirPath(path:String):LiveData<List<ComicEntry>>
}