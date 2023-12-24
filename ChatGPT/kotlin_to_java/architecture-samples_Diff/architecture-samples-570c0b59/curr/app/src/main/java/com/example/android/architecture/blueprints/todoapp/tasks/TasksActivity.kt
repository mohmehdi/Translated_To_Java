
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.util.ActionBarUtil;
import com.google.android.material.navigation.NavigationView;

public class TasksActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tasks_act);

        ActionBarUtil.setupActionBar(this, R.id.toolbar, actionBar -> {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
        });

        setupNavigationDrawer();
    }

    private void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.setStatusBarBackground(R.color.colorPrimaryDark);
        setupDrawerContent(findViewById(R.id.nav_view));
    }

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

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.list_navigation_menu_item:
                    NavOptions navOpts = new NavOptions.Builder().setPopUpTo(R.id.tasksFragment, true).build();
                    Navigation.findNavController(this, R.id.nav_host_fragment).navigate(R.id.tasksFragment, null, navOpts);
                    break;
                case R.id.statistics_navigation_menu_item:
                    NavOptions navOpts = new NavOptions.Builder().setPopUpTo(R.id.statisticsFragment, true).build();
                    Navigation.findNavController(this, R.id.nav_host_fragment).navigate(R.id.statisticsFragment, null, navOpts);
                    break;
            }

            menuItem.setChecked(true);
            drawerLayout.closeDrawers();
            return true;
        });
    }
}
