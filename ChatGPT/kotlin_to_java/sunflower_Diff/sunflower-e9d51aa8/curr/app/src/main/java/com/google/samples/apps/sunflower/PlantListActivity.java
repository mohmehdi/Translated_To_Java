package com.google.samples.apps.sunflower;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.samples.apps.sunflower.adapters.PlantAdapter;
import com.google.samples.apps.sunflower.databinding.ActivityPlantListBinding;
import com.google.samples.apps.sunflower.utilities.InjectorUtils;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;

public class PlantListActivity extends AppCompatActivity {

    private boolean isTwoPane = false;

    private PlantAdapter adapter;
    private PlantListViewModel viewModel;

    private boolean arePlantsFiltered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityPlantListBinding binding = DataBindingUtil.setContentView(this,
                R.layout.activity_plant_list);
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setTitle(getTitle());

        if (binding.plantListFrame.getPlantDetailContainer() != null) {
            isTwoPane = true;
        }
        adapter = new PlantAdapter(this, isTwoPane);
        binding.plantListFrame.getPlantList().setAdapter(adapter);

        PlantListViewModelFactory factory = InjectorUtils.providePlantListViewModelFactory(this);
        viewModel = ViewModelProviders.of(this, factory).get(PlantListViewModel.class);
        subscribeUi();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_plant_list, menu);
        return true;
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

    private void subscribeUi() {
        viewModel.getPlants().observe(this, new Observer<List<Plant>>() {
            @Override
            public void onChanged(@Nullable List<Plant> plants) {
                if (plants != null) {
                    adapter.setValues(plants);
                }
            }
        });
    }

    private void updateData() {
        arePlantsFiltered = arePlantsFiltered ? false : true;
        if (arePlantsFiltered) {
            viewModel.clearGrowZoneNumber();
        } else {
            viewModel.setGrowZoneNumber(9);
        }
    }
}