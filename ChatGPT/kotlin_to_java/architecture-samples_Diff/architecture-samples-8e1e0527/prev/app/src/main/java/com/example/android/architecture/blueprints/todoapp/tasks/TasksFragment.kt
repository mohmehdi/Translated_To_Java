
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

    private TasksViewModel viewModel;

    private TasksFragmentArgs args;

    private TasksFragBinding viewDataBinding;

    private TasksAdapter listAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.getInstance()).get(TasksViewModel.class);
        args = TasksFragmentArgs.fromBundle(getArguments());
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewDataBinding = TasksFragBinding.inflate(inflater, container, false);
        viewDataBinding.setViewmodel(viewModel);
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

    private void setupNavigation() {
        viewModel.getOpenTaskEvent().observe(
                getViewLifecycleOwner(),
                new EventObserver<>(this::openTaskDetails)
        );
        viewModel.getNewTaskEvent().observe(
                getViewLifecycleOwner(),
                new EventObserver<>(ignored -> navigateToAddNewTask())
        );
    }

    private void setupSnackbar() {
        View view = getView();
        if (view != null) {
            SnackbarUtilsKt.setupSnackbar(view, getViewLifecycleOwner(), viewModel.getSnackbarText(), Snackbar.LENGTH_SHORT);
        }
        Bundle arguments = getArguments();
        if (arguments != null) {
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
        FloatingActionButton fab = requireView().findViewById(R.id.add_task_fab);
        if (fab != null) {
            fab.setOnClickListener(v -> navigateToAddNewTask());
        }
    }

    private void navigateToAddNewTask() {
        NavDirections action = TasksFragmentDirections
                .actionTasksFragmentToAddEditTaskFragment(
                        null,
                        getResources().getString(R.string.add_task)
                );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void openTaskDetails(String taskId) {
        NavDirections action = TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment(taskId);
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
}
