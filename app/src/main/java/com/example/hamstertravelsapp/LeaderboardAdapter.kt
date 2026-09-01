package com.example.hamstertravelsapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.hamstertravelsapp.databinding.ItemLeaderboardBinding

class LeaderboardAdapter :
    ListAdapter<HamsterViewModel.Landmark, LeaderboardAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeaderboardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        holder.binding.name.text = item.name
        holder.binding.flag.text = item.flag
        
        val distanceKm = item.distance / 1000.0
        holder.binding.distance.text = context.getString(R.string.leaderboard_distance_home, distanceKm)

        val currentHamster = currentList.find { it.isCurrent }
        if (currentHamster != null) {
            val gap = (item.distance - currentHamster.distance) / 1000.0
            if (item.isCurrent) {
                holder.binding.gap.text = context.getString(R.string.leaderboard_current_position)
                holder.binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.leaderboard_highlight))
            } else {
                holder.binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background_default))
                if (gap > 0) {
                    holder.binding.gap.text = context.getString(R.string.leaderboard_ahead, gap)
                } else {
                    holder.binding.gap.text = context.getString(R.string.leaderboard_behind, -gap)
                }
            }
        } else {
            holder.binding.gap.text = ""
            holder.binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background_default))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HamsterViewModel.Landmark>() {
        override fun areItemsTheSame(oldItem: HamsterViewModel.Landmark, newItem: HamsterViewModel.Landmark): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: HamsterViewModel.Landmark, newItem: HamsterViewModel.Landmark): Boolean {
            return oldItem == newItem
        }
    }
}
