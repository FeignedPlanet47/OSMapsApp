package com.example.osmapsapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.osmapsapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val poiCategories = listOf(
        "Заправка" to "amenity=fuel",
        "Кафе" to "amenity=cafe",
        "Ресторан" to "amenity=restaurant",
        "Отель" to "tourism=hotel",
        "Кемпинг" to "tourism=camp_site",
        "Возле воды" to "natural~\"water|lake|river\"",
        "В лесу" to "natural~\"wood|forest\"",
        "Достопримечательность" to "tourism~\"attraction|viewpoint\"",
        "Парковка" to "amenity=parking"
    )

    private val selectedCategories = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Configuration.getInstance().userAgentValue = packageName
        map = binding.map
        map.setMultiTouchControls(true)

        binding.selectCategoriesButton.setOnClickListener {
            showCategorySelectionDialog()
        }

        binding.calculateRouteButton.setOnClickListener {
            val start = binding.startPointInput.text.toString()
            val end = binding.endPointInput.text.toString()
            val maxDistance = binding.maxDistanceInput.text.toString().toDoubleOrNull()
            if (start.isBlank() || end.isBlank() || maxDistance == null) {
                Toast.makeText(this, "Заполните все поля корректно", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedCategories.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одну категорию", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                calculateRouteWithStops(start, end, maxDistance)
            }
        }

        binding.clearRouteButton.setOnClickListener {
            map.overlays.clear()
            map.invalidate()
        }
    }

    private fun showCategorySelectionDialog() {
        val categories = poiCategories.map { it.first }.toTypedArray()
        val checkedItems = BooleanArray(categories.size) { i ->
            selectedCategories.contains(poiCategories[i].first)
        }

        AlertDialog.Builder(this)
            .setTitle("Выберите категории остановок")
            .setMultiChoiceItems(categories, checkedItems) { _, which, isChecked ->
                val category = poiCategories[which].first
                if (isChecked) {
                    selectedCategories.add(category)
                } else {
                    selectedCategories.remove(category)
                }
                updateSelectedCategoriesText()
            }
            .setPositiveButton("Готово", null)
            .show()
    }

    private fun updateSelectedCategoriesText() {
        binding.selectedCategoriesText.text = when {
            selectedCategories.isEmpty() -> "Не выбрано"
            selectedCategories.size == 1 -> "Выбрано: ${selectedCategories.first()}"
            selectedCategories.size > 3 -> "Выбрано: ${selectedCategories.size} категорий"
            else -> "Выбрано: ${selectedCategories.joinToString(", ")}"
        }
    }

    private suspend fun calculateRouteWithStops(start: String, end: String, maxDistanceKm: Double) {
        val startTime = System.currentTimeMillis()
        try {
            val startCoords = geocode(start) ?: run {
                Toast.makeText(this, "Не удалось определить координаты начальной точки", Toast.LENGTH_SHORT).show()
                return
            }
            val endCoords = geocode(end) ?: run {
                Toast.makeText(this, "Не удалось определить координаты конечной точки", Toast.LENGTH_SHORT).show()
                return
            }

            val threshold = maxDistanceKm * 1000
            val totalDistance = startCoords.distanceToAsDouble(endCoords)
            val numStops = (totalDistance / threshold).toInt()

            val stops = mutableListOf(startCoords)

            for (i in 1..numStops) {
                val fraction = i * threshold / totalDistance
                val pointOnLine = interpolateGeoPoint(startCoords, endCoords, fraction)
                val poi = getNearestPOI(pointOnLine, maxDistanceKm) ?: pointOnLine
                stops.add(poi)
            }

            stops.add(endCoords)

            val finalRoute = mutableListOf<GeoPoint>()
            for (i in 0 until stops.size - 1) {
                val segment = getRoute(stops[i], stops[i + 1])
                finalRoute.addAll(segment)
            }

            withContext(Dispatchers.Main) {
                drawRoute(finalRoute, stops)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            val endTime = System.currentTimeMillis()
            val elapsedTime = endTime - startTime
            Log.d("RouteCalculationTime", "Время выполнения алгоритма: ${elapsedTime} мс")
        }
    }

    private fun drawRoute(routePoints: List<GeoPoint>, stops: List<GeoPoint>) {
        map.overlays.clear()

        val polyline = Polyline().apply {
            setPoints(routePoints)
            color = 0xFFFF0000.toInt()
            width = 5f
        }
        map.overlays.add(polyline)

        stops.forEachIndexed { index, geoPoint ->
            val marker = Marker(map).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = when (index) {
                    0 -> "Начало"
                    stops.size - 1 -> "Конец"
                    else -> "Остановка ${index}"
                }
            }
            map.overlays.add(marker)
        }

        map.controller.setZoom(8.0)
        map.controller.setCenter(stops.first())
        map.invalidate()
    }

    private fun interpolateGeoPoint(start: GeoPoint, end: GeoPoint, fraction: Double): GeoPoint {
        val lat = start.latitude + (end.latitude - start.latitude) * fraction
        val lon = start.longitude + (end.longitude - start.longitude) * fraction
        return GeoPoint(lat, lon)
    }

    private suspend fun getNearestPOI(location: GeoPoint, maxDistanceKm: Double): GeoPoint? = withContext(Dispatchers.IO) {
        if (selectedCategories.isEmpty()) return@withContext null

        var retries = 3
        while (retries > 0) {
            try {
                val randomCategory = selectedCategories.random()
                val categoryTag = poiCategories.first { it.first == randomCategory }.second

                val query = if (categoryTag.contains("~")) {
                    "[out:json];node(around:${maxDistanceKm * 1000},${location.latitude},${location.longitude})[$categoryTag];out 1;"
                } else {
                    "[out:json];node(around:${maxDistanceKm * 1000},${location.latitude},${location.longitude})[$categoryTag];out 1;"
                }

                val url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val elements = JSONObject(body).getJSONArray("elements")
                if (elements.length() == 0) return@withContext null
                val obj = elements.getJSONObject(0)
                return@withContext GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
            } catch (e: Exception) {
                retries--
                if (retries == 0) return@withContext null
                delay(1000)
            }
        }
        null
    }

    private suspend fun geocode(query: String): GeoPoint? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search?format=json&q=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder().url(url).header("User-Agent", "RouteApp/1.0").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext null
        val array = JSONObject("{\"results\":$body}").getJSONArray("results")
        if (array.length() == 0) return@withContext null
        val obj = array.getJSONObject(0)
        GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
    }

    private suspend fun getRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> = withContext(Dispatchers.IO) {
        val url = "http://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext emptyList()
        val coords = JSONObject(body).getJSONArray("routes")
            .getJSONObject(0).getJSONObject("geometry")
            .getJSONArray("coordinates")
        val list = mutableListOf<GeoPoint>()
        for (i in 0 until coords.length()) {
            val point = coords.getJSONArray(i)
            list.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
        }
        list
    }
}