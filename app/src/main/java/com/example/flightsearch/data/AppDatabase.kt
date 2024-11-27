package com.example.flightsearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.flightsearch.data.model.Airport
import com.example.flightsearch.data.model.Favorite

@Database(entities = [Airport::class, Favorite::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun airportDao(): AirportDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath("flight_search")
                if (!dbFile.exists()) {
                    Room.databaseBuilder(context, AppDatabase::class.java, "flight_search")
                        .createFromAsset("databases/flight_search.db")
                        .fallbackToDestructiveMigration()
                        .build().also { INSTANCE = it }
                } else {
                    Room.databaseBuilder(context, AppDatabase::class.java, "flight_search")
                        .fallbackToDestructiveMigration()
                        .build().also { INSTANCE = it }
                }
            }
        }

    }
}
