package com.example.osmapsapp.Data.Repository

import com.example.osmapsapp.Data.Local.LocalDataSource
import com.example.osmapsapp.Data.Remote.RemoteDataSource
import com.example.osmapsapp.Domain.Model.RoutePoint
import com.example.osmapsapp.Domain.Model.RouteType
import com.example.osmapsapp.Domain.Model.StopInfo
import com.example.osmapsapp.Domain.Repository.GeocodingRepository
import com.example.osmapsapp.Domain.Repository.RoutingRepository
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class RoutingRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val geocodingRepository: GeocodingRepository
) : RoutingRepository {

    private val routeCache = mutableMapOf<String, List<RoutePoint>>()
    private val poiCache = mutableMapOf<String, StopInfo?>()

    companion object {
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

    init {
        routeCache.putAll(localDataSource.getRouteCache())
        poiCache.putAll(localDataSource.getPOICache())
    }

    override suspend fun getRoute(
        start: RoutePoint,
        end: RoutePoint,
        routeType: RouteType,
        stops: List<RoutePoint>
    ): Result<List<RoutePoint>> {
        return try {
            val profile = when (routeType) {
                RouteType.DRIVING -> "driving"
                RouteType.CYCLING -> "cycling"
                RouteType.WALKING -> "walking"
            }

            val routePoints = listOf(start) + stops + end
            val coordinates = routePoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            val cacheKey = "$profile:$coordinates"

            routeCache[cacheKey]?.let { 
                return Result.success(it) 
            }

            val response = remoteDataSource.getRoute(profile, coordinates)
            if (response.routes.isEmpty()) {
                return Result.failure(Exception("No route found"))
            }

            val points = response.routes.first().geometry.coordinates.map { coord ->
                RoutePoint(
                    latitude = coord[1],
                    longitude = coord[0]
                )
            }

            routeCache[cacheKey] = points
            localDataSource.saveRouteCache(routeCache)

            Result.success(points)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findNearbyPOIs(
        location: RoutePoint,
        nextStop: RoutePoint,
        maxDistanceKm: Double,
        categories: List<String>
    ): Result<List<StopInfo>> {
        return try {
            val key = "${location.latitude},${location.longitude},$maxDistanceKm,${categories.sorted().joinToString()}"
            poiCache[key]?.let { cachedPOI ->
                return Result.success(if (cachedPOI != null) listOf(cachedPOI) else emptyList())
            }

            val tagsList = categories.mapNotNull { selected ->
                poiCategories.find { it.first == selected }?.second
            }
            if (tagsList.isEmpty()) {
                return Result.success(emptyList())
            }

            val radiusMeters = (maxDistanceKm * 1000).toInt()
            val groupedTags = tagsList.groupBy { it.substringBefore('=') }

            val queryBuilder = StringBuilder("[out:json];(")
            groupedTags.forEach { (key, values) ->
                val orValues = values.map { it.substringAfter('=') }
                if (orValues.size > 1) {
                    queryBuilder.append(
                        "node(around:$radiusMeters,${location.latitude},${location.longitude})[$key~\"${orValues.joinToString("|")}\"];"
                    )
                } else {
                    queryBuilder.append("node(around:$radiusMeters,${location.latitude},${location.longitude})[$key=${orValues.first()}];")
                }
            }
            queryBuilder.append(");out body;")

            val encodedQuery = URLEncoder.encode(queryBuilder.toString(), "UTF-8")
            val response = remoteDataSource.searchPOIs(encodedQuery)

            if (response.elements.isEmpty()) {
                poiCache[key] = null
                localDataSource.savePOICache(poiCache)
                return Result.success(emptyList())
            }

            var closestPOI: StopInfo? = null
            var minDistance = Double.MAX_VALUE

            for (element in response.elements) {
                val poiPoint = RoutePoint(element.lat, element.lon)
                val distance = calculateDistance(location, poiPoint)
                if (distance < minDistance) {
                    minDistance = distance
                    val name = element.tags?.get("name") ?: "Остановка"
                    val addressResult = geocodingRepository.reverseGeocode(poiPoint)
                    val address = addressResult.getOrNull() ?: "Адрес не определен"
                    closestPOI = StopInfo(name, address, poiPoint)
                }
            }

            poiCache[key] = closestPOI
            localDataSource.savePOICache(poiCache)

            Result.success(if (closestPOI != null) listOf(closestPOI) else emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findStopsAlongRoute(
        startPoint: RoutePoint,
        routePoints: List<RoutePoint>,
        maxDistanceKm: Double,
        selectedCategories: List<String>
    ): Result<List<StopInfo>> {
        return try {
            val stops = mutableListOf<StopInfo>()
            var accumulatedDistance = 0.0
            
            for (i in 1 until routePoints.size) {
                val previousPoint = routePoints[i - 1]
                val currentPoint = routePoints[i]
                accumulatedDistance += calculateDistance(previousPoint, currentPoint)
                
                if (accumulatedDistance >= maxDistanceKm * 1000) {
                    val nearbyPOIs = findNearbyPOIs(currentPoint, currentPoint, maxDistanceKm, selectedCategories)
                    nearbyPOIs.getOrNull()?.firstOrNull()?.let { stop ->
                        stops.add(stop)
                    }
                    accumulatedDistance = 0.0
                }
            }
            
            val startAddress = geocodingRepository.reverseGeocode(startPoint).getOrElse { "Стартовая точка" }
            val endAddress = geocodingRepository.reverseGeocode(routePoints.last()).getOrElse { "Конечная точка" }
            
            stops.add(0, StopInfo("Старт", startAddress, startPoint))
            stops.add(StopInfo("Конец", endAddress, routePoints.last()))
            
            Result.success(stops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateDistance(point1: RoutePoint, point2: RoutePoint): Double {
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = sin(deltaLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return 6371000 * c
    }
}