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
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.databinding.TasksFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.obtainViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;

public class TasksFragment extends Fragment {


private TasksFragBinding viewDataBinding;
private TasksAdapter listAdapter;

public View onCreateView(LayoutInflater inflater, ViewGroup container,
                         Bundle savedInstanceState) {
    viewDataBinding = TasksFragBinding.inflate(inflater, container, false);
    viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
    viewDataBinding.setViewmodel(obtainViewModel(TasksViewModel.class));
    setHasOptionsMenu(true);
    return viewDataBinding.getRoot();
}

@Override
public boolean onOptionsItemSelected(MenuItem item) {
    return when (item.getItemId()) {
        R.id.menu_clear -> {
            viewDataBinding.getViewmodel().clearCompletedTasks();
            true;
        }
        R.id.menu_filter -> {
            showFilteringPopUpMenu();
            true;
        }
        R.id.menu_refresh -> {
            viewDataBinding.getViewmodel().loadTasks(true);
            true;
        }
        default -> false;
    };
}

@Override
public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.tasks_fragment_menu, menu);
}

@Override
public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    setupSnackbar();
    setupListAdapter();
    setupRefreshLayout();
    setupNavigation();
    setupFab();
    viewDataBinding.getViewmodel().loadTasks(true);
}

private void setupNavigation() {
    if (viewDataBinding.getViewmodel() != null) {
        viewDataBinding.getViewmodel().getOpenTaskEvent().observe(this, new EventObserver() {
            @Override
            public void onEventUnhandled(Object o) {
                openTaskDetails((String) o);
            }
        });
        viewDataBinding.getViewmodel().getNewTaskEvent().observe(this, new EventObserver() {
            @Override
            public void onEventUnhandled(Object o) {
                navigateToAddNewTask();
            }
        });
    }
}

private void setupSnackbar() {
    if (viewDataBinding.getViewmodel() != null) {
        View view = getView();
        if (view != null) {
            view.setupSnackbar(this, viewDataBinding.getViewmodel().getSnackbarMessage(), Snackbar.LENGTH_LONG);
        }
        Bundle arguments = getArguments();
        if (arguments != null) {
            String message = TasksFragmentArgs.fromBundle(arguments).getUserMessage();
            viewDataBinding.getViewmodel().showEditResultMessage(message);
        }
    }
}

private void showFilteringPopUpMenu() {
    View view = getActivity().findViewById(R.id.menu_filter);
    if (view != null) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
        popupMenu.getMenuInflater().inflate(R.menu.filter_tasks, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            viewDataBinding.getViewmodel().run {
                setFiltering(
                        item.getItemId() == R.id.active ? TasksFilterType.ACTIVE_TASKS :
                                item.getItemId() == R.id.completed ? TasksFilterType.COMPLETED_TASKS :
                                        TasksFilterType.ALL_TASKS
                );
                loadTasks(false);
            }
            true;
        });
        popupMenu.show();
    }
}

private void setupFab() {
    FloatingActionButton fabAddTask = getActivity().findViewById(R.id.fab_add_task);
    if (fabAddTask != null) {
        fabAddTask.setOnClickListener(v -> navigateToAddNewTask());
    }
}

private void navigateToAddNewTask() {
    TasksFragmentDirections.ActionTasksFragmentToAddEditTaskFragment action =
            TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(null);
    Navigation.findNavController(getView()).navigate(action);
}

private void openTaskDetails(String taskId) {
    TasksFragmentDirections.ActionTasksFragmentToTaskDetailFragment action =
            TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment(taskId);
    Navigation.findNavController(getView()).navigate(action);
}

private void setupListAdapter() {
    TasksViewModel viewModel = viewDataBinding.getViewmodel();
    if (viewModel != null) {
        listAdapter = new TasksAdapter(new ArrayList<>(), viewModel);
        RecyclerView tasksList = viewDataBinding.getTasksList();
        if (tasksList != null) {
            tasksList.setAdapter(listAdapter);
        } else {
            Timber.w("RecyclerView not initialized when attempting to set up adapter.");
        }
    } else {
        Timber.w("ViewModel not initialized when attempting to set up adapter.");
    }
}

private void setupRefreshLayout() {
    SwipeRefreshLayout refreshLayout = viewDataBinding.getRefreshLayout();
    if (refreshLayout != null) {
        refreshLayout.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorAccent,
                R.color.colorPrimaryDark
        );

        refreshLayout.setOnRefreshListener(() -> {
            viewDataBinding.getViewmodel().loadTasks(true);
            refreshLayout.setRefreshing(false);
        });

        refreshLayout.setOnChildScrollUpCallback(new SwipeRefreshLayout.OnChildScrollUpCallback() {
            @Override
            public boolean canChildScrollUp(SwipeRefreshLayout parent, RecyclerView child) {
                return child.canScrollVertically(-1);
            }
        });
    }
}
}