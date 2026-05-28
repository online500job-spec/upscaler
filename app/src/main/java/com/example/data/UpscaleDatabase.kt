package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UpscaleItem::class], version = 1, exportSchema = false)
abstract class UpscaleDatabase : RoomDatabase() {
    abstract fun upscaleDao(): UpscaleDao

    companion object {
        @Volatile
        private var INSTANCE: UpscaleDatabase? = null

        fun getDatabase(context: Context): UpscaleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UpscaleDatabase::class.java,
                    "upscale_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
