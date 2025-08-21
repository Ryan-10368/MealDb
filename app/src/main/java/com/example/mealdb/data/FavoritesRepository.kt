package com.example.mealdb.data

import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoritesRepository(private val favoriteMealDao: FavoriteMealDao) {

    fun getAllFavorites(): LiveData<List<FavoriteMeal>> {
        return favoriteMealDao.getAllFavorites()
    }

    fun isFavorite(mealId: String): LiveData<Boolean> {
        return favoriteMealDao.isFavoriteLiveData(mealId)
    }

    suspend fun addToFavorites(meal: Meal) {
        withContext(Dispatchers.IO) {
            // Handle nullable values from Meal class
            val mealId = meal.idMeal ?: return@withContext
            val mealName = meal.strMeal ?: "Unknown Meal"
            val mealThumb = meal.strMealThumb

            val favoriteMeal = FavoriteMeal(
                idMeal = mealId,
                strMeal = mealName,
                strMealThumb = mealThumb,
                strCategory = meal.strCategory,
                strArea = meal.strArea
            )
            favoriteMealDao.insertFavorite(favoriteMeal)
        }
    }

    suspend fun removeFromFavorites(mealId: String) {
        withContext(Dispatchers.IO) {
            favoriteMealDao.deleteFavoriteById(mealId)
        }
    }

    suspend fun toggleFavorite(meal: Meal): Boolean {
        return withContext(Dispatchers.IO) {
            val mealId = meal.idMeal ?: return@withContext false
            val isFavorite = favoriteMealDao.isFavorite(mealId)

            if (isFavorite) {
                favoriteMealDao.deleteFavoriteById(mealId)
                false // Now not favorite
            } else {
                addToFavorites(meal)
                true // Now favorite
            }
        }
    }

    suspend fun checkIsFavorite(mealId: String): Boolean {
        return withContext(Dispatchers.IO) {
            favoriteMealDao.isFavorite(mealId)
        }
    }
}