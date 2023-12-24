
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.PopupMenu;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.fragment.NavHostFragmentArgs;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.accompanist.appcompattheme.AppCompatTheme;
import com.google.android.material.snackbar.Snackbar;

public class TasksFragment extends Fragment {

    private final TasksViewModel viewModel;
    private final TasksFragmentArgs args;

    public TasksFragment() {
        this.viewModel = new ViewModelProvider(this, ViewModelFactory.getInstance()).get(TasksViewModel.class);
        this.args = NavHostFragmentArgs.fromBundle(getArguments());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        ComposeView composeView = new ComposeView(requireContext());
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed);
        composeView.setContent(() -> {
            AppCompatTheme.INSTANCE.applyTheme();
            TasksScreenKt.TasksScreen(viewModel, this::navigateToAddNewTask, this::openTaskDetails);
        });

        return composeView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupSnackbar();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_clear) {
            viewModel.clearCompletedTasks();
            return true;
        } else if (itemId == R.id.menu_filter) {
            showFilteringPopUpMenu();
            return true;
        } else if (itemId == R.id.menu_refresh) {
            viewModel.loadTasks(true);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.tasks_fragment_menu, menu);
    }

    private void setupSnackbar() {
        View view = getView();
        if (view != null) {
            view.setupSnackbar(getViewLifecycleOwner(), viewModel.getSnackbarText(), Snackbar.LENGTH_SHORT);
        }
        Bundle arguments = getArguments();
        if (arguments != null) {
            viewModel.showEditResultMessage(args.getUserMessage());
        }
    }

    private void showFilteringPopUpMenu() {
        View view = getActivity().findViewById(R.id.menu_filter);
        if (view == null) {
            return;
        }
        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
        popupMenu.getMenuInflater().inflate(R.menu.filter_tasks, popupMenu.getMenu());

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

    private void navigateToAddNewTask() {
        NavDirections action = TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(
                null,
                getResources().getString(R.string.add_task)
        );
        NavHostFragment.findNavController(this).navigate(action);
    }

    private void openTaskDetails(String taskId) {
        NavDirections action = TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment(taskId);
        NavHostFragment.findNavController(this).navigate(action);
    }
}
