package com.example.mealdb.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MealViewModel : ViewModel() {

    private val api = ApiClient.mealApiService

    private val _meals = MutableLiveData<List<Meal>>()
    val meals: LiveData<List<Meal>> = _meals

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

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
}
