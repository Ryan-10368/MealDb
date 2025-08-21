package com.example.mealdb.data

import com.google.gson.annotations.SerializedName

data class MealResponse(
    @SerializedName("meals")
    val meals: List<Meal>?
)

data class AreasResponse(
    @SerializedName("meals")
    val areas: List<Area>?
)

data class Area(
    @SerializedName("strArea")
    val strArea: String?
)

data class CategoriesResponse(
    @SerializedName("meals")
    val categories: List<Category>?
)

data class Category(
    @SerializedName("strCategory")
    val strCategory: String?
)