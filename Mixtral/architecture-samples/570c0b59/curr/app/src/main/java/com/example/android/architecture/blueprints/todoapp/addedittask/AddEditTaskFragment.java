package com.example.android.architecture.blueprints.todoapp.addedittask;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.fragment.FindNavController;
import com.example.android.architecture.blueprints.todoapp.EventObserver;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.AddtaskFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.ADD_EDIT_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.obtainViewModel;
import com.google.android.material.snackbar.Snackbar;

public class AddEditTaskFragment extends Fragment {

  private AddtaskFragBinding viewDataBinding;
  private AddEditTaskViewModel viewModel;

  public View onCreateView(
    LayoutInflater inflater,
    ViewGroup container,
    Bundle savedInstanceState
  ) {
    View root = inflater.inflate(R.layout.addtask_frag, container, false);
    viewModel = obtainViewModel(AddEditTaskViewModel.class);
    viewDataBinding = AddtaskFragBinding.bind(root);
    viewDataBinding.setViewmodel(viewModel);
    viewDataBinding.setLifecycleOwner((LifecycleOwner) this);
    return viewDataBinding.getRoot();
  }

  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setupSnackbar();
    setupActionBar();
    setupNavigation();
    loadData();
  }

  private void setupSnackbar() {
    viewModel
      .getSnackbarMessage()
      .observe(
        this,
        new EventObserver() {
          @Override
          public void onEventUnhandled(String s) {
            ConstraintLayout view = requireView()
              .findViewById(R.id.add_task_container);
            Snackbar.make(view, s, Snackbar.LENGTH_LONG).show();
          }
        }
      );
  }

  private void setupNavigation() {
    viewModel
      .getTaskUpdatedEvent()
      .observe(
        this,
        new EventObserver() {
          @Override
          public void onEventUnhandled(Unit unit) {
            NavController findNavController = FindNavController.findNavController(
              requireView()
            );
            findNavController.navigate(
              AddEditTaskFragmentDirections.actionAddEditTaskFragmentToTasksFragment(
                ADD_EDIT_RESULT_OK
              )
            );
          }
        }
      );
  }

  private void loadData() {
    viewModel.start(getTaskId());
  }

  private String getTaskId() {
    return AddEditTaskFragmentArgs.fromBundle(getArguments()).getTASKID();
  }

  private void setupActionBar() {
    AppCompatActivity activity = (AppCompatActivity) requireActivity();
    activity
      .getSupportActionBar()
      .setTitle(getTaskId() != null ? R.string.edit_task : R.string.add_task);
  }
}
