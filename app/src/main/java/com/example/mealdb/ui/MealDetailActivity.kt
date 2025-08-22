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

    // =================================
    // CONSTANTS
    // =================================

    companion object {
        const val EXTRA_MEAL_ID = "extra_meal_id"
        private const val TAG = "MealDetailActivity"
    }

    // =================================
    // PROPERTIES
    // =================================

    private lateinit var binding: ActivityMealDetailBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var ingredientAdapter: IngredientAdapter

    // =================================
    // LIFECYCLE METHODS
    // =================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        loadMealFromIntent()
    }

    override fun onSupportNavigateUp(): Boolean {
        Log.d(TAG, "Toolbar back button pressed")
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Log.d(TAG, "System back button pressed - finishing activity")
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MealDetailActivity destroyed")
    }

    // =================================
// INITIALIZATION
// =================================

    private fun initializeComponents() {
        initializeViewModel()
        setupRecyclerView()
        observeViewModel()

    }
    private fun initializeViewModel() {
        viewModel = ViewModelProvider(this)[MealViewModel::class.java]
    }

    private fun setupRecyclerView() {
        ingredientAdapter = IngredientAdapter()
        binding.recyclerViewIngredients.apply {
            adapter = ingredientAdapter
            layoutManager = LinearLayoutManager(this@MealDetailActivity)
        }
    }

    private fun loadMealFromIntent() {
        val mealId = intent.getStringExtra(EXTRA_MEAL_ID)
        mealId?.let {
            loadMealDetails(it)
        } ?: handleInvalidMealId()
    }

    // =================================
    // VIEWMODEL OBSERVERS
    // =================================

    private fun observeViewModel() {
        observeLoadingState()
        observeErrorState()
    }

    private fun observeLoadingState() {
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "Loading state: $isLoading")
            // You can add progress bar visibility here if needed
            // binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun observeErrorState() {
        viewModel.error.observe(this) { error ->
            error?.let {
                Log.e(TAG, "Error observed: $error")
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    // =================================
    // DATA LOADING
    // =================================

    private fun loadMealDetails(mealId: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading meal details for ID: $mealId")
                val meal = viewModel.getMealById(mealId)
                handleMealResult(meal)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading meal details for ID: $mealId", e)
                handleMealLoadFailure("Error loading meal details: ${e.message}")
            }
        }
    }

    private fun handleMealResult(meal: Meal?) {
        if (meal != null) {
            Log.d(TAG, "Meal loaded successfully: ${meal.strMeal}")
            displayMeal(meal)
        } else {
            Log.w(TAG, "No meal data received")
            handleMealLoadFailure("Meal details not available")
        }
    }

    private fun handleMealLoadFailure(message: String) {
        binding.textViewMealDetailName.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleInvalidMealId() {
        Log.e(TAG, "No meal ID provided in intent")
        handleMealLoadFailure("Invalid meal ID")
    }

    // =================================
    // UI DISPLAY METHODS
    // =================================

    private fun displayMeal(meal: Meal) {
        displayBasicInfo(meal)
        displayImage(meal)
        displayIngredients(meal)
        setupFavoriteButton(meal)
    }

    private fun displayBasicInfo(meal: Meal) {
        binding.apply {
            textViewMealDetailName.text = meal.strMeal ?: "Unknown Meal"
            textViewMealDetailCategory.text = formatCategoryText(meal.strCategory)
            textViewMealDetailArea.text = formatAreaText(meal.strArea)
            textViewMealDetailInstructions.text = formatInstructions(meal.strInstructions)
        }
    }

    private fun displayImage(meal: Meal) {
        meal.strMealThumb?.let { imageUrl ->
            try {
                Glide.with(this@MealDetailActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(binding.imageViewMealDetail)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image", e)
            }
        }
    }

    private fun displayIngredients(meal: Meal) {
        try {
            val ingredients = extractIngredients(meal)
            ingredientAdapter.submitList(ingredients)
            Log.d(TAG, "Displayed ${ingredients.size} ingredients")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ingredients", e)
            ingredientAdapter.submitList(emptyList())
        }
    }

    // =================================
    // TEXT FORMATTING
    // =================================

    private fun formatCategoryText(category: String?): String {
        return try {
            getString(R.string.category_format, category ?: getString(R.string.unknown))
        } catch (_: Exception) {
            "Category: ${category ?: "Unknown"}"
        }
    }

    private fun formatAreaText(area: String?): String {
        return try {
            getString(R.string.area_format, area ?: getString(R.string.unknown))
        } catch (_: Exception) {
            "Area: ${area ?: "Unknown"}"
        }
    }

    private fun formatInstructions(instructions: String?): String {
        return try {
            instructions ?: getString(R.string.no_instructions_available)
        } catch (_: Exception) {
            "No instructions available"
        }
    }

    // =================================
    // INGREDIENTS PROCESSING
    // =================================

    private fun extractIngredients(meal: Meal): List<Pair<String, String>> {
        val ingredients = mutableListOf<Pair<String, String>>()
        val ingredientsList = getIngredientsList(meal)
        val measurementsList = getMeasurementsList(meal)

        ingredientsList.zip(measurementsList) { ingredient, measurement ->
            if (!ingredient.isNullOrBlank()) {
                val measure = if (measurement.isNullOrBlank()) "" else measurement.trim()
                val ingredientName = ingredient.trim()
                ingredients.add(measure to ingredientName)
            }
        }

        return ingredients
    }

    private fun getIngredientsList(meal: Meal): List<String?> {
        return listOf(
            meal.strIngredient1, meal.strIngredient2, meal.strIngredient3, meal.strIngredient4, meal.strIngredient5,
            meal.strIngredient6, meal.strIngredient7, meal.strIngredient8, meal.strIngredient9, meal.strIngredient10,
            meal.strIngredient11, meal.strIngredient12, meal.strIngredient13, meal.strIngredient14, meal.strIngredient15,
            meal.strIngredient16, meal.strIngredient17, meal.strIngredient18, meal.strIngredient19, meal.strIngredient20
        )
    }

    private fun getMeasurementsList(meal: Meal): List<String?> {
        return listOf(
            meal.strMeasure1, meal.strMeasure2, meal.strMeasure3, meal.strMeasure4, meal.strMeasure5,
            meal.strMeasure6, meal.strMeasure7, meal.strMeasure8, meal.strMeasure9, meal.strMeasure10,
            meal.strMeasure11, meal.strMeasure12, meal.strMeasure13, meal.strMeasure14, meal.strMeasure15,
            meal.strMeasure16, meal.strMeasure17, meal.strMeasure18, meal.strMeasure19, meal.strMeasure20
        )
    }

    // =================================
    // FAVORITES FUNCTIONALITY
    // =================================

    private fun setupFavoriteButton(meal: Meal) {
        observeFavoriteStatus(meal)
        handleFavoriteButtonClick(meal)
    }

    private fun observeFavoriteStatus(meal: Meal) {
        viewModel.isFavorite(meal.idMeal ?: "").observe(this) { isFavorite ->
            updateFavoriteButton(isFavorite)
        }
    }

    private fun handleFavoriteButtonClick(meal: Meal) {
        binding.buttonToggleFavorite.setOnClickListener {
            viewModel.toggleFavorite(meal)
            Toast.makeText(this, "Favorite updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        binding.buttonToggleFavorite.apply {
            if (isFavorite) {
                setFavoriteButtonState(
                    textResId = R.string.remove_from_favorites,
                    iconResId = R.drawable.ic_favorite_filled
                )
            } else {
                setFavoriteButtonState(
                    textResId = R.string.add_to_favorites,
                    iconResId = R.drawable.ic_favorite_border
                )
            }
        }
    }

    private fun setFavoriteButtonState(textResId: Int, iconResId: Int) {
        binding.buttonToggleFavorite.apply {
            text = getString(textResId)
            try {
                (this as? com.google.android.material.button.MaterialButton)?.setIconResource(iconResId)
                    ?: setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0)
            } catch (_: Exception) {
                setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0)
            }
        }
    }
}