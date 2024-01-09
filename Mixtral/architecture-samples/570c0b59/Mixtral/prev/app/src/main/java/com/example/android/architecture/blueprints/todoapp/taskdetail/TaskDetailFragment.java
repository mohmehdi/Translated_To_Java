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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.TaskdetailFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.snackbar.Snackbar;

public class TaskDetailFragment extends Fragment {

  private TaskdetailFragBinding viewDataBinding;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState
  ) {
    View view = inflater.inflate(R.layout.taskdetail_frag, container, false);
    viewDataBinding = TaskdetailFragBinding.bind(view);
    viewDataBinding.viewmodel =
      ((TaskDetailActivity) requireActivity()).obtainViewModel();
    viewDataBinding.listener =
      new TaskDetailUserActionsListener() {
        @Override
        public void onCompleteChanged(View v) {
          viewDataBinding.viewmodel.setCompleted(((CheckBox) v).isChecked());
        }
      };
    viewDataBinding.setLifecycleOwner(
      (LifecycleOwner) this.getViewLifecycleOwner()
    );
    setHasOptionsMenu(true);
    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setupFab();
    viewDataBinding
      .getViewmodel()
      .let(view ->
        setupSnackbar(
          view.getContext(),
          view.getSnackbarMessage(),
          Snackbar.LENGTH_LONG
        )
      );
  }

  private void setupFab() {
    AppCompatActivity activity = (AppCompatActivity) requireActivity();
    if (activity != null) {
      View fab = activity.findViewById(R.id.fab);
      if (fab != null) {
        fab.setOnClickListener(view ->
          viewDataBinding.getViewmodel().onCompleteClicked()
        );
      }
    }
  }
  @Override
public void onResume() {
super.onResume();
viewDataBinding.getViewmodel().start(requireArguments().getString(ARGUMENT_TASK_ID));
}

@Override
public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
inflater.inflate(R.menu.taskdetail_fragment_menu, menu);
}

@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) {
return item.getItemId() == R.id.menu_delete ? (viewDataBinding.getViewmodel().deleteTask(), true) : super.onOptionsItemSelected(item);
}

public static final String ARGUMENT_TASK_ID = "TASK_ID";
public static final int REQUEST_EDIT_TASK = 1;

public static TaskDetailFragment newInstance(String taskId) {
TaskDetailFragment fragment = new TaskDetailFragment();
Bundle arguments = new Bundle();
arguments.putString(ARGUMENT_TASK_ID, taskId);
fragment.setArguments(arguments);
return fragment;
}
}
