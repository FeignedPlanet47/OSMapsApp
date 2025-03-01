package com.example.osmapsapp

import com.example.osmapsapp.Models.NominatimResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimService {
    @GET("search")
    fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1
    ): Call<List<NominatimResponse>>
}