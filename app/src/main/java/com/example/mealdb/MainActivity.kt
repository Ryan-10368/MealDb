package com.example.mealdb

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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

    // Store original list for sorting
    private var originalMealsList: List<Meal> = emptyList()
    private var favoriteMealIds: Set<String> = emptySet()

    // Updated sorting options with favorites
    private val sortingOptions = listOf(
        "Default",
        "Name (A-Z)",
        "Name (Z-A)",
        "Area (A-Z)",
        "Area (Z-A)",
        "Category (A-Z)",
        "Category (Z-A)",
        "Favorites First"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupSearch()
        setupSortingSpinner()
        observeViewModel()

        // Load initial random meal
        loadRandomMeal()
    }

    private fun setupViewModel() {
        // Use AndroidViewModelFactory for AndroidViewModel
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MealViewModel::class.java]
    }

    private fun setupRecyclerView() {
        mealAdapter = MealAdapter(
            onMealClick = { meal ->
                // Navigate to meal detail
                navigateToMealDetail(meal)
            },
            onFavoriteClick = { meal ->
                // Handle favorite toggle
                viewModel.toggleFavorite(meal)
                Toast.makeText(this, "Favorite updated", Toast.LENGTH_SHORT).show()
            }
        )

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

    private fun setupSortingSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortingOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = adapter

        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applySorting(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeViewModel() {
        viewModel.meals.observe(this) { meals ->
            originalMealsList = meals
            // Apply current sorting when new data arrives
            val currentSortPosition = binding.spinnerSort.selectedItemPosition
            applySorting(currentSortPosition)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        // Observe favorites to update favorite IDs set and re-apply sorting
        viewModel.favoritesMeals.observe(this) { favorites ->
            // Update the set of favorite meal IDs
            favoriteMealIds = favorites.map { it.idMeal }.toSet()

            // Show favorites count
            val favCount = favorites.size
            if (favCount > 0) {
                println("Total favorites: $favCount")
            }

            // Re-apply current sorting to reflect favorite changes
            val currentSortPosition = binding.spinnerSort.selectedItemPosition
            applySorting(currentSortPosition)
        }
    }

    private fun applySorting(sortPosition: Int) {
        if (originalMealsList.isEmpty()) return

        val sortedList = when (sortPosition) {
            0 -> originalMealsList // Default
            1 -> originalMealsList.sortedBy { it.strMeal?.lowercase() } // Name A-Z
            2 -> originalMealsList.sortedByDescending { it.strMeal?.lowercase() } // Name Z-A
            3 -> originalMealsList.sortedBy { it.strArea?.lowercase() ?: "zzz" } // Area A-Z
            4 -> originalMealsList.sortedByDescending { it.strArea?.lowercase() ?: "zzz" } // Area Z-A
            5 -> originalMealsList.sortedBy { it.strCategory?.lowercase() ?: "zzz" } // Category A-Z
            6 -> originalMealsList.sortedByDescending { it.strCategory?.lowercase() ?: "zzz" } // Category Z-A
            7 -> sortByFavoritesFirst() // Favorites First
            else -> originalMealsList
        }

        mealAdapter.submitList(sortedList) {
            // Scroll to top after the list is updated
            binding.recyclerViewMeals.scrollToPosition(0)
        }
    }

    private fun sortByFavoritesFirst(): List<Meal> {
        return originalMealsList.sortedWith { meal1, meal2 ->
            val isMeal1Favorite = favoriteMealIds.contains(meal1.idMeal)
            val isMeal2Favorite = favoriteMealIds.contains(meal2.idMeal)

            when {
                isMeal1Favorite && !isMeal2Favorite -> -1 // meal1 is favorite, goes first
                !isMeal1Favorite && isMeal2Favorite -> 1  // meal2 is favorite, goes first
                else -> {
                    // Both are favorites or both are not favorites - sort by name
                    (meal1.strMeal ?: "").compareTo(meal2.strMeal ?: "", ignoreCase = true)
                }
            }
        }
    }

    private fun loadRandomMeal() {
        viewModel.getRandomMeal()
        // Reset spinner to default when loading random meal
        binding.spinnerSort.setSelection(0)
    }

    private fun navigateToMealDetail(meal: Meal) {
        val intent = Intent(this, MealDetailActivity::class.java)
        intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.idMeal)
        startActivity(intent)
    }
}