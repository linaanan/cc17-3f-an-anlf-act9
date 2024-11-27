package com.example.flightsearch

import android.content.Context
import android.database.MatrixCursor
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flightsearch.data.AppDatabase
import com.example.flightsearch.data.model.Favorite
import com.example.flightsearch.ui.FlightAdapter
import com.example.flightsearch.ui.FlightInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class MainActivity : AppCompatActivity() {
    private lateinit var flightAdapter: FlightAdapter
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        restoreSearchText()

        flightAdapter = FlightAdapter(
            onFavoriteToggle = { departureCode, destinationCode ->
                toggleFavorite(departureCode, destinationCode)
            },
            isFavorite = { departureCode, destinationCode ->
                database.favoriteDao().isFavorite(departureCode, destinationCode) > 0
            },
            fetchAirportName = { iataCode ->
                database.airportDao().getAirportName(iataCode)
            }
        )

        val rvFlightList = findViewById<RecyclerView>(R.id.rvFlightList)
        rvFlightList.layoutManager = LinearLayoutManager(this)
        rvFlightList.adapter = flightAdapter

        val svFlight = findViewById<SearchView>(R.id.svFlight)

        svFlight.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    saveSearchText(it)
                    fetchFlights(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    saveSearchText(it)

                    searchJob?.cancel()
                    searchJob = lifecycleScope.launch {
                        delay(300)
                        fetchAirports(it)
                    }
                }
                return true
            }
        })


    }

    private fun saveSearchText(query: String) {
        lifecycleScope.launch {
            applicationContext.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("search_text")] = query
            }
        }
    }

    private fun restoreSearchText() {
        lifecycleScope.launch {
            applicationContext.dataStore.data.map { preferences ->
                preferences[stringPreferencesKey("search_text")] ?: ""
            }.collect { searchText ->
                findViewById<SearchView>(R.id.svFlight).setQuery(searchText, false)
                if (searchText.isBlank()) {
                    loadFavorites()
                } else {
                    fetchAirports(searchText)
                }
            }
        }
    }

    private fun fetchAirports(query: String) {
        if (query.isBlank()) {
            loadFavorites()
            return
        }
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                database.airportDao().getAirports("%$query%")
            }
            val flightFromTextView = findViewById<TextView>(R.id.flightFrom)
            val noResultsTextView = findViewById<TextView>(R.id.noResultsTextView)


            if (results.isNotEmpty()) {
                flightFromTextView.visibility = View.GONE
                noResultsTextView.visibility = View.GONE
            } else {
                flightAdapter.updateData(emptyList())
                flightFromTextView.visibility = View.GONE
            }
            val cursor = MatrixCursor(arrayOf("_id", "name", "iata_code"))
            results.forEachIndexed { index, airport ->
                cursor.addRow(arrayOf(index, airport.name, airport.iata_code))
            }
            updateSearchViewSuggestions(cursor)
        }
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            val favorites = withContext(Dispatchers.IO) {
                database.favoriteDao().getAllFavorites()
            }
            val flightFromTextView = findViewById<TextView>(R.id.flightFrom)
            val noResultsTextView = findViewById<TextView>(R.id.noResultsTextView)

            flightFromTextView.text = getString(R.string.favorite_flights)
            flightFromTextView.visibility = View.VISIBLE

            if (favorites.isNotEmpty()) {
                val flightInfos = favorites.map {
                    FlightInfo(
                        departureCode = it.departure_code,
                        flightIataCode = it.destination_code
                    )
                }
                flightAdapter.updateData(flightInfos)
                noResultsTextView.visibility = View.GONE
            } else {
                flightAdapter.updateData(emptyList())
                noResultsTextView.visibility = View.GONE
                Toast.makeText(this@MainActivity, "No favorite flight routes saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun toggleFavorite(departureCode: String?, destinationCode: String?) {
        if (departureCode.isNullOrBlank() || destinationCode.isNullOrBlank()) {
            Toast.makeText(this, "Invalid route", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val isFavorite = withContext(Dispatchers.IO) {
                    database.favoriteDao().isFavorite(departureCode, destinationCode) > 0
                }

                withContext(Dispatchers.IO) {
                    if (isFavorite) {
                        database.favoriteDao().deleteFavorite(departureCode, destinationCode)
                        Log.d("Favorite", "Removed favorite: $departureCode -> $destinationCode")
                    } else {
                        val favorite = Favorite(id = 0, departure_code = departureCode, destination_code = destinationCode)
                        database.favoriteDao().insertFavorite(favorite)
                        Log.d("Favorite", "Inserting favorite: $departureCode -> $destinationCode")
                    }
                }

                val message = if (isFavorite) "Removed from favorites" else "Added to favorites"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()

                val searchView = findViewById<SearchView>(R.id.svFlight)
                val currentQuery = searchView.query?.toString()

                if (currentQuery.isNullOrBlank() && isFavorite) {
                    loadFavorites()
                }

            } catch (e: Exception) {
                Log.e("toggleFavorite", "Error toggling favorite", e)
                Toast.makeText(this@MainActivity, "Error toggling favorite", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun updateSearchViewSuggestions(cursor: MatrixCursor) {
        val svFlight = findViewById<SearchView>(R.id.svFlight)

        val adapter = SimpleCursorAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            cursor,
            arrayOf("name"),
            intArrayOf(android.R.id.text1),
            0
        )

        svFlight.suggestionsAdapter = adapter

        adapter.notifyDataSetChanged()

        svFlight.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return handleSuggestionClick(position)
            }

            override fun onSuggestionClick(position: Int): Boolean {
                return handleSuggestionClick(position)
            }
        })

        Log.d("SearchView", "Suggestions updated with ${cursor.count} items")
    }

    private fun handleSuggestionClick(position: Int): Boolean {
        val svFlight = findViewById<SearchView>(R.id.svFlight)
        val cursor = svFlight.suggestionsAdapter.getItem(position) as? MatrixCursor
        cursor?.let {
            val iataCode = it.getString(it.getColumnIndex("iata_code"))
            fetchFlights(iataCode)
        }
        return true
    }

    private fun fetchFlights(departureCode: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                database.airportDao().getAvailableFlights(departureCode)
            }
            val flightFromTextView = findViewById<TextView>(R.id.flightFrom)
            val noResultsTextView = findViewById<TextView>(R.id.noResultsTextView)

            if (results.isNotEmpty()) {
                flightFromTextView.visibility = View.VISIBLE
                flightFromTextView.text = getString(R.string.flights_from, departureCode)
                val flightInfos = results.map {
                    FlightInfo(
                        departureCode = departureCode,
                        flightIataCode = it.iata_code
                    )
                }
                flightAdapter.updateData(flightInfos)
                noResultsTextView.visibility = View.GONE
            } else {
                flightFromTextView.visibility = View.GONE
                flightAdapter.updateData(emptyList())
                noResultsTextView.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "No flights available", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
