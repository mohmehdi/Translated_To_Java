package com.google.samples.apps.sunflower.adapters;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.HomeViewPagerFragmentDirections;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.plantlist.PlantListItem;
import com.google.samples.apps.sunflower.data.Plant;

public class PlantAdapter extends ListAdapter<Plant, RecyclerView.ViewHolder> {
private static final PlantDiffCallback DIFF_CALLBACK = new PlantDiffCallback();


public PlantAdapter() {
    super(DIFF_CALLBACK);
}

@NonNull
@Override
public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ComposeView composeView = new ComposeView(parent.getContext());
    return new PlantViewHolder(composeView);
}

@Override
public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Plant plant = getItem(position);
    ((PlantViewHolder) holder).bind(plant);
}

static class PlantViewHolder extends RecyclerView.ViewHolder {
    private ComposeView composeView;

    public PlantViewHolder(@NonNull ComposeView itemView) {
        super(itemView);
        this.composeView = itemView;
    }

    public void bind(@NonNull Plant plant) {
        composeView.setContent {
            MdcTheme {
                PlantListItem plant = plant;
                {
                    navigateToPlant(plant);
                }
            }
        };
    }

    private void navigateToPlant(@NonNull Plant plant) {
        NavController navController = Navigation.findNavController(composeView);
        HomeViewPagerFragmentDirections.ActionViewPagerFragmentToPlantDetailFragment action =
                HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment(plant.getPlantId());
        navController.navigate(action);
    }
}

private static class PlantDiffCallback extends DiffUtil.ItemCallback<Plant> {

    @Override
    public boolean areItemsTheSame(@NonNull Plant oldItem, @NonNull Plant newItem) {
        return oldItem.getPlantId() == newItem.getPlantId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Plant oldItem, @NonNull Plant newItem) {
        return oldItem.equals(newItem);
    }
}
}