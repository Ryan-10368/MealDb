package com.example.mealdb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_meals")
data class FavoriteMeal(
    @PrimaryKey
    val idMeal: String,
    val strMeal: String?,
    val strMealThumb: String?,
    val strCategory: String?,
    val strArea: String?,
    val dateAdded: Long = System.currentTimeMillis()
)