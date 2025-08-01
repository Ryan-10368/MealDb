package com.example.mealdb

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealdb.adapter.MealAdapter
import com.example.mealdb.data.Meal
import com.example.mealdb.data.MealDetailActivity
import com.example.mealdb.data.MealViewModel
import com.example.mealdb.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var mealAdapter: MealAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupSearch()
        observeViewModel()

        // Load initial random meal
        loadRandomMeal()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MealViewModel::class.java]
    }

    private fun setupRecyclerView() {
        mealAdapter = MealAdapter { meal ->
            // Navigate to meal detail
            navigateToMealDetail(meal)
        }

        binding.recyclerViewMeals.apply {
            adapter = mealAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    viewModel.searchMeals(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Optional: implement real-time search
                return true
            }
        })

        binding.buttonRandomMeal.setOnClickListener {
            loadRandomMeal()
        }
    }

    private fun observeViewModel() {
        viewModel.meals.observe(this) { meals ->
            mealAdapter.submitList(meals)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRandomMeal() {
        viewModel.getRandomMeal()
    }

    private fun navigateToMealDetail(meal: Meal) {
        val intent = Intent(this, MealDetailActivity::class.java)
        intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.idMeal)
        startActivity(intent)
    }


}