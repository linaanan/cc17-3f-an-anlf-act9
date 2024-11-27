package com.example.flightsearch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.flightsearch.data.model.Favorite

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite")
    fun getAllFavorites(): List<Favorite>

    @Insert
    fun insertFavorite(favorite: Favorite)

    @Query("DELETE FROM favorite WHERE departure_code = :departure_code AND destination_code = :destination_code")
    fun deleteFavorite(departure_code: String, destination_code: String): Int

    @Query("SELECT COUNT(*) FROM favorite WHERE departure_code = :departureCode AND destination_code = :destinationCode")
    fun isFavorite(departureCode: String, destinationCode: String): Int
}



