
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.fragment.NavHostFragmentArgs;

import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.TaskdetailFragBinding;
import com.example.android.architecture.blueprints.todoapp.tasks.DELETE_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupRefreshLayout;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.snackbar.Snackbar;

public class TaskDetailFragment extends Fragment {

    private TaskdetailFragBinding viewDataBinding;
    private TaskDetailFragmentArgs args;
    private TaskDetailViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.taskdetail_frag, container, false);
        viewDataBinding = TaskdetailFragBinding.bind(view);
        viewDataBinding.setViewmodel(viewModel);
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());

        viewModel.start(args.getTaskId());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupFab();
        view.setupSnackbar(this, viewModel.getSnackbarText(), Snackbar.LENGTH_SHORT);
        setupNavigation();
        setupRefreshLayout(viewDataBinding.getRefreshLayout());
    }

    private void setupNavigation() {
        viewModel.getDeleteTaskEvent().observe(getViewLifecycleOwner(), new EventObserver<Void>() {
            @Override
            public void onEvent(Void aVoid) {
                NavHostFragment.findNavController(TaskDetailFragment.this)
                        .navigate(TaskDetailFragmentDirections
                                .actionTaskDetailFragmentToTasksFragment(DELETE_RESULT_OK));
            }
        });

        viewModel.getEditTaskEvent().observe(getViewLifecycleOwner(), new EventObserver<Void>() {
            @Override
            public void onEvent(Void aVoid) {
                NavHostFragment.findNavController(TaskDetailFragment.this)
                        .navigate(TaskDetailFragmentDirections
                                .actionTaskDetailFragmentToAddEditTaskFragment(
                                        args.getTaskId(),
                                        getResources().getString(R.string.edit_task)
                                ));
            }
        });
    }

    private void setupFab() {
        View fab = getActivity().findViewById(R.id.fab_edit_task);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.editTask();
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.taskdetail_fragment_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_delete) {
            viewModel.deleteTask();
            return true;
        }
        return false;
    }
}
