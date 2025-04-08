package com.example.osmapsapp.Data.Local

import android.content.SharedPreferences
import com.example.osmapsapp.Domain.Model.RoutePoint
import com.example.osmapsapp.Domain.Model.StopInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataSource @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {
    companion object {
        private const val KEY_GEOCODE_CACHE = "geocode_cache"
        private const val KEY_REVERSE_GEOCODE_CACHE = "reverse_geocode_cache"
        private const val KEY_ROUTE_CACHE = "route_cache"
        private const val KEY_POI_CACHE = "poi_cache"
    }

    fun getGeocodeCache(): Map<String, RoutePoint> {
        val type = object : TypeToken<Map<String, RoutePoint>>() {}.type
        val json = sharedPreferences.getString(KEY_GEOCODE_CACHE, null)
        return if (json != null) gson.fromJson(json, type) else emptyMap()
    }

    fun saveGeocodeCache(cache: Map<String, RoutePoint>) {
        sharedPreferences.edit()
            .putString(KEY_GEOCODE_CACHE, gson.toJson(cache))
            .apply()
    }

    fun getReverseGeocodeCache(): Map<String, String> {
        val type = object : TypeToken<Map<String, String>>() {}.type
        val json = sharedPreferences.getString(KEY_REVERSE_GEOCODE_CACHE, null)
        return if (json != null) gson.fromJson(json, type) else emptyMap()
    }

    fun saveReverseGeocodeCache(cache: Map<String, String>) {
        sharedPreferences.edit()
            .putString(KEY_REVERSE_GEOCODE_CACHE, gson.toJson(cache))
            .apply()
    }

    fun getRouteCache(): Map<String, List<RoutePoint>> {
        val type = object : TypeToken<Map<String, List<RoutePoint>>>() {}.type
        val json = sharedPreferences.getString(KEY_ROUTE_CACHE, null)
        return if (json != null) gson.fromJson(json, type) else emptyMap()
    }

    fun saveRouteCache(cache: Map<String, List<RoutePoint>>) {
        sharedPreferences.edit()
            .putString(KEY_ROUTE_CACHE, gson.toJson(cache))
            .apply()
    }

    fun getPOICache(): Map<String, StopInfo?> {
        val type = object : TypeToken<Map<String, StopInfo?>>() {}.type
        val json = sharedPreferences.getString(KEY_POI_CACHE, null)
        return if (json != null) gson.fromJson(json, type) else emptyMap()
    }

    fun savePOICache(cache: Map<String, StopInfo?>) {
        sharedPreferences.edit()
            .putString(KEY_POI_CACHE, gson.toJson(cache))
            .apply()
    }

    fun clearCache() {
        sharedPreferences.edit().clear().apply()
    }
}