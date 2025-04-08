package com.example.osmapsapp.Domain.Repository

import com.example.osmapsapp.Domain.Model.RoutePoint
import com.example.osmapsapp.Domain.Model.RouteType
import com.example.osmapsapp.Domain.Model.StopInfo

interface GeocodingRepository {
    suspend fun geocode(query: String): Result<RoutePoint>
    suspend fun reverseGeocode(point: RoutePoint): Result<String>
}

interface RoutingRepository {
    suspend fun getRoute(
        start: RoutePoint,
        end: RoutePoint,
        routeType: RouteType,
        stops: List<RoutePoint>
    ): Result<List<RoutePoint>>

    suspend fun findNearbyPOIs(
        location: RoutePoint,
        nextStop: RoutePoint,
        maxDistanceKm: Double,
        categories: List<String>
    ): Result<List<StopInfo>>

    suspend fun findStopsAlongRoute(
        startPoint: RoutePoint,
        routePoints: List<RoutePoint>,
        maxDistanceKm: Double,
        selectedCategories: List<String>
    ): Result<List<StopInfo>>
}