package com.example.osmapsapp.Data.Remote.Dto

import com.google.gson.annotations.SerializedName

data class NominatimGeocodingResponse(
    @SerializedName("lat") val latitude: String,
    @SerializedName("lon") val longitude: String,
    @SerializedName("display_name") val displayName: String?
)

data class OverpassResponse(
    val elements: List<OverpassElement>
)

data class OverpassElement(
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>?
)