
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.TaskdetailFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.DELETE_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.SnackbarUtilsKt;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.google.android.material.snackbar.Snackbar;

public class TaskDetailFragment extends Fragment {

    private TaskdetailFragBinding viewDataBinding;
    private TaskDetailViewModel viewModel;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupFab();
        viewDataBinding.getViewmodel().getSnackbarMessage().observe(getViewLifecycleOwner(), new EventObserver<String>() {
            @Override
            public void onEvent(String event) {
                SnackbarUtilsKt.setupSnackbar(TaskDetailFragment.this, getView(), event, Snackbar.LENGTH_LONG);
            }
        });

        setupNavigation();
    }

    private void setupNavigation() {
        viewModel.getDeleteTaskCommand().observe(getViewLifecycleOwner(), new EventObserver<Boolean>() {
            @Override
            public void onEvent(Boolean event) {
                NavHostFragment.findNavController(TaskDetailFragment.this)
                        .navigate(TaskDetailFragmentDirections.actionTaskDetailFragmentToTasksFragment(DELETE_RESULT_OK));
            }
        });
        viewModel.getEditTaskCommand().observe(getViewLifecycleOwner(), new EventObserver<Boolean>() {
            @Override
            public void onEvent(Boolean event) {
                String taskId = TaskDetailFragmentArgs.fromBundle(getArguments()).getTASKID();
                NavHostFragment.findNavController(TaskDetailFragment.this)
                        .navigate(TaskDetailFragmentDirections.actionTaskDetailFragmentToAddEditTaskFragment(taskId));
            }
        });
    }

    private void setupFab() {
        View fab = getActivity().findViewById(R.id.fab_edit_task);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewDataBinding.getViewmodel().editTask();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        String taskId = TaskDetailFragmentArgs.fromBundle(getArguments()).getTASKID();
        viewDataBinding.getViewmodel().start(taskId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.taskdetail_frag, container, false);
        viewModel = ViewModelFactory.getInstance().create(TaskDetailViewModel.class);
        viewDataBinding = TaskdetailFragBinding.bind(view);
        viewDataBinding.setViewmodel(viewModel);
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_delete) {
            viewDataBinding.getViewmodel().deleteTask();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.taskdetail_fragment_menu, menu);
    }
}
