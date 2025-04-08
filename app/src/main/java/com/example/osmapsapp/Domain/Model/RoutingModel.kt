package com.example.osmapsapp.Domain.Model

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val displayName: String? = null
)

data class StopInfo(
    val name: String,
    val address: String,
    val point: RoutePoint
)

data class RouteResult(
    val points: List<RoutePoint>,
    val stops: List<StopInfo>,
    val routeType: RouteType
)

enum class RouteType {
    DRIVING, CYCLING, WALKING
}