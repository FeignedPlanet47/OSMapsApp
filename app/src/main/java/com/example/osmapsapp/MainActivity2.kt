package com.example.osmapsapp

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.osmapsapp.databinding.ActivityMapBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MainActivity2 : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var binding: ActivityMapBinding
    private lateinit var sharedPref: SharedPreferences
    private val gson = Gson()
    private var isOnline = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val geocodeCache = mutableMapOf<String, GeoPoint>()
    private val reverseGeocodeCache = mutableMapOf<GeoPoint, String>()
    private val routeCache = mutableMapOf<String, List<GeoPoint>>()
    private val poiCache = mutableMapOf<String, StopInfo?>()

    companion object {
        private const val PREFS_NAME = "RouteCachePrefs"
        private const val KEY_GEOCODE_CACHE = "geocode_cache"
        private const val KEY_REVERSE_GEOCODE_CACHE = "reverse_geocode_cache"
        private const val KEY_ROUTE_CACHE = "route_cache"
        private const val KEY_POI_CACHE = "poi_cache"
        private const val OFFLINE_MAP_DIR = "osmdroid"
        private const val OFFLINE_MAP_NAME = "map.zip"

        val poiCategories = listOf(
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
    }

    enum class RouteType(val apiValue: String) {
        DRIVING("driving"),
        CYCLING("cycling"),
        WALKING("walking")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isOnline = checkInternetConnection()

        sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        loadCache()

        setupOfflineMaps()

        Configuration.getInstance().userAgentValue = packageName
        map = binding.map
        map.setMultiTouchControls(true)

        binding.clearCacheButton.setOnClickListener {
            clearCache()
            Toast.makeText(this, "Кэш очищен", Toast.LENGTH_SHORT).show()
        }

        val start = intent.getStringExtra("startPoint") ?: ""
        val end = intent.getStringExtra("endPoint") ?: ""
        val maxDistanceKm = intent.getDoubleExtra("maxDistance", 0.0)
        val selectedCategories =
            intent.getStringArrayListExtra("selectedCategories") ?: arrayListOf()
        val routeType =
            RouteType.valueOf(intent.getStringExtra("routeType") ?: RouteType.DRIVING.name)

        lifecycleScope.launch {
            if (!isOnline) {
                Toast.makeText(
                    this@MainActivity2,
                    "Работаем в оффлайн-режиме. Данные могут быть устаревшими",
                    Toast.LENGTH_LONG
                ).show()
            }

            val startPoint = async { geocode(start) }.await()
            val endPoint = async { geocode(end) }.await()

            if (startPoint == null || endPoint == null) {
                Toast.makeText(
                    this@MainActivity2,
                    "Не удалось определить координаты точек",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val tempRoutePoints = getRoute(startPoint, endPoint, routeType.apiValue, emptyList())
            if (tempRoutePoints.isEmpty()) {
                Toast.makeText(
                    this@MainActivity2,
                    "Не удалось построить маршрут",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val stops =
                findStopsAlongRoute(startPoint, tempRoutePoints, maxDistanceKm, selectedCategories)
            val stopPoints = stops.map { it.point }
            val routePoints = getRoute(startPoint, endPoint, routeType.apiValue, stopPoints)
            drawRoute(routePoints, stops, routeType)
        }
    }

    private fun checkInternetConnection(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun setupOfflineMaps() {
        val mapFile = File(getExternalFilesDir(OFFLINE_MAP_DIR), OFFLINE_MAP_NAME)
        if (mapFile.exists()) {
            try {
                val tileWriter = SqliteArchiveTileWriter(mapFile.absolutePath)
                val tileSource = XYTileSource(
                    "OfflineMap",
                    1, 20, 256, ".png",
                    arrayOf("https://a.tile.openstreetmap.org/")
                )
                Configuration.getInstance().setTileDownloadThreads(0)
                Configuration.getInstance().setTileFileSystemThreads(0)
                map.setTileSource(tileSource)
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка загрузки оффлайн-карты", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCache()
    }

    private fun saveCache() {
        sharedPref.edit {
            putString(KEY_GEOCODE_CACHE, gson.toJson(geocodeCache))
            putString(KEY_REVERSE_GEOCODE_CACHE, gson.toJson(reverseGeocodeCache))
            putString(KEY_ROUTE_CACHE, gson.toJson(routeCache))
            putString(KEY_POI_CACHE, gson.toJson(poiCache))
        }
    }

    private fun loadCache() {
        val geocodeType = object : TypeToken<Map<String, GeoPoint>>() {}.type
        val reverseGeocodeType = object : TypeToken<Map<GeoPoint, String>>() {}.type
        val routeType = object : TypeToken<Map<String, List<GeoPoint>>>() {}.type
        val poiType = object : TypeToken<Map<String, StopInfo?>>() {}.type

        sharedPref.getString(KEY_GEOCODE_CACHE, null)?.let {
            geocodeCache.putAll(gson.fromJson(it, geocodeType))
        }
        sharedPref.getString(KEY_REVERSE_GEOCODE_CACHE, null)?.let {
            reverseGeocodeCache.putAll(gson.fromJson(it, reverseGeocodeType))
        }
        sharedPref.getString(KEY_ROUTE_CACHE, null)?.let {
            routeCache.putAll(gson.fromJson(it, routeType))
        }
        sharedPref.getString(KEY_POI_CACHE, null)?.let {
            poiCache.putAll(gson.fromJson(it, poiType))
        }
    }

    private fun clearCache() {
        geocodeCache.clear()
        reverseGeocodeCache.clear()
        routeCache.clear()
        poiCache.clear()
        sharedPref.edit().clear().apply()
    }

    private suspend fun geocode(query: String): GeoPoint? = withContext(Dispatchers.IO) {
        geocodeCache[query]?.let { return@withContext it }

        if (!isOnline) return@withContext null

        try {
            val url =
                "https://nominatim.openstreetmap.org/search?format=json&q=" + URLEncoder.encode(
                    query,
                    "UTF-8"
                )
            val response = client.newCall(
                Request.Builder().url(url).header("User-Agent", "RouteApp/1.0").build()
            ).execute()
            val body = response.body?.string() ?: return@withContext null
            val array = JSONArray(body)
            val obj = array.getJSONObject(0)
            val point = GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
            geocodeCache[query] = point
            point
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun reverseGeocode(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        reverseGeocodeCache[point]?.let { return@withContext it }

        if (!isOnline) return@withContext "Адрес не доступен в оффлайне"

        try {
            val url =
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=${point.latitude}&lon=${point.longitude}&zoom=18&addressdetails=1"
            val response = client.newCall(
                Request.Builder().url(url).header("User-Agent", "RouteApp/1.0").build()
            ).execute()
            val body = response.body?.string() ?: return@withContext null
            val address = JSONObject(body).optString("display_name")
            reverseGeocodeCache[point] = address
            address
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getRoute(
        start: GeoPoint,
        end: GeoPoint,
        routeType: String,
        stops: List<GeoPoint>
    ): List<GeoPoint> = withContext(Dispatchers.IO) {
        val cacheKey =
            "$routeType:${start.latitude},${start.longitude}->${stops.joinToString()}->${end.latitude},${end.longitude}"
        routeCache[cacheKey]?.let { return@withContext it }

        if (!isOnline) return@withContext emptyList()

        try {
            val routePoints = listOf(start) + stops + end
            val coordinates = routePoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val url =
                "https://router.project-osrm.org/route/v1/$routeType/$coordinates?overview=full&geometries=geojson"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val coords =
                JSONObject(body).getJSONArray("routes").getJSONObject(0).getJSONObject("geometry")
                    .getJSONArray("coordinates")
            val result = List(coords.length()) {
                val point = coords.getJSONArray(it)
                GeoPoint(point.getDouble(1), point.getDouble(0))
            }
            routeCache[cacheKey] = result
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getNearestPOI(
        location: GeoPoint,
        maxDistanceKm: Double,
        selectedCategories: List<String>
    ): StopInfo? = withContext(Dispatchers.IO) {
        val key = "${location.latitude},${location.longitude},$maxDistanceKm,${
            selectedCategories.sorted().joinToString()
        }"
        poiCache[key]?.let { return@withContext it }

        if (!isOnline) return@withContext null

        val tagsList = selectedCategories.mapNotNull { selected ->
            poiCategories.find { it.first == selected }?.second
        }
        if (tagsList.isEmpty()) return@withContext null

        val radiusMeters = (maxDistanceKm * 1000).toInt()
        val groupedTags = tagsList.groupBy { it.substringBefore('=') }

        val queryBuilder = StringBuilder("[out:json];(")
        groupedTags.forEach { (key, values) ->
            val orValues = values.map { it.substringAfter('=') }
            if (orValues.size > 1) {
                queryBuilder.append(
                    "node(around:$radiusMeters,${location.latitude},${location.longitude})[$key~\"${
                        orValues.joinToString(
                            "|"
                        )
                    }\"];"
                )
            } else {
                queryBuilder.append("node(around:$radiusMeters,${location.latitude},${location.longitude})[$key=${orValues.first()}];")
            }
        }
        queryBuilder.append(");out body;")

        val url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(
            queryBuilder.toString(),
            "UTF-8"
        )

        repeat(3) {
            try {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: return@withContext null
                val elements = JSONObject(body).getJSONArray("elements")
                if (elements.length() == 0) return@withContext null

                var closest: StopInfo? = null
                var minDistance = Double.MAX_VALUE

                for (i in 0 until elements.length()) {
                    val obj = elements.getJSONObject(i)
                    val point = GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
                    val distance = point.distanceToAsDouble(location)
                    if (distance < minDistance) {
                        minDistance = distance
                        val name = obj.optJSONObject("tags")?.optString("name") ?: "Остановка"
                        val address = reverseGeocode(point) ?: "Адрес не определен"
                        closest = StopInfo(name, address, point)
                    }
                }

                poiCache[key] = closest
                return@withContext closest
            } catch (e: Exception) {
                delay(1000)
            }
        }
        null
    }

    private suspend fun findStopsAlongRoute(
        startPoint: GeoPoint,
        routePoints: List<GeoPoint>,
        maxDistanceKm: Double,
        selectedCategories: List<String>
    ): List<StopInfo> {
        val stops = mutableListOf<StopInfo>()
        var accumulatedDistance = 0.0
        for (i in 1 until routePoints.size) {
            val previousPoint = routePoints[i - 1]
            val currentPoint = routePoints[i]
            accumulatedDistance += currentPoint.distanceToAsDouble(previousPoint)
            if (accumulatedDistance >= maxDistanceKm * 1000) {
                val stop = getNearestPOI(currentPoint, maxDistanceKm, selectedCategories)
                if (stop != null) stops.add(stop)
                accumulatedDistance = 0.0
            }
        }
        stops.add(0, StopInfo("Старт", "Стартовая точка", startPoint))
        stops.add(StopInfo("Конец", "Конечная точка", routePoints.last()))
        return stops
    }

    private fun drawRoute(
        routePoints: List<GeoPoint>,
        stops: List<StopInfo>,
        routeType: RouteType
    ) {
        map.overlays.clear()

        if (routePoints.isNotEmpty()) {
            val polyline = Polyline().apply {
                setPoints(routePoints)
                color = when (routeType) {
                    RouteType.DRIVING -> 0xFF0000FF.toInt()
                    RouteType.CYCLING -> 0xFF00AA00.toInt()
                    RouteType.WALKING -> 0xFFFFA500.toInt()
                }
                width = 8f
            }
            map.overlays.add(polyline)
        }

        stops.forEach { stop ->
            val marker = Marker(map).apply {
                position = stop.point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = stop.name
                subDescription = stop.address
            }
            map.overlays.add(marker)
        }

        if (routePoints.isNotEmpty()) {
            var north = routePoints[0].latitude
            var south = routePoints[0].latitude
            var east = routePoints[0].longitude
            var west = routePoints[0].longitude

            for (point in routePoints) {
                if (point.latitude > north) north = point.latitude
                if (point.latitude < south) south = point.latitude
                if (point.longitude > east) east = point.longitude
                if (point.longitude < west) west = point.longitude
            }

            val bounds = org.osmdroid.util.BoundingBox(north, east, south, west)
            map.zoomToBoundingBox(bounds, true, 50)
        }

        map.invalidate()
    }
}