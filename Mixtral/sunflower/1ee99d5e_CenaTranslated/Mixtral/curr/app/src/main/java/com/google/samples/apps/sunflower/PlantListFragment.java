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
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.compose.plantlist.PlantListScreen;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PlantListFragment extends Fragment {

    private PlantListViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        ComposeView composeView = new ComposeView(requireContext());
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed);
        composeView.setContent {
            MdcTheme {
                PlantListScreen(plant - > navigateToPlant(plant));
            }
        };
        return composeView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_plant_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return when(item.getItemId()) {
            R.id.filter_zone - > {
                updateData();
                true;
            }
            default - > super.onOptionsItemSelected(item);
        }
    }

    private void navigateToPlant(Plant plant) {
        HomeViewPagerFragmentDirections.ActionViewPagerFragmentToPlantDetailFragment action =
            HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment(plant.getPlantId());
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        navController.navigate(action);
    }

    private void updateData() {
        PlantListViewModel.Factory factory = new PlantListViewModel.Factory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, factory).get(PlantListViewModel.class);
        if (viewModel.isFiltered()) {
            viewModel.clearGrowZoneNumber();
        } else {
            viewModel.setGrowZoneNumber(9);
        }
    }
}