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
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.TaskDetailViewModel;
import com.example.android.architecture.blueprints.todoapp.databinding.TaskdetailFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.DELETE_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.obtainViewModel;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.snackbar.Snackbar;

public class TaskDetailFragment extends Fragment {
private TaskdetailFragBinding viewDataBinding;
private TaskDetailViewModel viewModel;


@Nullable
@Override
public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.taskdetail_frag, container, false);
    viewModel = obtainViewModel(TaskDetailViewModel.class);
    viewDataBinding = DataBindingUtil.bind(view).apply {
        setLifecycleOwner((AppCompatActivity) requireActivity());
        setVariable(com.example.android.architecture.blueprints.todoapp.BR.viewmodel, viewModel);
        setVariable(com.example.android.architecture.blueprints.todoapp.BR.listener, new TaskDetailUserActionsListener() {
            @Override
            public void onCompleteChanged(View v) {
                viewModel.setCompleted(((CheckBox) v).isChecked());
            }
        });
    };
    viewDataBinding.executePendingBindings();
    setHasOptionsMenu(true);
    return view;
}

@Override
public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setupFab();
    viewModel.getSnackbarMessage().observe(getViewLifecycleOwner(), new EventObserver() {
        @Override
        public void onEventUnhandled(Object o) {
            viewDataBinding.getRoot().setupSnackbar(getActivity(), (String) o, Snackbar.LENGTH_LONG);
        }
    });

    setupNavigation();
}

private void setupNavigation() {
    viewModel.getDeleteTaskCommand().observe(getViewLifecycleOwner(), new EventObserver() {
        @Override
        public void onEventUnhandled(Object o) {
            NavController navController = Navigation.findNavController(getView());
            Bundle bundle = new Bundle();
            bundle.putInt("DELETE_RESULT_OK", DELETE_RESULT_OK);
            navController.navigate(R.id.action_taskDetailFragment_to_tasksFragment, bundle);
        }
    });
    viewModel.getEditTaskCommand().observe(getViewLifecycleOwner(), new EventObserver() {
        @Override
        public void onEventUnhandled(Object o) {
            int taskId = TaskDetailFragmentArgs.fromBundle(getArguments()).getTASKID();
            NavController navController = Navigation.findNavController(getView());
            Bundle bundle = new Bundle();
            bundle.putInt("TASKID", taskId);
            navController.navigate(R.id.action_taskDetailFragment_to_addEditTaskFragment, bundle);
        }
    });
}

private void setupFab() {
    View fabEditTask = requireActivity().findViewById(R.id.fab_edit_task);
    if (fabEditTask != null) {
        fabEditTask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.editTask();
            }
        });
    }
}

@Override
public void onResume() {
    super.onResume();
    Integer taskId = getArguments() != null ? TaskDetailFragmentArgs.fromBundle(getArguments()).getTASKID() : null;
    viewModel.start(taskId);
}

@Override
public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    inflater.inflate(R.menu.taskdetail_fragment_menu, menu);
    super.onCreateOptionsMenu(menu, inflater);
}

@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
        case R.id.menu_delete:
            viewModel.deleteTask();
            return true;
        default:
            return super.onOptionsItemSelected(item);
    }
}
}