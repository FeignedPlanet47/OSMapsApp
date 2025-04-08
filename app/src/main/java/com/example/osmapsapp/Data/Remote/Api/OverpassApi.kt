package com.example.osmapsapp.Data.Remote.Api

import com.example.osmapsapp.Data.Remote.Dto.OverpassResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OverpassApi {
    @GET("interpreter")
    suspend fun searchPOIs(
        @Query("data") query: String
    ): OverpassResponse
}