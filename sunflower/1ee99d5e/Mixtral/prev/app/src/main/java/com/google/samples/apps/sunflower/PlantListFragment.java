package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.google.samples.apps.sunflower.adapters.PlantAdapter;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.databinding.FragmentPlantListBinding;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.List;

@AndroidEntryPoint
public class PlantListFragment extends Fragment {


private PlantListViewModel viewModel;
private PlantAdapter adapter;
private FragmentPlantListBinding binding;

@Override
public View onCreateView(@NonNull LayoutInflater inflater,
                         ViewGroup container, Bundle savedInstanceState) {
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_plant_list, container, false);
    AppCompatActivity activity = (AppCompatActivity) requireActivity();
    activity.setSupportActionBar(binding.toolbar);
    activity.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (getParentFragmentManager().getBackStackEntryCount() == 0) {
                requireActivity().finish();
            } else {
                getParentFragmentManager().popBackStack();
            }
        }
    });

    adapter = new PlantAdapter();
    binding.plantList.setAdapter(adapter);
    subscribeUi(adapter);

    setHasOptionsMenu(true);
    return binding.getRoot();
}

@Override
public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.menu_plant_list, menu);
}

@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
        case R.id.filter_zone:
            updateData();
            return true;
        default:
            return super.onOptionsItemSelected(item);
    }
}

private void subscribeUi(final PlantAdapter adapter) {
    viewModel = new ViewModelProvider(this).get(PlantListViewModel.class);
    viewModel.getPlants().observe(getViewLifecycleOwner(), new Observer<List<Plant>>() {
        @Override
        public void onChanged(@Nullable List<Plant> plants) {
            adapter.submitList(plants);
        }
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