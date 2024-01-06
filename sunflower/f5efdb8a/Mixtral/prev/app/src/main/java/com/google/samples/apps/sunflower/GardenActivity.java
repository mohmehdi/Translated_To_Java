package com.google.samples.apps.sunflower;

import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.samples.apps.sunflower.databinding.ActivityGardenBinding;

public class GardenActivity extends AppCompatActivity {


private ActionBarDrawerToggle drawerToggle;

@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ActivityGardenBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_garden);
    setupToolbar(binding);
    setupNavigationDrawer(binding);
}

private void setupToolbar(ActivityGardenBinding binding) {
    setSupportActionBar(binding.toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeButtonEnabled(true);
}

private void setupNavigationDrawer(ActivityGardenBinding binding) {
    drawerToggle = new ActionBarDrawerToggle(
            this, binding.drawerLayout, R.string.drawer_open, R.string.drawer_close
    );
    binding.drawerLayout.addDrawerListener(drawerToggle);
    drawerToggle.setDrawerSlideAnimationEnabled(false);
    binding.drawerLayout.addDrawerListener(drawerToggle);
    drawerToggle.syncState();

    NavigationView navigationView = binding.navigationView;
    navigationView.setNavigationItemSelectedListener(
            menuItem -> {
                selectDrawerItem(menuItem);
                return true;
            }
    );

    AppBarConfiguration appBarConfiguration =
            new AppBarConfiguration.Builder(
                    R.id.navigation_plants, R.id.navigation_garden, R.id.navigation_notifications)
                    .setDrawerLayout(binding.drawerLayout)
                    .build();
    NavigationUI.setupActionBarWithNavController(this, Navigation.findNavController(this, R.id.garden_nav_fragment), appBarConfiguration);
    NavigationUI.setupWithNavController(navigationView, Navigation.findNavController(this, R.id.garden_nav_fragment));
}

@Override
public boolean onSupportNavigateUp() {
    DrawerLayout drawer = findViewById(R.id.drawer_layout);
    if (drawer.isDrawerOpen(GravityCompat.START)) {
        drawer.closeDrawer(GravityCompat.START);
    } else {
        super.onSupportNavigateUp();
    }
    return true;
}

@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (drawerToggle.onOptionsItemSelected(item)) {
        return true;
    }
    return super.onOptionsItemSelected(item);
}

@Override
protected void onPostCreate(@Nullable Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    drawerToggle.syncState();
}

@Override
public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    drawerToggle.onConfigurationChanged(newConfig);
}

private void selectDrawerItem(MenuItem menuItem) {
    // Handle navigation view item clicks here.
}
}