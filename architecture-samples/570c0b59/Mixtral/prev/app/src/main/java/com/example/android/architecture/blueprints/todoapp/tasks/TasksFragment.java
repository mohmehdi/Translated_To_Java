package com.example.android.architecture.blueprints.todoapp.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.databinding.TasksFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;

public class TasksFragment extends Fragment {


private TasksFragBinding viewDataBinding;
private TasksAdapter listAdapter;

public static TasksFragment newInstance() {
    return new TasksFragment();
}

@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container,
                         Bundle savedInstanceState) {
    viewDataBinding = TasksFragBinding.inflate(inflater, container, false);
    viewDataBinding.setLifecycleOwner(this);
    setHasOptionsMenu(true);
    return viewDataBinding.getRoot();
}

@Override
public void onResume() {
    super.onResume();
    viewDataBinding.getViewmodel().start();
}

@Override
public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
        case R.id.menu_clear:
            viewDataBinding.getViewmodel().clearCompletedTasks();
            return true;
        case R.id.menu_filter:
            showFilteringPopUpMenu();
            return true;
        case R.id.menu_refresh:
            viewDataBinding.getViewmodel().loadTasks(true);
            return true;
        default:
            return false;
    }
}

@Override
public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.tasks_fragment_menu, menu);
}

@Override
public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    viewDataBinding.getViewmodel().getSnackbarMessage().observe(getViewLifecycleOwner(), snackbarMessage -> {
        view?.setupSnackbar(this, snackbarMessage, Snackbar.LENGTH_LONG);
    });
    setupFab();
    setupListAdapter();
    setupRefreshLayout();
}

private void showFilteringPopUpMenu() {
    View view = requireActivity().findViewById(R.id.menu_filter);
    if (view == null) return;
    PopupMenu popupMenu = new PopupMenu(requireContext(), view);
    popupMenu.getMenuInflater().inflate(R.menu.filter_tasks, popupMenu.getMenu());

    popupMenu.setOnMenuItemClickListener(item -> {
        viewDataBinding.getViewmodel().run {
            setFiltering(
                    item.getItemId() == R.id.active
                            ? TasksFilterType.ACTIVE_TASKS
                            : item.getItemId() == R.id.completed
                                    ? TasksFilterType.COMPLETED_TASKS
                                    : TasksFilterType.ALL_TASKS
            );
            loadTasks(false);
        }
        return true;
    });
    popupMenu.show();
}

private void setupFab() {
    FloatingActionButton fabAddTask = requireActivity().findViewById(R.id.fab_add_task);
    if (fabAddTask != null) {
        fabAddTask.setImageResource(R.drawable.ic_add);
        fabAddTask.setOnClickListener(v -> viewDataBinding.getViewmodel().addNewTask());
    }
}

private void setupListAdapter() {
    TasksViewModel viewModel = viewDataBinding.getViewmodel();
    if (viewModel != null) {
        listAdapter = new TasksAdapter(new ArrayList<>(), viewModel);
        RecyclerView tasksList = viewDataBinding.getTasksList();
        if (tasksList != null) {
            tasksList.setAdapter(listAdapter);
        }
    } else {
        Timber.w("ViewModel not initialized when attempting to set up adapter.");
    }
}

private void setupRefreshLayout() {
    viewDataBinding.getRefreshLayout().setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
    );
    viewDataBinding.getRefreshLayout().setOnRefreshListener(() -> {
        viewDataBinding.getViewmodel().loadTasks(true);
        viewDataBinding.getRefreshLayout().setRefreshing(false);
    });
}
}