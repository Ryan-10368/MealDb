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

    // =============================================================================
    // ENUMS AND DATA CLASSES
    // =============================================================================

    enum class DataSource {
        INITIAL,      // Initial random meals
        SEARCH,       // Search results
        RANDOM,       // User requested random meals
        CATEGORY,     // Category search
        AREA         // Area search
    }

    // =============================================================================
    // CONSTANTS
    // =============================================================================

    private companion object {
        private const val SEARCH_DEBOUNCE_TIME = 300L // 300ms debounce
        private const val DEFAULT_RANDOM_MEALS_COUNT = 10
    }

    // =============================================================================
    // API AND DATABASE COMPONENTS
    // =============================================================================

    private val api = ApiClient.mealApiService
    private val database by lazy { MealDatabase.getDatabase(application) }
    private val favoritesRepository by lazy { FavoritesRepository(database.favoriteMealDao()) }

    // =============================================================================
    // LIVEDATA PROPERTIES
    // =============================================================================

    private val _meals = MutableLiveData<List<Meal>>()
    val meals: LiveData<List<Meal>> = _meals

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var _favoritesMeals: LiveData<List<FavoriteMeal>>? = null
    val favoritesMeals: LiveData<List<FavoriteMeal>>
        get() {
            if (_favoritesMeals == null) {
                _favoritesMeals = favoritesRepository.getAllFavorites()
                Log.d("MealViewModel", "✅ Favorites LiveData initialized from repository")
            }
            return _favoritesMeals!!
        }

    // =============================================================================
    // STATE TRACKING PROPERTIES
    // =============================================================================

    private var currentDataSource: DataSource = DataSource.INITIAL
    private var hasUserInteractedWithData = false
    private var isInitialDataLoaded = false

    // Search state tracking
    private var lastSearchQuery: String = ""
    private var lastSearchTime: Long = 0

    // =============================================================================
    // JOB MANAGEMENT
    // =============================================================================

    private var currentSearchJob: Job? = null
    private var currentRandomMealJob: Job? = null

    // =============================================================================
    // INITIALIZATION
    // =============================================================================

    init {
        Log.d("MealViewModel", "🚀 ViewModel initialized")
        loadInitialDataIfNeeded()
    }

    /**
     * Load initial random meals ONLY if no user interaction has occurred
     */
    private fun loadInitialDataIfNeeded() {
        if (hasUserInteractedWithData || isInitialDataLoaded) {
            Log.d("MealViewModel", "⏭️ Skipping initial data - user has interacted or already loaded")
            return
        }

        Log.d("MealViewModel", "📱 Loading initial data...")
        currentDataSource = DataSource.INITIAL

        viewModelScope.launch {
            try {
                loadMultipleRandomMealsInternal(DEFAULT_RANDOM_MEALS_COUNT)
                isInitialDataLoaded = true
                Log.d("MealViewModel", "✅ Initial data loaded successfully")
            } catch (e: Exception) {
                Log.e("MealViewModel", "❌ Error loading initial data", e)
                // Fallback to single random meal
                getRandomMeal()
            }
        }
    }

    // =============================================================================
    // PUBLIC SEARCH METHODS
    // =============================================================================

    /**
     * Enhanced search with state tracking and proper error handling
     */
    fun searchMealsEnhanced(query: String) {
        if (query.isBlank()) {
            Log.d("MealViewModel", "⚠️ Empty search query - ignoring")
            return
        }

        // Mark user interaction
        markUserInteraction(DataSource.SEARCH, query)

        // Prevent duplicate searches
        val currentTime = System.currentTimeMillis()
        if (query == lastSearchQuery && (currentTime - lastSearchTime) < SEARCH_DEBOUNCE_TIME) {
            Log.d("MealViewModel", "⏭️ Skipping duplicate search for: $query")
            return
        }

        lastSearchQuery = query
        lastSearchTime = currentTime

        // Cancel any existing operations
        cancelAllJobs()

        currentSearchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "🔍 Enhanced search for: '$query'")
                val searchResults = performMultiTypeSearch(query)

                if (searchResults.isNullOrEmpty()) {
                    _meals.value = emptyList()
                    _error.value = "No meals found for '$query'"
                    Log.d("MealViewModel", "❌ No meals found for '$query' in any category")
                } else {
                    _meals.value = searchResults
                    Log.d("MealViewModel", "✅ Search completed - found ${searchResults.size} meals for '$query'")
                }

            } catch (e: Exception) {
                handleSearchError(e, "Enhanced search error")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search meals by category
     */
    fun searchMealsByCategory(category: String) {
        markUserInteraction(DataSource.CATEGORY, category)
        performSingleTypeSearch(
            searchType = "category",
            query = category,
            apiCall = { api.getMealsByCategory(category) }
        )
    }

    /**
     * Search meals by area/cuisine
     */
    fun searchMealsByArea(area: String) {
        markUserInteraction(DataSource.AREA, area)
        performSingleTypeSearch(
            searchType = "area",
            query = area,
            apiCall = { api.getMealsByArea(area) }
        )
    }

    // =============================================================================
    // RANDOM MEALS METHODS
    // =============================================================================

    /**
     * Get multiple random meals - called by user action
     */
    fun getMultipleRandomMeals(count: Int = DEFAULT_RANDOM_MEALS_COUNT) {
        Log.d("MealViewModel", "🎲 User requested $count random meals")
        markUserInteraction(DataSource.RANDOM, "")

        cancelAllJobs()

        currentRandomMealJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                loadMultipleRandomMealsInternal(count)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _error.value = "Network error: ${e.message}"
                    Log.e("MealViewModel", "❌ Error loading random meals", e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get single random meal - fallback method
     */
    fun getRandomMeal() {
        if (currentRandomMealJob?.isActive == true) {
            Log.d("MealViewModel", "⏭️ Random meal request already in progress")
            return
        }

        cancelAllJobs()

        currentRandomMealJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "🎲 Getting single random meal")

                val response = withContext(Dispatchers.IO) {
                    api.getRandomMeal()
                }

                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()
                    _meals.value = meals
                    Log.d("MealViewModel", "✅ Got random meal: ${meals.firstOrNull()?.strMeal}")
                } else {
                    handleApiError(response, "Error getting random meal")
                }

            } catch (e: Exception) {
                handleNetworkError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // =============================================================================
    // MEAL DETAILS METHOD
    // =============================================================================

    /**
     * Get meal details by ID
     */
    suspend fun getMealById(id: String): Meal? {
        return try {
            Log.d("MealViewModel", "🔍 Getting meal by ID: $id")

            val response = withContext(Dispatchers.IO) {
                api.getMealById(id)
            }

            if (response.isSuccessful) {
                val meal = response.body()?.meals?.firstOrNull()
                Log.d("MealViewModel", "✅ Got meal by ID: ${meal?.strMeal}")
                meal
            } else {
                _error.value = "Error loading meal details: ${response.code()} - ${response.message()}"
                Log.e("MealViewModel", "❌ API error: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            _error.value = "Network error: ${e.message}"
            Log.e("MealViewModel", "❌ Network error getting meal by ID", e)
            null
        }
    }

    // =============================================================================
    // FAVORITES METHODS
    // =============================================================================

    /**
     * Toggle meal favorite status
     */
    fun toggleFavorite(meal: Meal) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    favoritesRepository.toggleFavorite(meal)
                }
                Log.d("MealViewModel", "✅ Toggled favorite for: ${meal.strMeal}")
            } catch (e: Exception) {
                _error.value = "Error updating favorites: ${e.message}"
                Log.e("MealViewModel", "❌ Favorites error", e)
            }
        }
    }

    /**
     * Check if meal is favorite
     */
    fun isFavorite(mealId: String): LiveData<Boolean> {
        return favoritesRepository.isFavorite(mealId)
    }

    // =============================================================================
    // UTILITY METHODS
    // =============================================================================

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
            SortOption.DEFAULT -> currentMeals
        }

        _meals.value = sortedMeals
        Log.d("MealViewModel", "📊 Sorted ${sortedMeals.size} meals by $sortBy")
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Get debug information about current state
     */
    fun getDebugInfo(): String {
        return "DataSource: $currentDataSource, Query: '$lastSearchQuery', " +
                "MealsCount: ${_meals.value?.size ?: 0}, UserInteracted: $hasUserInteractedWithData"
    }

    /**
     * Refresh current data
     */
    fun refreshData() {
        when (currentDataSource) {
            DataSource.SEARCH -> if (lastSearchQuery.isNotEmpty()) searchMealsEnhanced(lastSearchQuery)
            DataSource.CATEGORY -> if (lastSearchQuery.isNotEmpty()) searchMealsByCategory(lastSearchQuery)
            DataSource.AREA -> if (lastSearchQuery.isNotEmpty()) searchMealsByArea(lastSearchQuery)
            DataSource.RANDOM, DataSource.INITIAL -> getMultipleRandomMeals()
        }
    }

    // =============================================================================
    // PRIVATE HELPER METHODS
    // =============================================================================

    /**
     * Mark user interaction and update state
     */
    private fun markUserInteraction(dataSource: DataSource, query: String) {
        hasUserInteractedWithData = true
        currentDataSource = dataSource
        lastSearchQuery = query
        Log.d("MealViewModel", "📝 User interaction: $dataSource with query '$query'")
    }

    /**
     * Cancel all running jobs
     */
    private fun cancelAllJobs() {
        currentSearchJob?.cancel()
        currentRandomMealJob?.cancel()
    }

    /**
     * Perform multi-type search (name -> area -> category)
     */
    private suspend fun performMultiTypeSearch(query: String): List<Meal>? {
        // Try searching by meal name first
        val nameResponse = withContext(Dispatchers.IO) { api.searchMeals(query) }
        if (nameResponse.isSuccessful) {
            val nameResults = nameResponse.body()?.meals
            if (!nameResults.isNullOrEmpty()) {
                Log.d("MealViewModel", "✅ Found ${nameResults.size} meals by name")
                return enrichMealsWithDetails(nameResults) // Enrich with details
            }
        }

        // Try searching by area
        val areaResponse = withContext(Dispatchers.IO) { api.getMealsByArea(query) }
        if (areaResponse.isSuccessful) {
            val areaResults = areaResponse.body()?.meals
            if (!areaResults.isNullOrEmpty()) {
                Log.d("MealViewModel", "✅ Found ${areaResults.size} meals by area - fetching details...")
                return enrichMealsWithDetails(areaResults) // Enrich with details
            }
        }

        // Try searching by category
        val categoryResponse = withContext(Dispatchers.IO) { api.getMealsByCategory(query) }
        if (categoryResponse.isSuccessful) {
            val categoryResults = categoryResponse.body()?.meals
            if (!categoryResults.isNullOrEmpty()) {
                Log.d("MealViewModel", "✅ Found ${categoryResults.size} meals by category - fetching details...")
                return enrichMealsWithDetails(categoryResults) // Enrich with details
            }
        }

        return null
    }

    /**
     * Perform single type search (category or area)
     */
    private fun performSingleTypeSearch(
        searchType: String,
        query: String,
        apiCall: suspend () -> retrofit2.Response<MealResponse>
    ) {
        cancelAllJobs()

        currentSearchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("MealViewModel", "🔍 Searching by $searchType: $query")

                val response = withContext(Dispatchers.IO) { apiCall() }

                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()

                    if (meals.isEmpty()) {
                        _meals.value = emptyList()
                        _error.value = "No meals found for $query $searchType"
                    } else {
                        Log.d("MealViewModel", "✅ Found ${meals.size} meals for $searchType: $query - fetching details...")
                        val enrichedMeals = enrichMealsWithDetails(meals) // Enrich with details
                        _meals.value = enrichedMeals
                        Log.d("MealViewModel", "✅ Enriched ${enrichedMeals.size} meals with complete details")
                    }
                } else {
                    handleApiError(response, "Error searching $query $searchType")
                }

            } catch (e: Exception) {
                handleSearchError(e, "Network error in $searchType search")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load multiple random meals (internal method)
     */
    private suspend fun loadMultipleRandomMealsInternal(count: Int) {
        Log.d("MealViewModel", "🎲 Loading $count random meals")
        val randomMeals = mutableListOf<Meal>()

        repeat(count) {
            val response = withContext(Dispatchers.IO) {
                api.getRandomMeal()
            }

            if (response.isSuccessful) {
                response.body()?.meals?.firstOrNull()?.let { meal ->
                    randomMeals.add(meal)
                    Log.d("MealViewModel", "Got random meal ${randomMeals.size}/$count: ${meal.strMeal}")
                }
            }
        }

        if (randomMeals.isNotEmpty()) {
            _meals.value = randomMeals
            Log.d("MealViewModel", "✅ Loaded ${randomMeals.size} random meals")
        } else {
            _error.value = "Error loading random meals"
            Log.e("MealViewModel", "❌ No random meals loaded")
        }
    }

    /**
     * Handle search errors
     */
    private fun handleSearchError(e: Exception, message: String) {
        if (e !is kotlinx.coroutines.CancellationException) {
            _error.value = "$message: ${e.message}"
            _meals.value = emptyList()
            Log.e("MealViewModel", "❌ $message", e)
        }
    }

    /**
     * Handle API errors
     */
    private fun handleApiError(response: retrofit2.Response<*>, baseMessage: String) {
        _error.value = "$baseMessage: ${response.code()} - ${response.message()}"
        _meals.value = emptyList()
        Log.e("MealViewModel", "❌ API error: ${response.code()} - ${response.message()}")
    }

    /**
     * Handle network errors
     */
    private fun handleNetworkError(e: Exception) {
        if (e !is kotlinx.coroutines.CancellationException) {
            _error.value = "Network error: ${e.message}"
            _meals.value = emptyList()
            Log.e("MealViewModel", "❌ Network error", e)
        }
    }

    // =============================================================================
    // LIFECYCLE MANAGEMENT
    // =============================================================================

    override fun onCleared() {
        super.onCleared()
        cancelAllJobs()
        Log.d("MealViewModel", "🧹 ViewModel cleared, jobs canceled")
    }

    /**
     * Fetch complete meal details for meals that might have incomplete data
     */
    private suspend fun enrichMealsWithDetails(meals: List<Meal>): List<Meal> {
        return meals.map { meal ->
            // Check if meal has complete data (category and area)
            if (meal.strCategory.isNullOrBlank() || meal.strArea.isNullOrBlank()) {
                // Fetch complete details using meal ID
                meal.idMeal?.let { id ->
                    getMealById(id) ?: meal // Use complete details or fallback to original
                } ?: meal
            } else {
                meal // Already has complete data
            }
        }
    }

}

// =============================================================================
// ENUMS
// =============================================================================

enum class SortOption {
    DEFAULT, NAME_ASC, NAME_DESC, CATEGORY, AREA
}