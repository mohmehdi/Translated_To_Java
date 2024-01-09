package com.example.android.architecture.blueprints.todoapp.addedittask;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.AddtaskFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class AddEditTaskFragment extends Fragment {


private AddtaskFragBinding viewDataBinding;

public static final String ARGUMENT_EDIT_TASK_ID = "EDIT_TASK_ID";

public AddEditTaskFragment() {
    // Required empty public constructor
}

public static AddEditTaskFragment newInstance() {
    return new AddEditTaskFragment();
}

@Override
public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setupFab();
    if (viewDataBinding.viewmodel != null) {
        View view = getView();
        setupSnackbar.setup(this, viewDataBinding.viewmodel.snackbarMessage, Snackbar.LENGTH_LONG, view);
    }
    setupActionBar();
    loadData();
}

private void loadData() {
    if (getArguments() != null && getArguments().containsKey(ARGUMENT_EDIT_TASK_ID)) {
        viewDataBinding.viewmodel.start(getArguments().getString(ARGUMENT_EDIT_TASK_ID));
    }
}

@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container,
                         Bundle savedInstanceState) {
    View root = inflater.inflate(R.layout.addtask_frag, container, false);
    viewDataBinding = AddtaskFragBinding.inflate(inflater, container, false).apply {
        viewmodel = ((AddEditTaskActivity) requireActivity()).obtainViewModel();
    };
    viewDataBinding.setLifecycleOwner((LifecycleOwner) this.getViewLifecycleOwner());
    setHasOptionsMenu(true);
    setRetainInstance(false);
    return viewDataBinding.getRoot();
}

private void setupFab() {
    FloatingActionButton fabEditTaskDone = requireActivity().findViewById(R.id.fab_edit_task_done);
    if (fabEditTaskDone != null) {
        fabEditTaskDone.setImageResource(R.drawable.ic_done);
        fabEditTaskDone.setOnClickListener(view -> viewDataBinding.viewmodel.saveTask());
    }
}

private void setupActionBar() {
    AppCompatActivity activity = (AppCompatActivity) requireActivity();
    activity.getSupportActionBar().setTitle(getArguments() != null && getArguments().get(ARGUMENT_EDIT_TASK_ID) != null
            ? R.string.edit_task
            : R.string.add_task);
}
}