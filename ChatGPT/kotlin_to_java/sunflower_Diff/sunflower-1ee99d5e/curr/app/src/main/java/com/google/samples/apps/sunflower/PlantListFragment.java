
package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.fragment.findNavController;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.compose.plantlist.PlantListScreen;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PlantListFragment extends Fragment {

    private PlantListViewModel viewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = viewModels().get(PlantListViewModel.class);
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        ViewGroup container,
        Bundle savedInstanceState
    ) {
        setHasOptionsMenu(true);
        return new ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed);
            setContent(() -> {
                MdcTheme.INSTANCE.applyTheme();
                PlantListScreen.INSTANCE.createScreen(this::navigateToPlant);
            });
        };
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_plant_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.filter_zone:
                updateData();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void navigateToPlant(Plant plant) {
        HomeViewPagerFragmentDirections.ActionViewPagerFragmentToPlantDetailFragment direction =
            HomeViewPagerFragmentDirections.actionViewPagerFragmentToPlantDetailFragment(
                plant.getPlantId()
            );
        findNavController().navigate(direction);
    }

    private void updateData() {
        if (viewModel.isFiltered()) {
            viewModel.clearGrowZoneNumber();
        } else {
            viewModel.setGrowZoneNumber(9);
        }
    }
}
