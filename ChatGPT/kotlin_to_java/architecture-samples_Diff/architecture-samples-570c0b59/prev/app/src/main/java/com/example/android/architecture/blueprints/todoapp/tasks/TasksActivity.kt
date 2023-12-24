
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Observer;
import com.example.android.architecture.blueprints.todoapp.Event;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.addedittask.AddEditTaskActivity;
import com.example.android.architecture.blueprints.todoapp.statistics.StatisticsActivity;
import com.example.android.architecture.blueprints.todoapp.taskdetail.TaskDetailActivity;
import com.example.android.architecture.blueprints.todoapp.util.TaskItemNavigator;
import com.example.android.architecture.blueprints.todoapp.util.TasksNavigator;
import com.example.android.architecture.blueprints.todoapp.util.obtainViewModel;
import com.example.android.architecture.blueprints.todoapp.util.replaceFragmentInActivity;
import com.example.android.architecture.blueprints.todoapp.util.setupActionBar;
import com.google.android.material.navigation.NavigationView;

public class TasksActivity extends AppCompatActivity implements TaskItemNavigator, TasksNavigator {

    private DrawerLayout drawerLayout;
    private TasksViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tasks_act);

        setupActionBar(R.id.toolbar, actionBar -> {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
        });

        setupNavigationDrawer();

        setupViewFragment();

        viewModel = obtainViewModel().apply {
            openTaskEvent.observe(this@TasksActivity, new Observer<Event<String>>() {
                @Override
                public void onChanged(Event<String> event) {
                    event.getContentIfNotHandled()?.let {
                        openTaskDetails(it);
                    }
                }
            });

            newTaskEvent.observe(this@TasksActivity, new Observer<Event<Unit>>() {
                @Override
                public void onChanged(Event<Unit> event) {
                    event.getContentIfNotHandled()?.let {
                        TasksActivity.this.addNewTask();
                    }
                }
            });
        }
        viewModel.loadTasks(true);
    }

    private void setupViewFragment() {
        if (getSupportFragmentManager().findFragmentById(R.id.contentFrame) == null) {
            replaceFragmentInActivity(TasksFragment.newInstance(), R.id.contentFrame);
        }
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
                    break;
                case R.id.statistics_navigation_menu_item:
                    Intent intent = new Intent(TasksActivity.this, StatisticsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    break;
            }
            menuItem.setChecked(true);
            drawerLayout.closeDrawers();
            return true;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        viewModel.handleActivityResult(requestCode, resultCode);
    }

    @Override
    public void openTaskDetails(String taskId) {
        Intent intent = new Intent(this, TaskDetailActivity.class);
        intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId);
        startActivityForResult(intent, AddEditTaskActivity.REQUEST_CODE);
    }

    @Override
    public void addNewTask() {
        Intent intent = new Intent(this, AddEditTaskActivity.class);
        startActivityForResult(intent, AddEditTaskActivity.REQUEST_CODE);
    }

    private TasksViewModel obtainViewModel() {
        return obtainViewModel(TasksViewModel.class);
    }
}
