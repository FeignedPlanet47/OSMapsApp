package com.example.osmapsapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Rule

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity2::class.java)
    @Test
    fun mapIsDisplayed() {
        onView(withId(R.id.map)).check(matches(isDisplayed()))
    }
    @Test
    fun testRouteDrawnBetweenTwoPoints() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity2::class.java).apply {
            putExtra("startPoint", "Москва, Красная площадь")
            putExtra("endPoint", "Санкт-Петербург, Невский проспект")
            putExtra("maxDistance", 200.0)
            putExtra("selectedCategories", arrayListOf("Кафе", "Отель"))
            putExtra("routeType", "DRIVING")
        }
        ActivityScenario.launch<MainActivity2>(intent)

        onView(withId(R.id.map)).check(matches(isDisplayed()))
    }

}