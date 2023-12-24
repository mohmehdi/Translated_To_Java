
package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import com.google.samples.apps.sunflower.adapters.PlantAdapter;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.databinding.FragmentPlantListBinding;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PlantListFragment extends Fragment {

    private PlantListViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentPlantListBinding binding = FragmentPlantListBinding.inflate(inflater, container, false);
        if (getContext() == null) {
            return binding.getRoot();
        }

        viewModel = new PlantListViewModel();
        PlantAdapter adapter = new PlantAdapter();
        binding.getPlantList().setAdapter(adapter);
        subscribeUi(adapter);

        setHasOptionsMenu(true);
        return binding.getRoot();
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

    private void subscribeUi(PlantAdapter adapter) {
        viewModel.getPlants().observe(getViewLifecycleOwner(), plants -> {
            adapter.submitList(plants);
        });
    }

    private void updateData() {
        if (viewModel.isFiltered()) {
            viewModel.clearGrowZoneNumber();
        } else {
            viewModel.setGrowZoneNumber(9);
        }
    }
}
