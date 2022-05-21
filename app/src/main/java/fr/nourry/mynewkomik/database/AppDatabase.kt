package fr.nourry.mynewkomik.database

import androidx.room.Database
import androidx.room.RoomDatabase

const val DATABASE_NAME = "databaseMyNewKomic"

@Database(entities = [FileEntry::class], version=1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicEntryDao() : FileEntryDao
}
