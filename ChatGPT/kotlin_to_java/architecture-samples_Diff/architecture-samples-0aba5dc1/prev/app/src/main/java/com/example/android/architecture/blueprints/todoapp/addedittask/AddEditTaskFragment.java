
package com.example.android.architecture.blueprints.todoapp.addedittask;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.fragment.NavHostFragmentArgs;
import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.AddtaskFragBinding;
import com.example.android.architecture.blueprints.todoapp.tasks.ADD_EDIT_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.getVmFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupRefreshLayout;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.snackbar.Snackbar;

public class AddEditTaskFragment extends Fragment {

    private AddtaskFragBinding viewDataBinding;

    private final AddEditTaskFragmentArgs args = NavHostFragmentArgs.fromBundle(getArguments());

    private final AddEditTaskViewModel viewModel by viewModels<AddEditTaskViewModel> { getVmFactory() };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.addtask_frag, container, false);
        viewDataBinding = AddtaskFragBinding.bind(root);
        viewDataBinding.setViewmodel(viewModel);
        
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
        return viewDataBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupSnackbar();
        setupNavigation();
        this.setupRefreshLayout(viewDataBinding.getRefreshLayout());
        viewModel.start(args.getTaskId());
    }

    private void setupSnackbar() {
        view.setupSnackbar(this, viewModel.getSnackbarMessage(), Snackbar.LENGTH_SHORT);
    }

    private void setupNavigation() {
        viewModel.getTaskUpdatedEvent().observe(this, new EventObserver() {
            @Override
            public void onEvent(Object event) {
                AddEditTaskFragmentDirections.ActionAddEditTaskFragmentToTasksFragment action =
                        AddEditTaskFragmentDirections.actionAddEditTaskFragmentToTasksFragment(ADD_EDIT_RESULT_OK);
                NavHostFragment.findNavController(AddEditTaskFragment.this).navigate(action);
            }
        });
    }
}
