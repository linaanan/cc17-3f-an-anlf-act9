package com.example.flightsearch.data

import androidx.room.Dao
import androidx.room.Query
import com.example.flightsearch.data.model.Airport

@Dao
interface AirportDao {

    @Query("SELECT * FROM airport WHERE iata_code != :departureCode AND :departureCode IN (SELECT iata_code FROM airport) ORDER BY passengers DESC")
    fun getAvailableFlights(departureCode: String): List<Airport>

    @Query("SELECT * FROM airport WHERE name LIKE :query OR iata_code LIKE :query ORDER BY passengers DESC")
    fun getAirports(query: String): List<Airport>

    @Query("SELECT name FROM airport WHERE iata_code = :iataCode LIMIT 1")
    fun getAirportName(iataCode: String): String?
}


