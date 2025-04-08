package com.example.osmapsapp.Data.Remote.Dto

data class OSRMResponse(
    val routes: List<OSRMRoute>
)

data class OSRMRoute(
    val geometry: OSRMGeometry
)

data class OSRMGeometry(
    val coordinates: List<List<Double>>
)