package com.example.mealdb.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MealViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.mealApiService

    // Favorites Repository
    private val database = MealDatabase.getDatabase(application)
    private val favoritesRepository = FavoritesRepository(database.favoriteMealDao())

    private val _meals = MutableLiveData<List<Meal>>()
    val meals: LiveData<List<Meal>> = _meals

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Favorites LiveData
    val favoritesMeals: LiveData<List<FavoriteMeal>> = favoritesRepository.getAllFavorites()

    // New method: Search meals by area/cuisine
    fun searchMealsByArea(area: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = api.getMealsByArea(area)
                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()
                    _meals.value = meals

                    if (meals.isEmpty()) {
                        _error.value = "No meals found for $area cuisine"
                    }
                } else {
                    _error.value = "Error searching $area cuisine: ${response.code()} - ${response.message()}"
                    Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
                Log.e("MealViewModel", "Network error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Replace your existing searchMealsEnhanced method in MealViewModel with this:
    fun searchMealsEnhanced(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // First try searching by meal name
                val nameResponse = api.searchMeals(query)

                if (nameResponse.isSuccessful) {
                    val nameResults = nameResponse.body()?.meals

                    if (!nameResults.isNullOrEmpty()) {
                        _meals.value = nameResults
                        Log.d("MealViewModel", "Found ${nameResults.size} meals by name for '$query'")
                    } else {
                        // If no results by name, try searching by area
                        val areaResponse = api.getMealsByArea(query)

                        if (areaResponse.isSuccessful) {
                            val areaResults = areaResponse.body()?.meals

                            if (!areaResults.isNullOrEmpty()) {
                                _meals.value = areaResults
                                Log.d("MealViewModel", "Found ${areaResults.size} meals by area for '$query'")
                            } else {
                                // If no results by area, try searching by category
                                val categoryResponse = api.getMealsByCategory(query)

                                if (categoryResponse.isSuccessful) {
                                    val categoryResults = categoryResponse.body()?.meals ?: emptyList()
                                    _meals.value = categoryResults

                                    if (categoryResults.isEmpty()) {
                                        _error.value = "No meals found for '$query' in any category"
                                        Log.d("MealViewModel", "No meals found for '$query' in name, area, or category")
                                    } else {
                                        Log.d("MealViewModel", "Found ${categoryResults.size} meals by category for '$query'")
                                    }
                                } else {
                                    _error.value = "Error searching meals: ${categoryResponse.code()} - ${categoryResponse.message()}"
                                    Log.e("MealViewModel", "Category API error: ${categoryResponse.code()} - ${categoryResponse.message()}")
                                }
                            }
                        } else {
                            _error.value = "Error searching meals: ${areaResponse.code()} - ${areaResponse.message()}"
                            Log.e("MealViewModel", "Area API error: ${areaResponse.code()} - ${areaResponse.message()}")
                        }
                    }
                } else {
                    _error.value = "Error searching meals: ${nameResponse.code()} - ${nameResponse.message()}"
                    Log.e("MealViewModel", "Name API error: ${nameResponse.code()} - ${nameResponse.message()}")
                }
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
                Log.e("MealViewModel", "Network error in enhanced search", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // New method: Search by category
    fun searchMealsByCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = api.getMealsByCategory(category)
                if (response.isSuccessful) {
                    val meals = response.body()?.meals ?: emptyList()
                    _meals.value = meals

                    if (meals.isEmpty()) {
                        _error.value = "No meals found for $category category"
                    }
                } else {
                    _error.value = "Error searching $category category: ${response.code()} - ${response.message()}"
                    Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
                Log.e("MealViewModel", "Network error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getRandomMeal() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = api.getRandomMeal()
                if (response.isSuccessful) {
                    _meals.value = response.body()?.meals ?: emptyList()
                } else {
                    _error.value = "Error getting random meal: ${response.code()} - ${response.message()}"
                    Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
                Log.e("MealViewModel", "Network error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getMealById(id: String): Meal? {
        return try {
            val response = api.getMealById(id)
            if (response.isSuccessful) {
                response.body()?.meals?.firstOrNull()
            } else {
                _error.value = "Error loading meal details: ${response.code()} - ${response.message()}"
                Log.e("MealViewModel", "API error: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            _error.value = "Network error: ${e.message}"
            Log.e("MealViewModel", "Network error", e)
            null
        }
    }

    // Favorites Functions
    fun toggleFavorite(meal: Meal) {
        viewModelScope.launch {
            try {
                favoritesRepository.toggleFavorite(meal)
                // You can show a toast or update UI to indicate success
            } catch (e: Exception) {
                _error.value = "Error updating favorites: ${e.message}"
                Log.e("MealViewModel", "Favorites error", e)
            }
        }
    }

    fun isFavorite(mealId: String): LiveData<Boolean> {
        return favoritesRepository.isFavorite(mealId)
    }

}