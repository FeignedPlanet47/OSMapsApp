package com.example.osmapsapp.Presentation.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.osmapsapp.Domain.Model.RouteResult
import com.example.osmapsapp.Domain.Model.RouteType
import com.example.osmapsapp.Domain.Repository.GeocodingRepository
import com.example.osmapsapp.Domain.Repository.RoutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val geocodingRepository: GeocodingRepository,
    private val routingRepository: RoutingRepository
) : ViewModel() {

    private val _routeState = MutableStateFlow<UiState<RouteResult>>(UiState.Idle)
    val routeState: StateFlow<UiState<RouteResult>> = _routeState

    fun calculateRoute(
        startQuery: String,
        endQuery: String,
        maxDistanceKm: Double,
        selectedCategories: List<String>,
        routeType: RouteType
    ) {
        viewModelScope.launch {
            _routeState.value = UiState.Loading

            val startPoint = geocodingRepository.geocode(startQuery)
            val endPoint = geocodingRepository.geocode(endQuery)

            if (startPoint.isFailure || endPoint.isFailure) {
                _routeState.value = UiState.Error("Не удалось определить координаты точек")
                return@launch
            }

            val tempRoute = routingRepository.getRoute(
                startPoint.getOrThrow(),
                endPoint.getOrThrow(),
                routeType,
                emptyList()
            )

            if (tempRoute.isFailure) {
                _routeState.value = UiState.Error("Не удалось построить маршрут")
                return@launch
            }

            val stops = routingRepository.findStopsAlongRoute(
                startPoint.getOrThrow(),
                tempRoute.getOrThrow(),
                maxDistanceKm,
                selectedCategories
            )

            if (stops.isFailure) {
                _routeState.value = UiState.Error("Не удалось найти остановки")
                return@launch
            }

            val finalRoute = routingRepository.getRoute(
                startPoint.getOrThrow(),
                endPoint.getOrThrow(),
                routeType,
                stops.getOrThrow().map { it.point }
            )

            if (finalRoute.isFailure) {
                _routeState.value = UiState.Error("Не удалось построить финальный маршрут")
                return@launch
            }

            _routeState.value = UiState.Success(
                RouteResult(
                    points = finalRoute.getOrThrow(),
                    stops = stops.getOrThrow(),
                    routeType = routeType
                )
            )
        }
    }
}

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}