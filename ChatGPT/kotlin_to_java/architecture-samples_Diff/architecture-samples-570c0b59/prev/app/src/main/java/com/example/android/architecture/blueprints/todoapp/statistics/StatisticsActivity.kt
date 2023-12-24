
package com.example.android.architecture.blueprints.todoapp.statistics;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.tasks.TasksActivity;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelUtil;
import com.example.android.architecture.blueprints.todoapp.util.replaceFragmentInActivity;
import com.example.android.architecture.blueprints.todoapp.util.setupActionBar;
import com.google.android.material.navigation.NavigationView;

public class StatisticsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.statistics_act);

        setupActionBar(R.id.toolbar, actionBar -> {
            actionBar.setTitle(R.string.statistics_title);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
        });

        setupNavigationDrawer();

        findOrCreateViewFragment();
    }

    private void findOrCreateViewFragment() {
        StatisticsFragment statisticsFragment = (StatisticsFragment) getSupportFragmentManager().findFragmentById(R.id.contentFrame);
        if (statisticsFragment == null) {
            statisticsFragment = StatisticsFragment.newInstance();
            replaceFragmentInActivity(statisticsFragment, R.id.contentFrame);
        }
    }

    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.setStatusBarBackground(R.color.colorPrimaryDark);
        setupDrawerContent(findViewById(R.id.nav_view));
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.list_navigation_menu_item:
                    Intent intent = new Intent(StatisticsActivity.this, TasksActivity.class);
                    startActivity(intent);
                    break;
                case R.id.statistics_navigation_menu_item:
                    // Handle statistics navigation menu item
                    break;
            }

            menuItem.setChecked(true);
            drawerLayout.closeDrawers();
            return true;
        });
    }

    public StatisticsViewModel obtainViewModel() {
        return ViewModelUtil.obtainViewModel(this, StatisticsViewModel.class);
    }
}
