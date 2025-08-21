package com.example.mealdb.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealdb.R
import com.example.mealdb.data.FavoriteMeal
import com.example.mealdb.databinding.ItemFavoriteMealBinding

class FavoriteMealAdapter(
    private val onMealClick: (FavoriteMeal) -> Unit,
    private val onFavoriteClick: (FavoriteMeal) -> Unit
) : ListAdapter<FavoriteMeal, FavoriteMealAdapter.FavoriteViewHolder>(FavoriteMealDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteMealBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(
        private val binding: ItemFavoriteMealBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(favoriteMeal: FavoriteMeal) {
            binding.apply {
                // Set meal name
                textViewMealName.text = favoriteMeal.strMeal

                // Load image using Glide
                favoriteMeal.strMealThumb?.let { imageUrl ->
                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(imageViewMeal)
                }

                // Always show as favorited (filled heart)
                buttonFavorite.isSelected = true

                // Click listeners
                root.setOnClickListener {
                    onMealClick(favoriteMeal)
                }

                buttonFavorite.setOnClickListener {
                    onFavoriteClick(favoriteMeal)
                }
            }
        }
    }

    class FavoriteMealDiffCallback : DiffUtil.ItemCallback<FavoriteMeal>() {
        override fun areItemsTheSame(oldItem: FavoriteMeal, newItem: FavoriteMeal): Boolean {
            return oldItem.idMeal == newItem.idMeal
        }

        override fun areContentsTheSame(oldItem: FavoriteMeal, newItem: FavoriteMeal): Boolean {
            return oldItem == newItem
        }
    }
}