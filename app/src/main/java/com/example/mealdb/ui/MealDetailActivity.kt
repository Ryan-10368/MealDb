package com.example.mealdb.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mealdb.R
import com.example.mealdb.adapter.IngredientAdapter
import com.example.mealdb.data.Meal
import com.example.mealdb.data.MealViewModel
import com.example.mealdb.databinding.ActivityMealDetailBinding
import kotlinx.coroutines.launch

class MealDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEAL_ID = "extra_meal_id"
    }

    private lateinit var binding: ActivityMealDetailBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var ingredientAdapter: IngredientAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MealViewModel::class.java]
        setupRecyclerView()
        observeViewModel()

        val mealId = intent.getStringExtra(EXTRA_MEAL_ID)
        mealId?.let { loadMealDetails(it) }
    }

    private fun setupRecyclerView() {
        ingredientAdapter = IngredientAdapter()
        binding.recyclerViewIngredients.apply {
            adapter = ingredientAdapter
            layoutManager = LinearLayoutManager(this@MealDetailActivity)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            // You can add a progress bar in the detail layout if needed
            // Example: binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                // Handle error display (could show a Toast or error message)
                // Example: Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMealDetails(mealId: String) {
        lifecycleScope.launch {
            try {
                Log.d("MealDetailActivity", "Loading meal details for ID: $mealId")

                // Force fetch from API to get complete meal details
                val meal = viewModel.getMealById(mealId)

                if (meal != null) {
                    Log.d("MealDetailActivity", "Meal loaded successfully: ${meal.strMeal}")
                    displayMeal(meal)
                } else {
                    Log.w("MealDetailActivity", "No meal data received for ID: $mealId")
                    // Handle case where meal details couldn't be loaded
                    binding.textViewMealDetailName.text = "Meal details not available"
                    // You could also show a Toast here
                }
            } catch (e: Exception) {
                // Handle any exceptions during loading
                Log.e("MealDetailActivity", "Error loading meal details for ID: $mealId", e)
                e.printStackTrace()
                binding.textViewMealDetailName.text = "Error loading meal details: ${e.message}"
                // You could also show a Toast here
            }
        }
    }

    private fun displayMeal(meal: Meal) {
        binding.apply {
            // Safely display meal name
            textViewMealDetailName.text = meal.strMeal ?: "Unknown Meal"

            // Safely display category and area
            textViewMealDetailCategory.text = try {
                getString(R.string.category_format, meal.strCategory ?: getString(R.string.unknown))
            } catch (_: Exception) {
                "Category: ${meal.strCategory ?: "Unknown"}"
            }

            textViewMealDetailArea.text = try {
                getString(R.string.area_format, meal.strArea ?: getString(R.string.unknown))
            } catch (_: Exception) {
                "Area: ${meal.strArea ?: "Unknown"}"
            }

            textViewMealDetailInstructions.text = try {
                meal.strInstructions ?: getString(R.string.no_instructions_available)
            } catch (_: Exception) {
            } as CharSequence?

            // Load image safely
            meal.strMealThumb?.let { imageUrl ->
                try {
                    Glide.with(this@MealDetailActivity)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(imageViewMealDetail)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Extract and display ingredients safely
            try {
                val ingredients = extractIngredients(meal)
                ingredientAdapter.submitList(ingredients)
            } catch (e: Exception) {
                e.printStackTrace()
                // Submit empty list if extraction fails
                ingredientAdapter.submitList(emptyList())
            }
        }

        // Setup favorite button after displaying meal
        setupFavoriteButton(meal)
    }

    private fun extractIngredients(meal: Meal): List<Pair<String, String>> {
        val ingredients = mutableListOf<Pair<String, String>>()

        // Use the lists to create pairs
        val ingredientsList = listOf(
            meal.strIngredient1, meal.strIngredient2, meal.strIngredient3, meal.strIngredient4, meal.strIngredient5,
            meal.strIngredient6, meal.strIngredient7, meal.strIngredient8, meal.strIngredient9, meal.strIngredient10,
            meal.strIngredient11, meal.strIngredient12, meal.strIngredient13, meal.strIngredient14, meal.strIngredient15,
            meal.strIngredient16, meal.strIngredient17, meal.strIngredient18, meal.strIngredient19, meal.strIngredient20
        )

        val measurementsList = listOf(
            meal.strMeasure1, meal.strMeasure2, meal.strMeasure3, meal.strMeasure4, meal.strMeasure5,
            meal.strMeasure6, meal.strMeasure7, meal.strMeasure8, meal.strMeasure9, meal.strMeasure10,
            meal.strMeasure11, meal.strMeasure12, meal.strMeasure13, meal.strMeasure14, meal.strMeasure15,
            meal.strMeasure16, meal.strMeasure17, meal.strMeasure18, meal.strMeasure19, meal.strMeasure20
        )

        // Create pairs of measurement and ingredient (to match your layout order)
        ingredientsList.zip(measurementsList) { ingredient, measurement ->
            if (!ingredient.isNullOrBlank()) {
                val measure = if (measurement.isNullOrBlank()) "" else measurement.trim()
                val ingredientName = ingredient.trim()
                ingredients.add(measure to ingredientName)
            }
        }

        return ingredients
    }

    private fun setupFavoriteButton(meal: Meal) {
        // Check if meal is already favorite
        viewModel.isFavorite(meal.idMeal ?: "").observe(this) { isFavorite ->
            updateFavoriteButton(isFavorite)
        }

        // Handle favorite button click
        binding.buttonToggleFavorite.setOnClickListener {
            viewModel.toggleFavorite(meal)
            Toast.makeText(this, "Favorite updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        binding.buttonToggleFavorite.apply {
            if (isFavorite) {
                text = getString(R.string.remove_from_favorites)
                // For MaterialButton, use setIconResource
                // For regular Button, use setCompoundDrawablesWithIntrinsicBounds
                try {
                    // Try MaterialButton method first
                    (this as? com.google.android.material.button.MaterialButton)?.setIconResource(R.drawable.ic_favorite_filled)
                        ?: setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_filled, 0, 0, 0)
                } catch (_: Exception) {
                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_filled, 0, 0, 0)
                }
            } else {
                text = getString(R.string.add_to_favorites)
                try {
                    // Try MaterialButton method first
                    (this as? com.google.android.material.button.MaterialButton)?.setIconResource(R.drawable.ic_favorite_border)
                        ?: setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_border, 0, 0, 0)
                } catch (_: Exception) {
                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_border, 0, 0, 0)
                }
            }
        }
    }
}