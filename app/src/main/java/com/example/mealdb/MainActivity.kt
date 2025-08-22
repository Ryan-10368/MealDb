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

    // =================================
    // CONSTANTS
    // =================================

    private companion object {
        const val RANDOM_MEALS_COUNT = 10
        const val TAG = "MainActivity"

        // SavedState keys
        const val KEY_SEARCH_QUERY = "currentSearchQuery"
        const val KEY_SHOWING_SEARCH_RESULTS = "isShowingSearchResults"
        const val KEY_CURRENT_MEAL_IDS = "currentMealIds"
    }

    // =================================
    // PROPERTIES
    // =================================

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var mealAdapter: MealAdapter
    private lateinit var favoritesAdapter: FavoriteMealAdapter

    // State tracking
    private var favoriteMealIds: Set<String> = emptySet()
    private var currentSearchQuery: String = ""
    private var isShowingSearchResults: Boolean = false

    // Sorting options
    // Sorting options
    private val sortingOptions = listOf(
        "Default",
        "Name (A-Z)",
        "Name (Z-A)",
        "Category",
        "Area"
    )

    // =================================
    // LIFECYCLE METHODS
    // =================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "MainActivity created")

        initializeViewModel()
        setupUI()
        observeViewModel()

        Log.d(TAG, "MainActivity setup complete - ViewModel will auto-load data")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "Saving state - query: '$currentSearchQuery', showingResults: $isShowingSearchResults")

        outState.putString(KEY_SEARCH_QUERY, currentSearchQuery)
        outState.putBoolean(KEY_SHOWING_SEARCH_RESULTS, isShowingSearchResults)

        // Save current meals list to avoid re-fetching
        viewModel.meals.value?.let { meals ->
            outState.putStringArray(KEY_CURRENT_MEAL_IDS, meals.map { it.idMeal ?: "" }.toTypedArray())
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        currentSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "")
        isShowingSearchResults = savedInstanceState.getBoolean(KEY_SHOWING_SEARCH_RESULTS, false)

        Log.d(TAG, "Restoring state - query: '$currentSearchQuery', showingResults: $isShowingSearchResults")

        restoreSearchState()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== RESUME DEBUG ===")
        Log.d(TAG, "currentSearchQuery: '$currentSearchQuery'")
        Log.d(TAG, "isShowingSearchResults: $isShowingSearchResults")
        Log.d(TAG, "ViewModel debug: ${viewModel.getDebugInfo()}")
        Log.d(TAG, "==================")

        restoreSearchIfNeeded()
    }

    // =================================
    // INITIALIZATION
    // =================================

    private fun initializeViewModel() {
        viewModel = ViewModelProvider(this)[MealViewModel::class.java]
    }

    private fun setupUI() {
        setupRecyclerViews()
        setupSearchView()
        setupSortingSpinner()
        setupRandomMealButton()
    }

    // =================================
    // UI SETUP METHODS
    // =================================

    private fun setupRecyclerViews() {
        setupMainRecyclerView()
        setupFavoritesRecyclerView()
        Log.d(TAG, "RecyclerViews setup complete")
    }

    private fun setupMainRecyclerView() {
        mealAdapter = MealAdapter(
            onMealClick = { meal ->
                Log.d(TAG, "Meal clicked: ${meal.strMeal}")
                navigateToMealDetail(meal)
            },
            onFavoriteClick = { meal ->
                Log.d(TAG, "Favorite toggled for: ${meal.strMeal}")
                viewModel.toggleFavorite(meal)
                Toast.makeText(this, "Favorite updated!", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewMeals.apply {
            adapter = mealAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupFavoritesRecyclerView() {
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
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                handleSearchSubmit(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                handleSearchTextChange(newText)
                return true
            }
        })
    }

    private fun setupSortingSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortingOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = adapter

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                handleSortSelection(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRandomMealButton() {
        binding.buttonRandomMeal.setOnClickListener {
            Log.d(TAG, "Random meal button clicked")
            resetToRandomMeals()
        }
    }

    // =================================
    // VIEWMODEL OBSERVERS
    // =================================

    private fun observeViewModel() {
        Log.d(TAG, "Setting up ViewModel observers")

        observeMeals()
        observeLoadingState()
        observeErrors()
        observeFavorites()

        Log.d(TAG, "ViewModel observers setup complete")
    }

    private fun observeMeals() {
        viewModel.meals.observe(this) { meals ->
            Log.d(TAG, "✅ Received ${meals.size} meals in UI")
            handleMealsUpdate(meals)
        }
    }

    private fun observeLoadingState() {
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "Loading state: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun observeErrors() {
        viewModel.error.observe(this) { error ->
            error?.let {
                Log.e(TAG, "Error: $error")
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun observeFavorites() {
        viewModel.favoritesMeals.observe(this) { favorites ->
            Log.d(TAG, "🔄 FAVORITES CHANGED: ${favorites.size} favorites")
            handleFavoritesUpdate(favorites)
        }
    }

    // =================================
    // DATA HANDLING
    // =================================

    private fun handleMealsUpdate(meals: List<Meal>) {
        if (meals.isNotEmpty()) {
            updateMealsAdapter(meals)
            logMealsForDebug(meals)
            showSearchResultsToast(meals.size)
        } else {
            handleEmptyMealsList()
        }
    }

    private fun updateMealsAdapter(meals: List<Meal>) {
        mealAdapter.submitList(meals)
        mealAdapter.updateFavorites(favoriteMealIds)
    }

    private fun logMealsForDebug(meals: List<Meal>) {
        meals.take(3).forEach { meal ->
            Log.d(TAG, "  - ${meal.strMeal} (${meal.strCategory ?: "No Category"})")
        }
    }

    private fun showSearchResultsToast(mealsCount: Int) {
        if (isShowingSearchResults && currentSearchQuery.isNotEmpty()) {
            Toast.makeText(this, "Found $mealsCount meals for '$currentSearchQuery'", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEmptyMealsList() {
        Log.d(TAG, "No meals to display")
        mealAdapter.submitList(emptyList())

        if (isShowingSearchResults && currentSearchQuery.isNotEmpty()) {
            Toast.makeText(this, "No meals found for '$currentSearchQuery'", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFavoritesUpdate(favorites: List<com.example.mealdb.data.FavoriteMeal>) {
        logFavoritesForDebug(favorites)
        updateFavoritesState(favorites)
        updateAdaptersWithFavorites(favorites)
        updateFavoritesVisibility(favorites.size)
    }

    private fun logFavoritesForDebug(favorites: List<com.example.mealdb.data.FavoriteMeal>) {
        favorites.forEach { fav ->
            Log.d(TAG, "  - ${fav.strMeal} (ID: ${fav.idMeal})")
        }
    }

    private fun updateFavoritesState(favorites: List<com.example.mealdb.data.FavoriteMeal>) {
        favoriteMealIds = favorites.map { it.idMeal }.toSet()
        Log.d(TAG, "📝 Updated favoriteMealIds: $favoriteMealIds")
    }

    private fun updateAdaptersWithFavorites(favorites: List<com.example.mealdb.data.FavoriteMeal>) {
        mealAdapter.updateFavorites(favoriteMealIds)
        favoritesAdapter.submitList(favorites)
        Log.d(TAG, "✅ Called updateFavorites() on adapter")
    }

    private fun updateFavoritesVisibility(favoritesCount: Int) {
        if (favoritesCount > 0) {
            showFavoritesSection(favoritesCount)
        } else {
            hideFavoritesSection()
        }
    }

    private fun showFavoritesSection(count: Int) {
        binding.apply {
            textViewNoFavorites.visibility = View.GONE
            textViewFavoritesHeader.visibility = View.VISIBLE
            recyclerViewFavorites.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
            textViewFavoritesHeader.text = "❤️ Your Favorites ($count)"
        }
    }

    private fun hideFavoritesSection() {
        binding.apply {
            textViewNoFavorites.visibility = View.VISIBLE
            textViewFavoritesHeader.visibility = View.GONE
            recyclerViewFavorites.visibility = View.GONE
            divider.visibility = View.GONE
        }
    }

    // =================================
    // SEARCH FUNCTIONALITY
    // =================================

    private fun handleSearchSubmit(query: String?) {
        query?.let { searchQuery ->
            val trimmedQuery = searchQuery.trim()
            if (trimmedQuery.isNotBlank()) {
                Log.d(TAG, "Search submitted: $trimmedQuery")
                performSearch(trimmedQuery)
            } else {
                resetToRandomMeals()
            }
        }
        binding.searchView.clearFocus()
    }

    private fun handleSearchTextChange(newText: String?) {
        // Reset search state when text is completely empty and we were showing search results
        if (newText.isNullOrBlank() && isShowingSearchResults) {
            currentSearchQuery = ""
            isShowingSearchResults = false
        }
    }

    private fun performSearch(query: String) {
        currentSearchQuery = query
        isShowingSearchResults = true

        val normalizedQuery = query.lowercase()

        when {
            getCuisineMapping(normalizedQuery) != null -> {
                searchByCuisine(normalizedQuery)
            }
            getCategoryMapping(normalizedQuery) != null -> {
                searchByCategory(normalizedQuery)
            }
            else -> {
                viewModel.searchMealsEnhanced(query)
            }
        }
    }

    private fun searchByCuisine(normalizedQuery: String) {
        val cuisine = getCuisineMapping(normalizedQuery)!!
        viewModel.searchMealsByArea(cuisine)
        Toast.makeText(this, "Searching $cuisine cuisine...", Toast.LENGTH_SHORT).show()
    }

    private fun searchByCategory(normalizedQuery: String) {
        val category = getCategoryMapping(normalizedQuery)!!
        viewModel.searchMealsByCategory(category)
        Toast.makeText(this, "Searching $category category...", Toast.LENGTH_SHORT).show()
    }

    private fun getCuisineMapping(query: String): String? {
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
        return cuisineMap[query]
    }

    private fun getCategoryMapping(query: String): String? {
        val categoryMap = mapOf(
            "beef" to "beef", "chicken" to "chicken", "pork" to "pork",
            "seafood" to "seafood", "fish" to "seafood",
            "pasta" to "pasta", "dessert" to "dessert", "desserts" to "dessert",
            "vegan" to "vegan", "vegetarian" to "vegetarian",
            "breakfast" to "breakfast"
        )
        return categoryMap[query]
    }

    private fun resetToRandomMeals() {
        Log.d(TAG, "Resetting to $RANDOM_MEALS_COUNT random meals")

        binding.searchView.setQuery("", false)
        currentSearchQuery = ""
        isShowingSearchResults = false
        binding.spinnerSort.setSelection(0)

        viewModel.getMultipleRandomMeals(RANDOM_MEALS_COUNT)
        Toast.makeText(this, "Loading $RANDOM_MEALS_COUNT random meals...", Toast.LENGTH_SHORT).show()
    }

    // =================================
    // SORTING FUNCTIONALITY
    // =================================

    private fun handleSortSelection(position: Int) {
        val sortOption = when (position) {
            0 -> SortOption.DEFAULT
            1 -> SortOption.NAME_ASC
            2 -> SortOption.NAME_DESC
            3 -> SortOption.CATEGORY
            4 -> SortOption.AREA
            else -> SortOption.DEFAULT
        }

        Log.d(TAG, "Sort selected: $sortOption at position $position")
        viewModel.sortMeals(sortOption)
    }


    // =================================
    // STATE RESTORATION
    // =================================

    private fun restoreSearchState() {
        if (currentSearchQuery.isNotEmpty()) {
            binding.searchView.setQuery(currentSearchQuery, false)
        }

        if (isShowingSearchResults && currentSearchQuery.isNotEmpty()) {
            if (viewModel.meals.value.isNullOrEmpty()) {
                performSearch(currentSearchQuery)
            }
        }
    }

    private fun restoreSearchIfNeeded() {
        if (isShowingSearchResults && currentSearchQuery.isNotEmpty()) {
            if (viewModel.meals.value.isNullOrEmpty()) {
                Log.d(TAG, "🔄 Restoring search for: $currentSearchQuery")
                performSearch(currentSearchQuery)
            } else {
                Log.d(TAG, "✅ Meals already present, no need to reload")
            }
        }
    }

    // =================================
    // NAVIGATION
    // =================================

    private fun navigateToMealDetail(meal: Meal) {
        Log.d(TAG, "Navigating to meal detail: ${meal.strMeal}")
        Log.d(TAG, "Current state before navigation - query: '$currentSearchQuery', showingResults: $isShowingSearchResults")

        val intent = Intent(this, MealDetailActivity::class.java)
        intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.idMeal)
        startActivity(intent)
    }
}