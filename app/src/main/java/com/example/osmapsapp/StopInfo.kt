package com.example.osmapsapp

import org.osmdroid.util.GeoPoint

data class StopInfo(
    val name: String,
    val address: String,
    val point: GeoPoint
)