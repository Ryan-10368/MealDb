package com.example.mealdb.data

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mealdb.R
import com.example.mealdb.adapter.IngredientAdapter
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
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                // Handle error display (could show a Toast or error message)
            }
        }
    }

    private fun loadMealDetails(mealId: String) {
        lifecycleScope.launch {
            val meal = viewModel.getMealById(mealId)
            meal?.let { displayMeal(it) }
        }
    }

    private fun displayMeal(meal: Meal) {
        binding.apply {
            textViewMealDetailName.text = meal.strMeal
            textViewMealDetailCategory.text = getString(R.string.category_format, meal.strCategory ?: getString(R.string.unknown))
            textViewMealDetailArea.text = getString(R.string.area_format, meal.strArea ?: getString(R.string.unknown))
            textViewMealDetailInstructions.text = meal.strInstructions ?: getString(R.string.no_instructions_available)

            // Load image
            meal.strMealThumb?.let { imageUrl ->
                Glide.with(this@MealDetailActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(imageViewMealDetail)
            }

            // Extract and display ingredients
            val ingredients = extractIngredients(meal)
            ingredientAdapter.submitList(ingredients)
        }
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
}