package com.example.android.architecture.blueprints.todoapp.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.fragment.navArgs;

import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.databinding.TasksFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.SnackbarUtilsKt;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupRefreshLayout;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import timber.log.Timber;

public class TasksFragment extends Fragment {

    private final ViewModelFactory viewModelFactory = ViewModelFactory.getInstance();
    private final TasksViewModel viewModel = viewModelFactory.create(TasksViewModel.class);

    private final TasksFragmentArgs args by navArgs();

    private TasksFragBinding viewDataBinding;

    private TasksAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewDataBinding = TasksFragBinding.inflate(inflater, container, false);
        viewDataBinding.setViewmodel(viewModel);
        setHasOptionsMenu(true);
        return viewDataBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewDataBinding.setLifecycleOwner(getViewLifecycleOwner());
        setupSnackbar();
        setupListAdapter();
        setupRefreshLayout(viewDataBinding.getRefreshLayout(), viewDataBinding.getTasksList());
        setupNavigation();
        setupFab();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.tasks_fragment_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear:
                viewModel.clearCompletedTasks();
                return true;
            case R.id.menu_filter:
                showFilteringPopUpMenu();
                return true;
            case R.id.menu_refresh:
                viewModel.loadTasks(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupNavigation() {
        viewModel.getOpenTaskEvent().observe(getViewLifecycleOwner(), new EventObserver<String>() {
            @Override
            public void onEvent(String taskId) {
                openTaskDetails(taskId);
            }
        });
        viewModel.getNewTaskEvent().observe(getViewLifecycleOwner(), new EventObserver<Boolean>() {
            @Override
            public void onEvent(Boolean aBoolean) {
                navigateToAddNewTask();
            }
        });
    }

    private void setupSnackbar() {
        View view = getView();
        if (view != null) {
            SnackbarUtilsKt.setupSnackbar(this, view, viewModel.getSnackbarMessage(), Snackbar.LENGTH_SHORT);
        }
        Bundle arguments = getArguments();
        if (arguments != null) {
            TasksFragmentArgs args = TasksFragmentArgs.fromBundle(arguments);
            viewModel.showEditResultMessage(args.getUserMessage());
        }
    }

    private void showFilteringPopUpMenu() {
        View view = getActivity().findViewById(R.id.menu_filter);
        if (view != null) {
            PopupMenu popupMenu = new PopupMenu(requireContext(), view);
            popupMenu.inflate(R.menu.filter_tasks);
            popupMenu.setOnMenuItemClickListener(item -> {
                viewModel.setFiltering(
                        item.getItemId() == R.id.active ? TasksFilterType.ACTIVE_TASKS :
                                item.getItemId() == R.id.completed ? TasksFilterType.COMPLETED_TASKS :
                                        TasksFilterType.ALL_TASKS
                );
                return true;
            });
            popupMenu.show();
        }
    }

    private void setupFab() {
        FloatingActionButton fab = getActivity().findViewById(R.id.fab_add_task);
        if (fab != null) {
            fab.setOnClickListener(v -> navigateToAddNewTask());
        }
    }

    private void navigateToAddNewTask() {
        TasksFragmentDirections.ActionTasksFragmentToAddEditTaskFragment action =
                TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(
                        null,
                        getResources().getString(R.string.add_task)
                );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void openTaskDetails(String taskId) {
        TasksFragmentDirections.ActionTasksFragmentToTaskDetailFragment action =
                TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment(taskId);
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void setupListAdapter() {
        TasksViewModel viewModel = viewDataBinding.getViewmodel();
        if (viewModel != null) {
            listAdapter = new TasksAdapter(viewModel);
            viewDataBinding.getTasksList().setAdapter(listAdapter);
        } else {
            Timber.w("ViewModel not initialized when attempting to set up adapter.");
        }
    }
}