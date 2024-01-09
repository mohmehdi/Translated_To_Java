package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.compose.plantlist.PlantListScreen;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;

@AndroidEntryPoint
public class PlantListFragment extends Fragment {


private PlantListViewModel viewModel;
private NavController navController;

public PlantListFragment() {
}

@Override
public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                         Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    ComposeView composeView = new ComposeView(requireContext());
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed);
    composeView.setContent {
        MdcTheme {
            PlantListScreen(plant -> navigateToPlant(plant));
        }
    };
    return composeView;
}

@Override
public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    Objects.requireNonNull(getActivity()).getMenuInflater().inflate(R.menu.menu_plant_list, menu);
}

@Override
public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.filter_zone) {
        updateData();
        return true;
    }
    return super.onOptionsItemSelected(item);
}

private void navigateToPlant(Plant plant) {
    navController = NavHostFragment.findNavController(this);
    HomeViewPagerFragmentDirections.ActionViewPagerFragmentToPlantDetailFragment action =
            HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment(plant.getPlantId());
    navController.navigate(action);
}

private void updateData() {
    if (viewModel.isFiltered()) {
        viewModel.clearGrowZoneNumber();
    } else {
        viewModel.setGrowZoneNumber(9);
    }
}
}