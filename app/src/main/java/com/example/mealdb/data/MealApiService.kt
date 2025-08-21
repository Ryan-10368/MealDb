package com.example.mealdb.network

import com.example.mealdb.data.MealResponse
import com.example.mealdb.data.AreasResponse
import com.example.mealdb.data.CategoriesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MealApiService {

    @GET("search.php")
    suspend fun searchMeals(@Query("s") mealName: String): Response<MealResponse>

    @GET("random.php")
    suspend fun getRandomMeal(): Response<MealResponse>

    @GET("lookup.php")
    suspend fun getMealById(@Query("i") id: String): Response<MealResponse>

    // NEW: Search meals by area/cuisine
    @GET("filter.php")
    suspend fun getMealsByArea(@Query("a") area: String): Response<MealResponse>

    // NEW: Search meals by category  
    @GET("filter.php")
    suspend fun getMealsByCategory(@Query("c") category: String): Response<MealResponse>

    // OPTIONAL: Get all areas (for future features)
    @GET("list.php?a=list")
    suspend fun getAllAreas(): Response<AreasResponse>

    // OPTIONAL: Get all categories (for future features)
    @GET("list.php?c=list")
    suspend fun getAllCategories(): Response<CategoriesResponse>
}