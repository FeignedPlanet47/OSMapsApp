package com.example.osmapsapp.Data.Repository

import com.example.osmapsapp.Data.Local.LocalDataSource
import com.example.osmapsapp.Data.Remote.RemoteDataSource
import com.example.osmapsapp.Domain.Model.RoutePoint
import com.example.osmapsapp.Domain.Repository.GeocodingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeocodingRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource
) : GeocodingRepository {

    private val geocodeCache = mutableMapOf<String, RoutePoint>()
    private val reverseGeocodeCache = mutableMapOf<String, String>()

    init {
        geocodeCache.putAll(localDataSource.getGeocodeCache())
        reverseGeocodeCache.putAll(localDataSource.getReverseGeocodeCache())
    }

    override suspend fun geocode(query: String): Result<RoutePoint> {
        return try {
            geocodeCache[query]?.let { 
                return Result.success(it) 
            }

            val response = remoteDataSource.geocode(query)
            if (response.isEmpty()) {
                return Result.failure(Exception("No results found"))
            }

            val first = response.first()
            val point = RoutePoint(
                latitude = first.latitude.toDouble(),
                longitude = first.longitude.toDouble(),
                displayName = first.displayName
            )
            
            geocodeCache[query] = point
            localDataSource.saveGeocodeCache(geocodeCache)
            
            Result.success(point)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reverseGeocode(point: RoutePoint): Result<String> {
        return try {
            val key = "${point.latitude},${point.longitude}"
            reverseGeocodeCache[key]?.let { 
                return Result.success(it) 
            }

            val response = remoteDataSource.reverseGeocode(point.latitude, point.longitude)
            val address = response.displayName ?: "Адрес не определен"
            
            reverseGeocodeCache[key] = address
            localDataSource.saveReverseGeocodeCache(reverseGeocodeCache)
            
            Result.success(address)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}