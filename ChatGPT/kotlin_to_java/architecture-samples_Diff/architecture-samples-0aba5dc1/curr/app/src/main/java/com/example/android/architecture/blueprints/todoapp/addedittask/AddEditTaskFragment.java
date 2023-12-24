
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
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupRefreshLayout;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.snackbar.Snackbar;

public class AddEditTaskFragment extends Fragment {

    private AddtaskFragBinding viewDataBinding;

    private final AddEditTaskFragmentArgs args = NavHostFragmentArgs.fromBundle(getArguments());

    private final AddEditTaskViewModel viewModel by viewModels(AddEditTaskViewModel.class, new ViewModelFactory());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.addtask_frag, container, false);
        viewDataBinding = AddtaskFragBinding.bind(root);
        viewDataBinding.setViewmodel(viewModel);
        viewDataBinding.setLifecycleOwner(this);
        return viewDataBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupSnackbar();
        setupNavigation();
        setupRefreshLayout(viewDataBinding.getRefreshLayout());
        viewModel.start(args.getTaskId());
    }

    private void setupSnackbar() {
        View view = getView();
        if (view != null) {
            setupSnackbar(view, viewModel.getSnackbarText(), Snackbar.LENGTH_SHORT);
        }
    }

    private void setupNavigation() {
        viewModel.getTaskUpdatedEvent().observe(getViewLifecycleOwner(), new EventObserver() {
            @Override
            public void onEvent(Object event) {
                NavHostFragment.findNavController(AddEditTaskFragment.this)
                        .navigate(AddEditTaskFragmentDirections
                                .actionAddEditTaskFragmentToTasksFragment(ADD_EDIT_RESULT_OK));
            }
        });
    }
}
