package com.example.osmapsapp

import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*
import org.osmdroid.util.GeoPoint

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @Test
    fun geocode_validAddress_returnsGeoPoint() = runBlocking {
        val point = activity.geocode("Москва, Красная площадь")
        assertNotNull(point)
        assertTrue(point!!.latitude in 55.0..56.0)
    }
    @Test
    fun reverseGeocode_validPoint_returnsAddress() = runBlocking {
        val point = GeoPoint(55.7522, 37.6156) // Москва
        val address = activity.reverseGeocode(point)
        assertNotNull(address)
        assertTrue(address!!.contains("Москва", ignoreCase = true))
    }
    @Test
    fun getRoute_withoutStops_returnsNonEmptyRoute() = runBlocking {
        val start = GeoPoint(55.7522, 37.6156)
        val end = GeoPoint(59.9386, 30.3141)
        val route = activity.getRoute(start, end, "driving", emptyList())
        assertTrue(route.size > 2)
    }
    @Test
    fun getNearestPOI_returnsStopInfoOrNull() = runBlocking {
        val location = GeoPoint(55.7522, 37.6156)
        val next = GeoPoint(55.7532, 37.6166)
        val stop = activity.getNearestPOI(location, next, 5.0, listOf("Кафе"))
        assertTrue(stop == null || stop.name.contains("Кафе") || stop.address.isNotEmpty())
    }
    @Test
    fun findStopsAlongRoute_addsStartAndEndPoints() = runBlocking {
        val start = GeoPoint(55.7522, 37.6156)
        val end = GeoPoint(55.7622, 37.6256)
        val route = listOf(start, end)
        val stops = activity.findStopsAlongRoute(start, route, 0.1, listOf("Кафе"))
        assertEquals("Старт", stops.first().name)
        assertEquals("Конец", stops.last().name)
    }
    @Test
    fun findBoundingBox_returnsCorrectBox() {
        val points = listOf(
            GeoPoint(55.0, 37.0),
            GeoPoint(56.0, 38.0),
            GeoPoint(54.5, 36.5)
        )
        val box = activity.findBoundingBox(points)
        assertEquals(56.0, box.latNorth, 0.1)
        assertEquals(36.5, box.lonWest, 0.1)
    }
}