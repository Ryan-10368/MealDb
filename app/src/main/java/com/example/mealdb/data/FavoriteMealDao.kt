package com.example.mealdb.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FavoriteMealDao {

    @Query("SELECT * FROM favorite_meals ORDER BY dateAdded DESC")
    fun getAllFavorites(): LiveData<List<FavoriteMeal>>

    @Query("SELECT * FROM favorite_meals WHERE idMeal = :mealId")
    suspend fun getFavoriteById(mealId: String): FavoriteMeal?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_meals WHERE idMeal = :mealId)")
    suspend fun isFavorite(mealId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_meals WHERE idMeal = :mealId)")
    fun isFavoriteLiveData(mealId: String): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteMeal)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteMeal)

    @Query("DELETE FROM favorite_meals WHERE idMeal = :mealId")
    suspend fun deleteFavoriteById(mealId: String)

    @Query("DELETE FROM favorite_meals")
    suspend fun deleteAllFavorites()
}