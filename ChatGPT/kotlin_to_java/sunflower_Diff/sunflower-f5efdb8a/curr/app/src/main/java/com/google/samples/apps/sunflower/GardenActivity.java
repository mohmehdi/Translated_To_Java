
package com.google.samples.apps.sunflower;

import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import com.google.samples.apps.sunflower.databinding.ActivityGardenBinding;

public class GardenActivity extends AppCompatActivity {

    private ActionBarDrawerToggle drawerToggle;
    private ActivityGardenBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_garden);
        setupToolbar();
        setupNavigationDrawer();
        binding.setLifecycleOwner(this);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void setupNavigationDrawer() {
        drawerToggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, R.string.drawer_open, R.string.drawer_close
        );
        binding.drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.setDrawerSlideAnimationEnabled(false);

        NavController navController = Navigation.findNavController(this, R.id.garden_nav_fragment);
        NavigationUI.setupWithNavController(binding.navigationView, navController);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
