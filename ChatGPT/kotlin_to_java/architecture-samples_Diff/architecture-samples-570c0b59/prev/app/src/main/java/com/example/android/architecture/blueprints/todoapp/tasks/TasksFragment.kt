
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
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.databinding.TasksFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import timber.log.Timber;
import java.util.ArrayList;

public class TasksFragment extends Fragment {

    private TasksFragBinding viewDataBinding;
    private TasksAdapter listAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewDataBinding = TasksFragBinding.inflate(inflater, container, false);
        viewDataBinding.setViewmodel(((TasksActivity) getActivity()).obtainViewModel());
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
        viewDataBinding.setLifecycleOwner(getViewLifecycleOwner());
        setupFab();
        setupListAdapter();
        setupRefreshLayout();
    }

    private void showFilteringPopUpMenu() {
        View view = getActivity().findViewById(R.id.menu_filter);
        if (view == null) {
            return;
        }
        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
        popupMenu.getMenuInflater().inflate(R.menu.filter_tasks, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            viewDataBinding.getViewmodel().setFiltering(
                    item.getItemId() == R.id.active ? TasksFilterType.ACTIVE_TASKS :
                            item.getItemId() == R.id.completed ? TasksFilterType.COMPLETED_TASKS :
                                    TasksFilterType.ALL_TASKS
            );
            viewDataBinding.getViewmodel().loadTasks(false);
            return true;
        });
        popupMenu.show();
    }

    private void setupFab() {
        FloatingActionButton fab = getActivity().findViewById(R.id.fab_add_task);
        if (fab != null) {
            fab.setImageResource(R.drawable.ic_add);
            fab.setOnClickListener(view -> viewDataBinding.getViewmodel().addNewTask());
        }
    }

    private void setupListAdapter() {
        TasksViewModel viewModel = viewDataBinding.getViewmodel();
        if (viewModel != null) {
            listAdapter = new TasksAdapter(new ArrayList<>(), viewModel);
            viewDataBinding.tasksList.setAdapter(listAdapter);
        } else {
            Timber.w("ViewModel not initialized when attempting to set up adapter.");
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

    public static TasksFragment newInstance() {
        return new TasksFragment();
    }

    private static final String TAG = "TasksFragment";
}
