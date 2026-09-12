package com.mahamart.essae.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Plu::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pluDao(): PluDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "essae.db").build()
    }
}
