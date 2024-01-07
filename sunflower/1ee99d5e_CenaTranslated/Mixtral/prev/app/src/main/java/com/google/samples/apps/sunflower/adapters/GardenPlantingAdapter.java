package com.google.samples.apps.sunflower.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.sunflower.HomeViewPagerFragmentDirections;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import com.google.samples.apps.sunflower.databinding.ListItemGardenPlantingBinding;
import com.google.samples.apps.sunflower.viewmodels.PlantAndGardenPlantingsViewModel;

public class GardenPlantingAdapter extends ListAdapter < PlantAndGardenPlantings, GardenPlantingAdapter.ViewHolder > {

    private static class GardenPlantDiffCallback extends DiffUtil.ItemCallback < PlantAndGardenPlantings > {
        @Override
        public boolean areItemsTheSame(@NonNull PlantAndGardenPlantings oldItem, @NonNull PlantAndGardenPlantings newItem) {
            return oldItem.getPlant().getPlantId() == newItem.getPlant().getPlantId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull PlantAndGardenPlantings oldItem, @NonNull PlantAndGardenPlantings newItem) {
            return oldItem.getPlant() == newItem.getPlant();
        }
    }

    public GardenPlantingAdapter() {
        super(new GardenPlantDiffCallback());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListItemGardenPlantingBinding binding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.getContext()),
            R.layout.list_item_garden_planting,
            parent,
            false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private ListItemGardenPlantingBinding binding;

        public ViewHolder(@NonNull ListItemGardenPlantingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.setClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String plantId = binding.getViewModel().getPlantId();
                    if (plantId != null) {
                        navigateToPlant(plantId, view);
                    }
                }
            });
        }

        private void navigateToPlant(String plantId, View view) {
            HomeViewPagerFragmentDirections.ActionViewPagerFragmentToPlantDetailFragment action = HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment(plantId);
            NavController navController = Navigation.findNavController(view);
            navController.navigate(action);
        }

        public void bind(PlantAndGardenPlantings plantings) {
            binding.setViewModel(new PlantAndGardenPlantingsViewModel(plantings));
            binding.executePendingBindings();
        }
    }
}