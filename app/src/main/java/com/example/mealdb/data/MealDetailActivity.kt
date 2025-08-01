package com.example.mealdb.data

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mealdb.R
import com.example.mealdb.adapter.IngredientAdapter
import com.example.mealdb.databinding.ActivityMealDetailBinding
import kotlinx.coroutines.launch

class MealDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEAL_ID = "extra_meal_id"
    }

    private lateinit var binding: ActivityMealDetailBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var ingredientAdapter: IngredientAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMealDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MealViewModel::class.java]
        setupRecyclerView()

        val mealId = intent.getStringExtra(EXTRA_MEAL_ID)
        mealId?.let { loadMealDetails(it) }
    }

    private fun setupRecyclerView() {
        ingredientAdapter = IngredientAdapter()
        binding.recyclerViewIngredients.apply {
            adapter = ingredientAdapter
            layoutManager = LinearLayoutManager(this@MealDetailActivity)
        }
    }

    private fun loadMealDetails(mealId: String) {
        lifecycleScope.launch {
            val meal = viewModel.getMealById(mealId)
            meal?.let { displayMeal(it) }
        }
    }

    private fun displayMeal(meal: Meal) {
        binding.apply {
            textViewMealDetailName.text = meal.strMeal
            textViewMealDetailCategory.text = "Category: ${meal.strCategory ?: "Unknown"}"
            textViewMealDetailArea.text = "Area: ${meal.strArea ?: "Unknown"}"
            textViewMealDetailInstructions.text = meal.strInstructions ?: "No instructions available"

            // Load image
            meal.strMealThumb?.let { imageUrl ->
                Glide.with(this@MealDetailActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(imageViewMealDetail)
            }


        }
    }
}