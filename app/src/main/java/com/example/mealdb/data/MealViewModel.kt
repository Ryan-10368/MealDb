package com.example.mealdb.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MealViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.mealApiService

    // Lazy initialization of database components
    private val database by lazy { MealDatabase.getDatabase(application) }
    private val favoritesRepository by lazy { FavoritesRepository(database.favoriteMealDao()) }

    private val _meals = MutableLiveData<List<Meal>>()
    val meals: LiveData<List<Meal>> = _meals

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // FIXED: Initialize favorites properly
    private var _favoritesMeals: LiveData<List<FavoriteMeal>>? = null
    val favoritesMeals: LiveData<List<FavoriteMeal>>
        get() {
            if (_favoritesMeals == null) {
                // Get the real LiveData from repository - this will auto-update when database changes
                _favoritesMeals = favoritesRepository.getAllFavorites()
                Log.d("MealViewModel", "✅ Favorites LiveData initialized from repository")
            }
            return _favoritesMeals!!
        }

    // Add job management to prevent multiple simultaneous requests
    private var currentSearchJob: Job? = null
    private var currentRandomMealJob: Job? = null

    // Add request deduplication
    private var lastSearchQuery: String = ""
    private var lastSearchTime: Long = 0
    private val SEARCH_DEBOUNCE_TIME = 300L // 300ms debounce

    // Add initialization flag
    private var isInitialDataLoaded = false

    init {
        // Load initial data when ViewModel is created
        loadInitialData()
    }

    /**
     * Load initial random meals when the app starts
     */
    private fun loadInitialData() {
        if (isInitialDataLoaded) return

        viewModelScope.launch {
            try {
                Log.d("MealViewModel", "Loading initial data...")

                // Load multiple random meals for a better initial experience
                loadMultipleRandomMeals(5)

                isInitialDataLoaded = true
            } catch (e: Exception) {
                Log.e("MealViewModel", "Error loading initial data", e)
                // Fallback to single random meal
                getRandomMeal()
            }
        }
    }

    /**
     * Load multiple random meals for initial display
     */
    private suspend fun loadMultipleRandomMeals(count: Int) {
        _isLoading.value = true
        val randomMeals = mutableListOf<Meal>()

        try {
            repeat(count) {
                val response = withContext(Dispatchers.IO) {
                    api.getRandomMeal()
                }

                if (response.isSuccessful) {
                    response.body()?.meals?.firstOrNull()?.let { meal ->
                        randomMeals.add(meal)
                    }
                }
            }

            if (randomMeals.isNotEmpty()) {
                _meals.value = randomMeals
                Log.d("MealViewModel", "Loaded ${randomMeals.size} initial random meals")
            } else {
                // Fallback to single random meal
                getRandomMeal()
            }
        } catch (e: Exception) {
            Log.e("MealViewModel", "Error loading multiple random meals", e)
            // Fallback to single random meal
            getRandomMeal()
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Refresh data - useful for pull-to-refresh
     */
    fun refreshData() {
        Log.d("MealViewModel", "Refreshing data...")
        isInitialDataLoaded = false
        loadInitialData()
    }

    // Enhanced search with proper threading and better error handling
    fun searchMealsEnhanced(query: String) {
        if (query.isBlank()) {
            // If search is empty, reload initial data
            refreshData()
            return
        }

        // Prevent duplicate searches
        val currentTime = System.currentTimeMillis()
        if (query == lastSearchQuery && (currentTime - lastSearchTime) < SEARCH_DEBOUNCE_TIME) {
            Log.d("MealViewModel", "Skipping duplicate search for: $query")
            return
        }

        lastSearchQuery = query
        lastSearchTime = currentTime

        // Cancel any existing search
        currentSearchJob?.cancel()

        currentSearchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "Enhanced search for: $query")

                var searchResults: List<Meal>? = null

                // First try searching by meal name
                val nameResponse = withContext(Dispatchers.IO) {
                    api.searchMeals(query)
                }

                if (nameResponse.isSuccessful) {
                    val nameResults = nameResponse.body()?.meals
                    if (!nameResults.isNullOrEmpty()) {
                        searchResults = nameResults
                        Log.d(
                            "MealViewModel",
                            "Found ${nameResults.size} meals by name for '$query'"
                        )
                    }
                }

                // If no results by name, try searching by area
                if (searchResults.isNullOrEmpty()) {
                    val areaResponse = withContext(Dispatchers.IO) {
                        api.getMealsByArea(query)
                    }

                    if (areaResponse.isSuccessful) {
                        val areaResults = areaResponse.body()?.meals
                        if (!areaResults.isNullOrEmpty()) {
                            searchResults = areaResults
                            Log.d(
                                "MealViewModel",
                                "Found ${areaResults.size} meals by area for '$query'"
                            )
                        }
                    }
                }

                // If no results by area, try searching by category
                if (searchResults.isNullOrEmpty()) {
                    val categoryResponse = withContext(Dispatchers.IO) {
                        api.getMealsByCategory(query)
                    }

                    if (categoryResponse.isSuccessful) {
                        val categoryResults = categoryResponse.body()?.meals
                        if (!categoryResults.isNullOrEmpty()) {
                            searchResults = categoryResults
                            Log.d(
                                "MealViewModel",
                                "Found ${categoryResults.size} meals by category for '$query'"
                            )
                        }
                    }
                }

                // Update UI with results
                if (searchResults.isNullOrEmpty()) {
                    _meals.value = emptyList()
                    _error.value = "No meals found for '$query'"
                    Log.d("MealViewModel", "No meals found for '$query' in any category")
                } else {
                    _meals.value = searchResults
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Search error: ${e.message}"
                    _meals.value = emptyList()
                    Log.e("MealViewModel", "Network error in enhanced search", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Search by category with proper threading
    fun searchMealsByCategory(category: String) {
        // Cancel any existing search
        currentSearchJob?.cancel()

        currentSearchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "Searching by category: $category")

                val response = withContext(Dispatchers.IO) {
                    api.getMealsByCategory(category)
                }

                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()
                    _meals.value = meals

                    if (meals.isEmpty()) {
                        _error.value = "No meals found for $category category"
                    }
                    Log.d("MealViewModel", "Found ${meals.size} meals for category: $category")
                } else {
                    _error.value =
                        "Error searching $category category: ${response.code()} - ${response.message()}"
                    _meals.value = emptyList()
                    Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Network error: ${e.message}"
                    _meals.value = emptyList()
                    Log.e("MealViewModel", "Network error", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Search by area/cuisine
    fun searchMealsByArea(area: String) {
        // Cancel any existing search
        currentSearchJob?.cancel()

        currentSearchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "Searching by area: $area")

                val response = withContext(Dispatchers.IO) {
                    api.getMealsByArea(area)
                }

                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()
                    _meals.value = meals

                    if (meals.isEmpty()) {
                        _error.value = "No meals found for $area cuisine"
                    }
                    Log.d("MealViewModel", "Found ${meals.size} meals for area: $area")
                } else {
                    _error.value =
                        "Error searching $area cuisine: ${response.code()} - ${response.message()}"
                    _meals.value = emptyList()
                    Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Network error: ${e.message}"
                    _meals.value = emptyList()
                    Log.e("MealViewModel", "Network error", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getRandomMeal() {
        // Prevent multiple random meal requests
        if (currentRandomMealJob?.isActive == true) {
            Log.d("MealViewModel", "Random meal request already in progress, skipping")
            return
        }

        // Cancel any existing search when getting random meal
        currentSearchJob?.cancel()

        currentRandomMealJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "Getting random meal")

                val response = withContext(Dispatchers.IO) {
                    api.getRandomMeal()
                }

                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()
                    _meals.value = meals
                    Log.d("MealViewModel", "Got random meal: ${meals.firstOrNull()?.strMeal}")
                } else {
                    _error.value =
                        "Error getting random meal: ${response.code()} - ${response.message()}"
                    Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Network error: ${e.message}"
                    Log.e("MealViewModel", "Network error", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getMealById(id: String): Meal? {
        return try {
            Log.d("MealViewModel", "Getting meal by ID: $id")

            val response = withContext(Dispatchers.IO) {
                api.getMealById(id)
            }

            if (response.isSuccessful) {
                val meal = response.body()?.meals?.firstOrNull()
                Log.d("MealViewModel", "Got meal by ID: ${meal?.strMeal}")
                meal
            } else {
                _error.value =
                    "Error loading meal details: ${response.code()} - ${response.message()}"
                Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            _error.value = "Network error: ${e.message}"
            Log.e("MealViewModel", "Network error", e)
            null
        }
    }

    // FIXED: Simplified favorites functions
    fun toggleFavorite(meal: Meal) {
        viewModelScope.launch {
            try {
                // Use IO dispatcher for database operations
                withContext(Dispatchers.IO) {
                    favoritesRepository.toggleFavorite(meal)
                }
                Log.d("MealViewModel", "✅ Toggled favorite for: ${meal.strMeal}")
                // The LiveData from repository will automatically notify observers
            } catch (e: Exception) {
                _error.value = "Error updating favorites: ${e.message}"
                Log.e("MealViewModel", "Favorites error", e)
            }
        }
    }

    fun isFavorite(mealId: String): LiveData<Boolean> {
        return favoritesRepository.isFavorite(mealId)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Sort meals by different criteria
     */
    fun sortMeals(sortBy: SortOption) {
        val currentMeals = _meals.value ?: return

        val sortedMeals = when (sortBy) {
            SortOption.NAME_ASC -> currentMeals.sortedBy { it.strMeal }
            SortOption.NAME_DESC -> currentMeals.sortedByDescending { it.strMeal }
            SortOption.CATEGORY -> currentMeals.sortedBy { it.strCategory }
            SortOption.AREA -> currentMeals.sortedBy { it.strArea }
            SortOption.DEFAULT -> currentMeals // Keep original order
        }

        _meals.value = sortedMeals
        Log.d("MealViewModel", "Sorted ${sortedMeals.size} meals by $sortBy")
    }

    // Clean up jobs when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        currentSearchJob?.cancel()
        currentRandomMealJob?.cancel()
        Log.d("MealViewModel", "ViewModel cleared, jobs canceled")
    }

    fun getMultipleRandomMeals(count: Int = 5) {
        // Cancel any existing jobs
        currentSearchJob?.cancel()
        currentRandomMealJob?.cancel()

        currentRandomMealJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "Getting $count random meals")
                val randomMeals = mutableListOf<Meal>()

                repeat(count) {
                    val response = withContext(Dispatchers.IO) {
                        api.getRandomMeal()
                    }

                    if (response.isSuccessful) {
                        response.body()?.meals?.firstOrNull()?.let { meal ->
                            randomMeals.add(meal)
                            Log.d(
                                "MealViewModel",
                                "Got random meal ${randomMeals.size}/$count: ${meal.strMeal}"
                            )
                        }
                    }
                }

                if (randomMeals.isNotEmpty()) {
                    _meals.value = randomMeals
                    Log.d("MealViewModel", "✅ Loaded ${randomMeals.size} random meals")
                } else {
                    _error.value = "Error loading random meals"
                    Log.e("MealViewModel", "No random meals loaded")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Network error: ${e.message}"
                    Log.e("MealViewModel", "Error loading multiple random meals", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
enum class SortOption {
    DEFAULT, NAME_ASC, NAME_DESC, CATEGORY, AREA
}