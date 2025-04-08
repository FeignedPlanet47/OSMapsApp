package com.example.osmapsapp.Data.Remote

import com.example.osmapsapp.Data.Remote.Api.NominatimApi
import com.example.osmapsapp.Data.Remote.Api.OSRMApi
import com.example.osmapsapp.Data.Remote.Api.OverpassApi
import com.example.osmapsapp.Data.Remote.Dto.NominatimGeocodingResponse
import com.example.osmapsapp.Data.Remote.Dto.OSRMResponse
import com.example.osmapsapp.Data.Remote.Dto.OverpassResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDataSource @Inject constructor(
    private val nominatimApi: NominatimApi,
    private val osrmApi: OSRMApi,
    private val overpassApi: OverpassApi
) {
    suspend fun geocode(query: String): List<NominatimGeocodingResponse> {
        return nominatimApi.search(query, userAgent = "OSMapsApp/1.0")
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): NominatimGeocodingResponse {
        return nominatimApi.reverse(lat, lon, userAgent = "OSMapsApp/1.0")
    }

    suspend fun getRoute(profile: String, coordinates: String): OSRMResponse {
        return osrmApi.getRoute(profile, coordinates)
    }

    suspend fun searchPOIs(query: String): OverpassResponse {
        return overpassApi.searchPOIs(query)
    }
}