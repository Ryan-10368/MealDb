package com.example.mealdb.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealdb.R
import com.example.mealdb.data.Meal
import com.example.mealdb.databinding.ItemMealBinding

class MealAdapter(
    private val onMealClick: (Meal) -> Unit,
    private val onFavoriteClick: (Meal) -> Unit
) : ListAdapter<Meal, MealAdapter.MealViewHolder>(MealDiffCallback()) {

    // Add this property to track favorites
    private var favoriteMealIds: Set<String> = emptySet()

    // Add this method to update favorites
    fun updateFavorites(favoriteIds: Set<String>) {
        favoriteMealIds = favoriteIds
        notifyDataSetChanged() // This will refresh all visible items
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val binding = ItemMealBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MealViewHolder(
        private val binding: ItemMealBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(meal: Meal) {
            binding.apply {
                // Set meal name
                textViewMealName.text = meal.strMeal

                // Combine category and area
                textViewMealCategory.text = buildString {
                    meal.strCategory?.let { append(it) }
                    if (meal.strCategory != null && meal.strArea != null) append(" • ")
                    meal.strArea?.let { append(it) }
                }

                // Load image using Glide
                meal.strMealThumb?.let { imageUrl ->
                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(imageViewMeal)
                }

                // THIS IS THE KEY FIX - Set favorite button state
                val isFavorite = favoriteMealIds.contains(meal.idMeal)
                buttonFavorite.isSelected = isFavorite

                // Click listeners
                root.setOnClickListener {
                    onMealClick(meal)
                }

                // Favorite button click listener
                buttonFavorite.setOnClickListener {
                    onFavoriteClick(meal)
                }
            }
        }
    }

    class MealDiffCallback : DiffUtil.ItemCallback<Meal>() {
        override fun areItemsTheSame(oldItem: Meal, newItem: Meal): Boolean {
            return oldItem.idMeal == newItem.idMeal
        }

        override fun areContentsTheSame(oldItem: Meal, newItem: Meal): Boolean {
            return oldItem == newItem
        }
    }
}