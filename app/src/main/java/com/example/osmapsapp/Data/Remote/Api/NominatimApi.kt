package com.example.osmapsapp.Data.Remote.Api

import com.example.osmapsapp.Data.Remote.Dto.NominatimGeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Header("User-Agent") userAgent: String
    ): List<NominatimGeocodingResponse>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Header("User-Agent") userAgent: String
    ): NominatimGeocodingResponse
}