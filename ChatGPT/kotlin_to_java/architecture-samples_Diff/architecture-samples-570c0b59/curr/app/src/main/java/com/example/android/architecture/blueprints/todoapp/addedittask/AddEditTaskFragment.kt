
package com.example.android.architecture.blueprints.todoapp.addedittask;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.AddtaskFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.ADD_EDIT_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.SnackbarUtilsKt;
import com.google.android.material.snackbar.Snackbar;

public class AddEditTaskFragment extends Fragment {

    private AddtaskFragBinding viewDataBinding;
    private AddEditTaskViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.addtask_frag, container, false);
        viewModel = obtainViewModel(AddEditTaskViewModel.class);
        viewDataBinding = AddtaskFragBinding.bind(root);
        viewDataBinding.setViewmodel(viewModel);
        viewDataBinding.setLifecycleOwner(this);
        return viewDataBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupSnackbar();
        setupActionBar();
        setupNavigation();
        loadData();
    }

    private void setupSnackbar() {
        if (viewDataBinding.getViewmodel() != null) {
            SnackbarUtilsKt.setupSnackbar(getView(), this, viewDataBinding.getViewmodel().getSnackbarMessage(), Snackbar.LENGTH_LONG);
        }
    }

    private void setupNavigation() {
        viewModel.getTaskUpdatedEvent().observe(getViewLifecycleOwner(), new EventObserver<Object>() {
            @Override
            public void onEvent(Object event) {
                AddEditTaskFragmentDirections.ActionAddEditTaskFragmentToTasksFragment action = AddEditTaskFragmentDirections.actionAddEditTaskFragmentToTasksFragment(ADD_EDIT_RESULT_OK);
                NavHostFragment.findNavController(AddEditTaskFragment.this).navigate(action);
            }
        });
    }

    private void loadData() {
        if (viewDataBinding.getViewmodel() != null) {
            viewDataBinding.getViewmodel().start(getTaskId());
        }
    }

    private void setupActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(
                    getTaskId() != null ? R.string.edit_task : R.string.add_task
            );
        }
    }

    private String getTaskId() {
        if (getArguments() != null) {
            return AddEditTaskFragmentArgs.fromBundle(getArguments()).getTASKID();
        }
        return null;
    }
}
