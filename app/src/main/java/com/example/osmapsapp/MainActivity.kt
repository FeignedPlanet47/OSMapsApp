package com.example.osmapsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.osmapsapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val selectedCategories = mutableSetOf<String>()
    private var currentRouteType: MainActivity2.RouteType = MainActivity2.RouteType.DRIVING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentRouteType = when (checkedId) {
                R.id.carRouteRadio -> MainActivity2.RouteType.DRIVING
                R.id.bikeRouteRadio -> MainActivity2.RouteType.CYCLING
                R.id.footRouteRadio -> MainActivity2.RouteType.WALKING
                else -> MainActivity2.RouteType.DRIVING
            }
        }

        binding.selectCategoriesButton.setOnClickListener {
            showCategorySelectionDialog()
        }

        binding.goToRouteButton.setOnClickListener {
            val start = binding.startPointInput.text.toString()
            val end = binding.endPointInput.text.toString()
            val maxHours = binding.maxDistanceInput.text.toString().toDoubleOrNull()

            if (start.isBlank() || end.isBlank() || maxHours == null) {
                Toast.makeText(this, "Заполните все поля корректно", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedCategories.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одну категорию", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, MainActivity2::class.java).apply {
                putExtra("startPoint", start)
                putExtra("endPoint", end)
                putExtra("maxDistance", maxHours)
                putExtra("selectedCategories", ArrayList(selectedCategories))
                putExtra("routeType", currentRouteType.name)
            }
            startActivity(intent)
        }
    }

    private fun showCategorySelectionDialog() {
        val categories = MainActivity2.poiCategories.map { it.first }.toTypedArray()
        val checkedItems = BooleanArray(categories.size) { i ->
            selectedCategories.contains(MainActivity2.poiCategories[i].first)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите категории остановок")
            .setMultiChoiceItems(categories, checkedItems) { _, which, isChecked ->
                val category = MainActivity2.poiCategories[which].first
                if (isChecked) {
                    selectedCategories.add(category)
                } else {
                    selectedCategories.remove(category)
                }
                updateSelectedCategoriesText()
            }
            .setPositiveButton("Готово", null)
            .show()
    }

    private fun updateSelectedCategoriesText() {
        binding.selectedCategoriesText.text = when {
            selectedCategories.isEmpty() -> "Не выбрано"
            selectedCategories.size == 1 -> "Выбрано: ${selectedCategories.first()}"
            selectedCategories.size > 3 -> "Выбрано: ${selectedCategories.size} категорий"
            else -> "Выбрано: ${selectedCategories.joinToString(", ")}"
        }
    }
}
