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

    fun searchMeals(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val response = api.searchMeals(query)
                if (response.isSuccessful) {
                    _meals.value = response.body()?.meals ?: emptyList()
                } else {
                    _error.value = "Error searching meals: ${response.code()} - ${response.message()}"
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
                val isNowFavorite = favoritesRepository.toggleFavorite(meal)
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

    suspend fun checkIsFavorite(mealId: String): Boolean {
        return favoritesRepository.checkIsFavorite(mealId)
    }
}