
package com.google.samples.apps.sunflower.adapters;

import android.view.ViewGroup;

import androidx.compose.ui.platform.ComposeView;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.HomeViewPagerFragmentDirections;
import com.google.samples.apps.sunflower.compose.plantlist.PlantListItem;
import com.google.samples.apps.sunflower.data.Plant;

public class PlantAdapter extends ListAdapter<Plant, RecyclerView.ViewHolder> {

    public PlantAdapter() {
        super(new PlantDiffCallback());
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new PlantViewHolder(new ComposeView(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Plant plant = getItem(position);
        ((PlantViewHolder) holder).bind(plant);
    }

    public static class PlantViewHolder extends RecyclerView.ViewHolder {
        private final ComposeView composeView;

        public PlantViewHolder(ComposeView composeView) {
            super(composeView);
            this.composeView = composeView;
        }

        public void bind(Plant plant) {
            composeView.setContent(() -> {
                MdcTheme.INSTANCE.getTheme().getComposeViewContent().invoke();
                new PlantListItem(plant, () -> navigateToPlant(plant));
            });
        }

        private void navigateToPlant(Plant plant) {
            HomeViewPagerFragmentDirections.ActionViewPagerFragmentToPlantDetailFragment direction =
                    HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment(plant.getPlantId());
            Navigation.findNavController(itemView).navigate(direction);
        }
    }

    private static class PlantDiffCallback extends DiffUtil.ItemCallback<Plant> {

        @Override
        public boolean areItemsTheSame(Plant oldItem, Plant newItem) {
            return oldItem.getPlantId() == newItem.getPlantId();
        }

        @Override
        public boolean areContentsTheSame(Plant oldItem, Plant newItem) {
            return oldItem.equals(newItem);
        }
    }
}
