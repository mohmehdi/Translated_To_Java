
package com.example.android.architecture.blueprints.todoapp.addedittask;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.AddtaskFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class AddEditTaskFragment extends Fragment {

    private AddtaskFragBinding viewDataBinding;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupFab();
        if (viewDataBinding.getViewModel() != null) {
            View view = getView();
            if (view != null) {
                view.setupSnackbar(this, viewDataBinding.getViewModel().getSnackbarMessage(), Snackbar.LENGTH_LONG);
            }
        }
        setupActionBar();
        loadData();
    }

    private void loadData() {
        if (viewDataBinding.getViewModel() != null) {
            viewDataBinding.getViewModel().start(getArguments().getString(ARGUMENT_EDIT_TASK_ID));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.addtask_frag, container, false);
        viewDataBinding = AddtaskFragBinding.bind(root);
        viewDataBinding.setViewmodel(((AddEditTaskActivity) getActivity()).obtainViewModel());
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
        setHasOptionsMenu(true);
        setRetainInstance(false);
        return viewDataBinding.getRoot();
    }

    private void setupFab() {
        FloatingActionButton fab = getActivity().findViewById(R.id.fab_edit_task_done);
        if (fab != null) {
            fab.setImageResource(R.drawable.ic_done);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (viewDataBinding.getViewModel() != null) {
                        viewDataBinding.getViewModel().saveTask();
                    }
                }
            });
        }
    }

    private void setupActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(
                    getArguments() != null && getArguments().getString(ARGUMENT_EDIT_TASK_ID) != null ?
                            R.string.edit_task : R.string.add_task
            );
        }
    }

    public static final String ARGUMENT_EDIT_TASK_ID = "EDIT_TASK_ID";

    public static AddEditTaskFragment newInstance() {
        return new AddEditTaskFragment();
    }
}
