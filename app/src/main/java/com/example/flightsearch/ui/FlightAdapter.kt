package com.example.flightsearch.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.flightsearch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FlightInfo(
    val departureCode: String,
    val flightIataCode: String,
)

class FlightAdapter(
    private val onFavoriteToggle: (departureCode: String, destinationCode: String) -> Unit,
    private val isFavorite: suspend (departureCode: String, destinationCode: String) -> Boolean,
    private val fetchAirportName: suspend (iataCode: String) -> String?,
) : RecyclerView.Adapter<FlightAdapter.FlightViewHolder>() {

    private val data = mutableListOf<FlightInfo>()

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newData: List<FlightInfo>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlightViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.flight_item, parent, false)
        return FlightViewHolder(view)
    }

    override fun onBindViewHolder(holder: FlightViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    inner class FlightViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val departureCodeTextView: TextView = itemView.findViewById(R.id.tvDepartureCode)
        private val departureNameTextView: TextView = itemView.findViewById(R.id.tvDepartureName)
        private val flightIataTextView: TextView = itemView.findViewById(R.id.tvArrivalCode)
        private val flightNameTextView: TextView = itemView.findViewById(R.id.tvArrivalName)
        private val favoriteImageView: ImageView = itemView.findViewById(R.id.favoriteBtn)

        fun bind(flight: FlightInfo) {
            departureCodeTextView.text = flight.departureCode
            flightIataTextView.text = flight.flightIataCode

            CoroutineScope(Dispatchers.Main).launch {
                val departureName = withContext(Dispatchers.IO) {
                    fetchAirportName(flight.departureCode)
                }
                val flightName = withContext(Dispatchers.IO) {
                    fetchAirportName(flight.flightIataCode)
                }
                departureNameTextView.text = departureName ?: "Unknown Departure"
                flightNameTextView.text = flightName ?: "Unknown Arrival"

                val isFav = withContext(Dispatchers.IO) {
                    isFavorite(flight.departureCode, flight.flightIataCode)
                }

                favoriteImageView.setImageResource(
                    if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_outline
                )

            }

            favoriteImageView.setOnClickListener {

                val isCurrentlyFavorite = favoriteImageView.tag == "favorite"

                if (isCurrentlyFavorite) {
                    favoriteImageView.setImageResource(R.drawable.ic_favorite_outline)
                    favoriteImageView.tag = "outline"
                } else {
                    favoriteImageView.setImageResource(R.drawable.ic_favorite)
                    favoriteImageView.tag = "favorite"
                }
                onFavoriteToggle(flight.departureCode, flight.flightIataCode)
            }
        }
    }
}
