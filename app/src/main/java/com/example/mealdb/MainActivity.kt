package com.example.mealdb

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealdb.adapter.FavoriteMealAdapter
import com.example.mealdb.adapter.MealAdapter
import com.example.mealdb.data.Meal
import com.example.mealdb.data.MealViewModel
import com.example.mealdb.data.SortOption
import com.example.mealdb.data.toMeal
import com.example.mealdb.databinding.ActivityMainBinding
import com.example.mealdb.ui.MealDetailActivity

class MainActivity : AppCompatActivity() {

    private val RANDOM_MEALS_COUNT = 10

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var mealAdapter: MealAdapter
    private lateinit var favoritesAdapter: FavoriteMealAdapter

    // Keep track of favorites for adapter
    private var favoriteMealIds: Set<String> = emptySet()

    // Search state
    private var currentSearchQuery: String = ""
    private var isShowingSearchResults: Boolean = false

    private val sortingOptions = listOf(
        "Default",
        "Name (A-Z)",
        "Name (Z-A)",
        "Category",
        "Area",
        "Favorites First"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("MainActivity", "MainActivity created")

        // Initialize ViewModel first
        viewModel = ViewModelProvider(this)[MealViewModel::class.java]

        // Setup UI components
        setupRecyclerViews()
        setupSearchView()
        setupSortingSpinner()
        setupRandomMealButton()

        // Setup observers - THIS IS CRITICAL
        observeViewModel()

        Log.d("MainActivity", "MainActivity setup complete - ViewModel will auto-load data")
    }

    private fun setupRecyclerViews() {
        // Setup main meals adapter
        mealAdapter = MealAdapter(
            onMealClick = { meal ->
                Log.d("MainActivity", "Meal clicked: ${meal.strMeal}")
                navigateToMealDetail(meal)
            },
            onFavoriteClick = { meal ->
                Log.d("MainActivity", "Favorite toggled for: ${meal.strMeal}")
                viewModel.toggleFavorite(meal)
                Toast.makeText(this, "Favorite updated!", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewMeals.apply {
            adapter = mealAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
        }

        // Setup favorites adapter
        favoritesAdapter = FavoriteMealAdapter(
            onMealClick = { favoriteMeal ->
                val meal = favoriteMeal.toMeal()
                navigateToMealDetail(meal)
            },
            onFavoriteClick = { favoriteMeal ->
                val meal = favoriteMeal.toMeal()
                viewModel.toggleFavorite(meal)
                Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewFavorites.apply {
            adapter = favoritesAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            isNestedScrollingEnabled = false
        }

        Log.d("MainActivity", "RecyclerViews setup complete")
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchQuery ->
                    val trimmedQuery = searchQuery.trim()
                    if (trimmedQuery.isNotBlank()) {
                        Log.d("MainActivity", "Search submitted: $trimmedQuery")
                        performSearch(trimmedQuery)
                    } else {
                        // If empty query is submitted, reset to random meals
                        resetToRandomMeals()
                    }
                }
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Remove the automatic reset behavior
                // Only clear search state when text is completely empty and we were showing search results
                if (newText.isNullOrBlank() && isShowingSearchResults) {
                    // Reset search state but don't automatically load random meals
                    currentSearchQuery = ""
                    isShowingSearchResults = false
                    // Optionally you can choose to reset here or wait for user to press enter
                    // resetToRandomMeals() // Uncomment if you want to auto-reset when cleared
                }
                return true
            }
        })
    }

    private fun setupRandomMealButton() {
        binding.buttonRandomMeal.setOnClickListener {
            Log.d("MainActivity", "Random meal button clicked")
            resetToRandomMeals()
        }
    }

    private fun setupSortingSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortingOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = adapter

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sortOption = when (position) {
                    0 -> SortOption.DEFAULT
                    1 -> SortOption.NAME_ASC
                    2 -> SortOption.NAME_DESC
                    3 -> SortOption.CATEGORY
                    4 -> SortOption.AREA
                    5 -> SortOption.DEFAULT // Favorites first - we'll handle this custom
                    else -> SortOption.DEFAULT
                }

                Log.d("MainActivity", "Sort selected: $sortOption at position $position")

                if (position == 5) {
                    // Custom favorites first sorting
                    sortByFavoritesFirst()
                } else {
                    viewModel.sortMeals(sortOption)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeViewModel() {
        Log.d("MainActivity", "Setting up ViewModel observers")

        // CRITICAL: Observe meals - this is what updates your RecyclerView
        viewModel.meals.observe(this) { meals ->
            Log.d("MainActivity", "✅ Received ${meals.size} meals in UI")

            if (meals.isNotEmpty()) {
                // Update the adapter with new meals
                mealAdapter.submitList(meals)
                // Update favorites state in adapter
                mealAdapter.updateFavorites(favoriteMealIds)

                // Log the meals for debugging
                meals.take(3).forEach { meal ->
                    Log.d("MainActivity", "  - ${meal.strMeal} (${meal.strCategory ?: "No Category"})")
                }

                if (isShowingSearchResults && currentSearchQuery.isNotEmpty()) {
                    Toast.makeText(this, "Found ${meals.size} meals for '$currentSearchQuery'", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("MainActivity", "No meals to display")
                mealAdapter.submitList(emptyList())

                if (isShowingSearchResults && currentSearchQuery.isNotEmpty()) {
                    Toast.makeText(this, "No meals found for '$currentSearchQuery'", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d("MainActivity", "Loading state: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe errors
        viewModel.error.observe(this) { error ->
            error?.let {
                Log.e("MainActivity", "Error: $error")
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        // Observe favorites
        // In your MainActivity observeViewModel() method, update the favorites observer:
        viewModel.favoritesMeals.observe(this) { favorites ->
            Log.d("MainActivity", "🔄 FAVORITES CHANGED: ${favorites.size} favorites")

            // Log each favorite for debugging
            favorites.forEach { fav ->
                Log.d("MainActivity", "  - ${fav.strMeal} (ID: ${fav.idMeal})")
            }

            // Update favorites set for main adapter
            favoriteMealIds = favorites.map { it.idMeal }.toSet()
            Log.d("MainActivity", "📝 Updated favoriteMealIds: $favoriteMealIds")

            // CRITICAL: Update adapter with new favorites
            mealAdapter.updateFavorites(favoriteMealIds)
            Log.d("MainActivity", "✅ Called updateFavorites() on adapter")

            // Update favorites RecyclerView
            favoritesAdapter.submitList(favorites)

            // Update favorites section visibility
            updateFavoritesVisibility(favorites.size)
        }

        Log.d("MainActivity", "ViewModel observers setup complete")
    }

    private fun updateFavoritesVisibility(favoritesCount: Int) {
        if (favoritesCount > 0) {
            binding.textViewNoFavorites.visibility = View.GONE
            binding.textViewFavoritesHeader.visibility = View.VISIBLE
            binding.recyclerViewFavorites.visibility = View.VISIBLE
            binding.divider.visibility = View.VISIBLE
            binding.textViewFavoritesHeader.text = "❤️ Your Favorites ($favoritesCount)"
        } else {
            binding.textViewNoFavorites.visibility = View.VISIBLE
            binding.textViewFavoritesHeader.visibility = View.GONE
            binding.recyclerViewFavorites.visibility = View.GONE
            binding.divider.visibility = View.GONE
        }
    }

    private fun performSearch(query: String) {
        currentSearchQuery = query
        isShowingSearchResults = true

        // Enhanced search with cuisine and category detection
        val normalizedQuery = query.lowercase()

        // Check for cuisine aliases
        val cuisineMap = mapOf(
            "chinese" to "chinese", "china" to "chinese",
            "italian" to "italian", "italy" to "italian",
            "mexican" to "mexican", "mexico" to "mexican",
            "indian" to "indian", "india" to "indian",
            "american" to "american", "usa" to "american",
            "british" to "british", "uk" to "british", "english" to "british",
            "french" to "french", "france" to "french",
            "japanese" to "japanese", "japan" to "japanese",
            "thai" to "thai", "thailand" to "thai"
        )

        // Check for category aliases
        val categoryMap = mapOf(
            "beef" to "beef", "chicken" to "chicken", "pork" to "pork",
            "seafood" to "seafood", "fish" to "seafood",
            "pasta" to "pasta", "dessert" to "dessert", "desserts" to "dessert",
            "vegan" to "vegan", "vegetarian" to "vegetarian",
            "breakfast" to "breakfast"
        )

        when {
            cuisineMap.containsKey(normalizedQuery) -> {
                val cuisine = cuisineMap[normalizedQuery]!!
                viewModel.searchMealsByArea(cuisine)
                Toast.makeText(this, "Searching $cuisine cuisine...", Toast.LENGTH_SHORT).show()
            }
            categoryMap.containsKey(normalizedQuery) -> {
                val category = categoryMap[normalizedQuery]!!
                viewModel.searchMealsByCategory(category)
                Toast.makeText(this, "Searching $category category...", Toast.LENGTH_SHORT).show()
            }
            else -> {
                viewModel.searchMealsEnhanced(query)
            }
        }
    }

    private fun resetToRandomMeals() {
        Log.d("MainActivity", "Resetting to $RANDOM_MEALS_COUNT random meals")
        binding.searchView.setQuery("", false)
        currentSearchQuery = ""
        isShowingSearchResults = false
        binding.spinnerSort.setSelection(0)

        viewModel.getMultipleRandomMeals(RANDOM_MEALS_COUNT)
        Toast.makeText(this, "Loading $RANDOM_MEALS_COUNT random meals...", Toast.LENGTH_SHORT).show()
    }

    private fun sortByFavoritesFirst() {
        val currentMeals = viewModel.meals.value ?: return

        val sortedList = currentMeals.sortedWith { meal1, meal2 ->
            val isMeal1Favorite = favoriteMealIds.contains(meal1.idMeal)
            val isMeal2Favorite = favoriteMealIds.contains(meal2.idMeal)

            when {
                isMeal1Favorite && !isMeal2Favorite -> -1
                !isMeal1Favorite && isMeal2Favorite -> 1
                else -> (meal1.strMeal ?: "").compareTo(meal2.strMeal ?: "", ignoreCase = true)
            }
        }

        mealAdapter.submitList(sortedList)
    }

    private fun navigateToMealDetail(meal: Meal) {
        val intent = Intent(this, MealDetailActivity::class.java)
        intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.idMeal)
        startActivity(intent)
    }
}