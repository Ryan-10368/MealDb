package com.example.mealdb.network

import com.example.mealdb.data.MealResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MealApiService {

    @GET("search.php")
    suspend fun searchMeals(@Query("s") query: String): Response<MealResponse>

    @GET("lookup.php")
    suspend fun getMealById(@Query("i") id: String): Response<MealResponse>

    @GET("random.php")
    suspend fun getRandomMeal(): Response<MealResponse>
}