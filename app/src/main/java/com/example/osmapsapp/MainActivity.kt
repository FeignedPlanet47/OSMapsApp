package com.example.osmapsapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.osmapsapp.Models.GraphHopperResponse
import com.example.osmapsapp.Models.NominatimResponse
import com.example.osmapsapp.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView

    private val nominatimService: NominatimService by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().userAgentValue = packageName

        map = binding.map

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.controller.setZoom(12.0)
        map.controller.setCenter(GeoPoint(55.7558, 37.6176))

        binding.calculateRouteButton.setOnClickListener {
            val start = binding.startPointInput.text.toString()
            val end = binding.endPointInput.text.toString()
            val stops = binding.stopsInput.text.toString().split(";").map { it.trim() }

            if (start.isNotEmpty() && end.isNotEmpty()) {
                calculateAndDisplayRoute(start, end, stops)
            } else {
                Toast.makeText(this, "Начальная и конечная точки обязательны",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateAndDisplayRoute(start: String, end: String, stops: List<String>) {
        val points = mutableListOf<GeoPoint>()
        val allPoints = listOf(start) + stops + listOf(end)

        allPoints.forEach { point ->
            nominatimService.search(point).enqueue(object : Callback<List<NominatimResponse>> {
                override fun onResponse(
                    call: Call<List<NominatimResponse>>,
                    response: Response<List<NominatimResponse>>
                ) {
                    if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                        val location = response.body()!![0]
                        val geoPoint = GeoPoint(location.lat.toDouble(), location.lon.toDouble())
                        points.add(geoPoint)

                        if (points.size == allPoints.size) {
                            getRouteFromGraphHopper(points)
                        }
                    } else {
                        Log.e("NominatimError", "Не удалось найти координаты для точки: $point")
                    }
                }

                override fun onFailure(call: Call<List<NominatimResponse>>, t: Throwable) {
                    Log.e("NominatimError", "Ошибка при запросе к Nominatim: ${t.message}")
                }
            })
        }
    }

    private fun getRouteFromGraphHopper(points: List<GeoPoint>) {
        val coordinates = points.map { "${it.latitude},${it.longitude}" }
        val apiKey = "bd4344ed-3e9b-4c95-b321-998ef0e8b23b"
        graphHopperService.getRoute(
            points = coordinates,
            passThrough = true,
            chDisable = true,
            apiKey = apiKey
        ).enqueue(object : Callback<GraphHopperResponse> {
            override fun onResponse(call: Call<GraphHopperResponse>, response: Response<GraphHopperResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val route = response.body()!!.paths[0]
                    val polyline = decodePolyline(route.points)
                    displayRouteOnMap(polyline)
                } else {
                    Log.e("GraphHopperError", "Не удалось получить маршрут от GraphHopper. Ответ: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<GraphHopperResponse>, t: Throwable) {
                Log.e("GraphHopperError", "Ошибка при запросе к GraphHopper: ${t.message}")
            }
        })
    }

    private fun decodePolyline(encodedPolyline: String): List<GeoPoint> {
        val decodedPoints = mutableListOf<GeoPoint>()
        var currentIndex = 0
        val polylineLength = encodedPolyline.length
        var currentLatitude = 0
        var currentLongitude = 0

        while (currentIndex < polylineLength) {
            var byte: Int
            var shift = 0
            var result = 0

            do {
                byte = encodedPolyline[currentIndex++].code - 63
                result = result or (byte and 0x1f shl shift)
                shift += 5
            } while (byte >= 0x20)

            val deltaLatitude = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            currentLatitude += deltaLatitude

            shift = 0
            result = 0

            do {
                byte = encodedPolyline[currentIndex++].code - 63
                result = result or (byte and 0x1f shl shift)
                shift += 5
            } while (byte >= 0x20)

            val deltaLongitude = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            currentLongitude += deltaLongitude

            val point = GeoPoint(currentLatitude / 1E5, currentLongitude / 1E5)
            decodedPoints.add(point)
        }

        Log.d("Polyline", "Декодировано точек: ${decodedPoints.size}")
        return decodedPoints
    }

    private fun displayRouteOnMap(route: List<GeoPoint>) {
        map.overlays.clear()

        val polyline = Polyline()
        polyline.setPoints(route)
        map.overlays.add(polyline)

        map.invalidate()
    }

}