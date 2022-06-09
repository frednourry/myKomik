package fr.nourry.mynewkomik.database

import androidx.room.Database
import androidx.room.RoomDatabase

const val DATABASE_NAME = "my_new_komik.db"

@Database(entities = [ComicEntry::class], version=1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicEntryDao() : ComicEntryDao
}
