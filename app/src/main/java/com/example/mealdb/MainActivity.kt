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
import com.example.mealdb.adapter.FavoriteMealAdapter
import com.example.mealdb.adapter.MealAdapter
import com.example.mealdb.data.Meal
import com.example.mealdb.data.MealViewModel
import com.example.mealdb.data.toMeal
import com.example.mealdb.databinding.ActivityMainBinding
import com.example.mealdb.ui.MealDetailActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MealViewModel
    private lateinit var mealAdapter: MealAdapter
    private lateinit var favoritesAdapter: FavoriteMealAdapter

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
        setupRecyclerViews()
        setupSearch()
        setupSortingSpinner()
        observeViewModel()

        // Load initial random meal
        loadRandomMeal()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MealViewModel::class.java]
    }

    private fun setupRecyclerViews() {
        // Setup main meals adapter
        mealAdapter = MealAdapter(
            onMealClick = { meal ->
                navigateToMealDetail(meal)
            },
            onFavoriteClick = { meal ->
                viewModel.toggleFavorite(meal)
                Toast.makeText(this, "Favorite updated", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewMeals.apply {
            adapter = mealAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
        }

        // Setup favorites adapter
        favoritesAdapter = FavoriteMealAdapter(
            onMealClick = { favoriteMeal ->
                // Use extension function to convert FavoriteMeal to Meal
                val meal = favoriteMeal.toMeal()
                navigateToMealDetail(meal)
            },
            onFavoriteClick = { favoriteMeal ->
                // Use extension function to convert and toggle
                val meal = favoriteMeal.toMeal()
                viewModel.toggleFavorite(meal)
                Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewFavorites.apply {
            adapter = favoritesAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchQuery ->
                    performEnhancedSearch(searchQuery.trim())
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Optional: Add real-time search as user types
                newText?.let { query ->
                    if (query.length >= 2) { // Start searching after 2 characters
                        performEnhancedSearch(query.trim())
                    } else if (query.isEmpty()) {
                        loadRandomMeal() // Reset to random meals when search is cleared
                    }
                }
                return true
            }
        })

        binding.buttonRandomMeal.setOnClickListener {
            loadRandomMeal()
            binding.searchView.setQuery("", false) // Clear search
        }
    }

    private fun performEnhancedSearch(query: String) {
        if (query.isEmpty()) return

        // Check if this looks like a cuisine/area search
        val cuisineAliases = mapOf(
            "chinese" to "chinese",
            "china" to "chinese",
            "italian" to "italian",
            "italy" to "italian",
            "mexican" to "mexican",
            "mexico" to "mexican",
            "indian" to "indian",
            "india" to "indian",
            "american" to "american",
            "usa" to "american",
            "british" to "british",
            "uk" to "british",
            "english" to "british",
            "french" to "french",
            "france" to "french",
            "japanese" to "japanese",
            "japan" to "japanese",
            "thai" to "thai",
            "thailand" to "thai",
            "greek" to "greek",
            "greece" to "greek",
            "spanish" to "spanish",
            "spain" to "spanish",
            "moroccan" to "moroccan",
            "morocco" to "moroccan",
            "turkish" to "turkish",
            "turkey" to "turkish"
        )

        // Add category aliases for common food categories
        val categoryAliases = mapOf(
            "beef" to "beef",
            "chicken" to "chicken",
            "dessert" to "dessert",
            "desserts" to "dessert",
            "lamb" to "lamb",
            "miscellaneous" to "miscellaneous",
            "misc" to "miscellaneous",
            "pasta" to "pasta",
            "pork" to "pork",
            "seafood" to "seafood",
            "fish" to "seafood",
            "side" to "side",
            "sides" to "side",
            "starter" to "starter",
            "starters" to "starter",
            "appetizer" to "starter",
            "appetizers" to "starter",
            "vegan" to "vegan",
            "vegetarian" to "vegetarian",
            "veggie" to "vegetarian",
            "breakfast" to "breakfast"
        )

        val normalizedQuery = query.lowercase()
        val matchedCuisine = cuisineAliases[normalizedQuery]
        val matchedCategory = categoryAliases[normalizedQuery]

        when {
            matchedCuisine != null -> {
                // Search by area/cuisine
                viewModel.searchMealsByArea(matchedCuisine)
                Toast.makeText(this, "Searching for $matchedCuisine cuisine...", Toast.LENGTH_SHORT).show()
            }
            matchedCategory != null -> {
                // Search by category
                viewModel.searchMealsByCategory(matchedCategory)
                Toast.makeText(this, "Searching for $matchedCategory category...", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Use the enhanced search method that tries name, then area, then category
                viewModel.searchMealsEnhanced(query)
            }
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
            val currentSortPosition = binding.spinnerSort.selectedItemPosition
            applySorting(currentSortPosition)

            // Show search results info
            if (binding.searchView.query.isNotEmpty()) {
                val searchTerm = binding.searchView.query.toString()
                Toast.makeText(this, "Found ${meals.size} meals for '$searchTerm'", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        // Handle favorites display
        viewModel.favoritesMeals.observe(this) { favorites ->
            favoriteMealIds = favorites.map { it.idMeal }.toSet()
            mealAdapter.updateFavorites(favoriteMealIds)

            // Update favorites section visibility and content
            if (favorites.isEmpty()) {
                // Show "no favorites" message
                binding.textViewNoFavorites.visibility = View.VISIBLE
                binding.textViewFavoritesHeader.visibility = View.GONE
                binding.recyclerViewFavorites.visibility = View.GONE
                binding.divider.visibility = View.GONE
            } else {
                // Show favorites section
                binding.textViewNoFavorites.visibility = View.GONE
                binding.textViewFavoritesHeader.visibility = View.VISIBLE
                binding.recyclerViewFavorites.visibility = View.VISIBLE
                binding.divider.visibility = View.VISIBLE

                // Update favorites adapter
                favoritesAdapter.submitList(favorites)

                // Update header with count
                binding.textViewFavoritesHeader.text = "❤️ Your Favorites (${favorites.size})"
            }

            val currentSortPosition = binding.spinnerSort.selectedItemPosition
            applySorting(currentSortPosition)
        }
    }

    private fun applySorting(sortPosition: Int) {
        if (originalMealsList.isEmpty()) return

        val sortedList = when (sortPosition) {
            0 -> originalMealsList
            1 -> originalMealsList.sortedBy { it.strMeal?.lowercase() }
            2 -> originalMealsList.sortedByDescending { it.strMeal?.lowercase() }
            3 -> originalMealsList.sortedBy { it.strArea?.lowercase() ?: "zzz" }
            4 -> originalMealsList.sortedByDescending { it.strArea?.lowercase() ?: "zzz" }
            5 -> originalMealsList.sortedBy { it.strCategory?.lowercase() ?: "zzz" }
            6 -> originalMealsList.sortedByDescending { it.strCategory?.lowercase() ?: "zzz" }
            7 -> sortByFavoritesFirst()
            else -> originalMealsList
        }

        mealAdapter.submitList(sortedList)
    }

    private fun sortByFavoritesFirst(): List<Meal> {
        return originalMealsList.sortedWith { meal1, meal2 ->
            val isMeal1Favorite = favoriteMealIds.contains(meal1.idMeal)
            val isMeal2Favorite = favoriteMealIds.contains(meal2.idMeal)

            when {
                isMeal1Favorite && !isMeal2Favorite -> -1
                !isMeal1Favorite && isMeal2Favorite -> 1
                else -> (meal1.strMeal ?: "").compareTo(meal2.strMeal ?: "", ignoreCase = true)
            }
        }
    }

    private fun loadRandomMeal() {
        viewModel.getRandomMeal()
        binding.spinnerSort.setSelection(0)
    }

    private fun navigateToMealDetail(meal: Meal) {
        val intent = Intent(this, MealDetailActivity::class.java)
        intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.idMeal)
        startActivity(intent)
    }
}