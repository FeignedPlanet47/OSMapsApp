package com.example.osmapsapp.Data.Remote.Api

import com.example.osmapsapp.Data.Remote.Dto.OSRMResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OSRMApi {
    @GET("route/v1/{profile}/{coordinates}")
    suspend fun getRoute(
        @Path("profile") profile: String,
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson"
    ): OSRMResponse
}