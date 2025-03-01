package com.example.osmapsapp

import com.example.osmapsapp.Models.GraphHopperResponse
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

val graphHopperService: GraphHopperService by lazy {
    Retrofit.Builder()
        .baseUrl("https://graphhopper.com/api/1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GraphHopperService::class.java)
}

interface GraphHopperService {
    @GET("route")
    fun getRoute(
        @Query("point") points: List<String>,
        @Query("vehicle") vehicle: String = "car",
        @Query("pass_through") passThrough: Boolean = true,
        @Query("ch.disable") chDisable: Boolean = true,
        @Query("key") apiKey: String
    ): Call<GraphHopperResponse>
}

