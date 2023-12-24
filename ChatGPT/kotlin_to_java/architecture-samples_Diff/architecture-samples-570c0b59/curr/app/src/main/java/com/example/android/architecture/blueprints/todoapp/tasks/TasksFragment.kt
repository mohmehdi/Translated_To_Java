
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.databinding.TasksFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.SnackbarUtils;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

public class TasksFragment extends Fragment {

    private TasksFragBinding viewDataBinding;
    private TasksAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewDataBinding = TasksFragBinding.inflate(inflater, container, false);
        viewDataBinding.setViewmodel(obtainViewModel());
        setHasOptionsMenu(true);
        return viewDataBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewDataBinding.setLifecycleOwner(getViewLifecycleOwner());
        setupSnackbar();
        setupListAdapter();
        setupRefreshLayout();
        setupNavigation();
        setupFab();
        viewDataBinding.getViewmodel().loadTasks(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.tasks_fragment_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
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
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupNavigation() {
        viewDataBinding.getViewmodel().getOpenTaskEvent().observe(getViewLifecycleOwner(), new EventObserver<>(this::openTaskDetails));
        viewDataBinding.getViewmodel().getNewTaskEvent().observe(getViewLifecycleOwner(), new EventObserver<>(ignored -> navigateToAddNewTask()));
    }

    private void setupSnackbar() {
        viewDataBinding.getViewmodel().getSnackbarMessage().observe(getViewLifecycleOwner(), (String message) -> {
            SnackbarUtils.showSnackbar(getView(), message, Snackbar.LENGTH_LONG);
        });

        Bundle args = getArguments();
        if (args != null) {
            String message = TasksFragmentArgs.fromBundle(args).getUserMessage();
            viewDataBinding.getViewmodel().showEditResultMessage(message);
        }
    }

    private void showFilteringPopUpMenu() {
        View view = getActivity().findViewById(R.id.menu_filter);
        if (view != null) {
            PopupMenu popupMenu = new PopupMenu(requireContext(), view);
            popupMenu.inflate(R.menu.filter_tasks);
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.active:
                        viewDataBinding.getViewmodel().setFiltering(TasksFilterType.ACTIVE_TASKS);
                        break;
                    case R.id.completed:
                        viewDataBinding.getViewmodel().setFiltering(TasksFilterType.COMPLETED_TASKS);
                        break;
                    default:
                        viewDataBinding.getViewmodel().setFiltering(TasksFilterType.ALL_TASKS);
                        break;
                }
                viewDataBinding.getViewmodel().loadTasks(false);
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
        NavHostFragment.findNavController(this).navigate(TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(null));
    }

    private void openTaskDetails(String taskId) {
        NavHostFragment.findNavController(this).navigate(TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment(taskId));
    }

    private void setupListAdapter() {
        RecyclerView recyclerView = viewDataBinding.tasksList;
        TasksViewModel viewModel = viewDataBinding.getViewmodel();
        if (viewModel != null) {
            listAdapter = new TasksAdapter(new ArrayList<>(0), viewModel);
            recyclerView.setAdapter(listAdapter);
        }
    }

    private void setupRefreshLayout() {
        viewDataBinding.refreshLayout.setColorSchemeColors(
                ContextCompat.getColor(requireActivity(), R.color.colorPrimary),
                ContextCompat.getColor(requireActivity(), R.color.colorAccent),
                ContextCompat.getColor(requireActivity(), R.color.colorPrimaryDark)
        );
        viewDataBinding.refreshLayout.setScrollUpChild(viewDataBinding.tasksList);
    }

    private TasksViewModel obtainViewModel() {
        ViewModelFactory factory = ViewModelFactory.getInstance(requireActivity().getApplication());
        return ViewModelProviders.of(this, factory).get(TasksViewModel.class);
    }
}
