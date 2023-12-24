
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import androidx.fragment.app.Fragment;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.TaskdetailFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.setupSnackbar;
import com.google.android.material.snackbar.Snackbar;

public class TaskDetailFragment extends Fragment {

    private TaskdetailFragBinding viewDataBinding;

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
    }

    private void setupFab() {
        View fab = getActivity().findViewById(R.id.fab_edit_task);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (viewDataBinding.getViewModel() != null) {
                        viewDataBinding.getViewModel().editTask();
                    }
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewDataBinding.getViewModel() != null) {
            viewDataBinding.getViewModel().start(getArguments().getString(ARGUMENT_TASK_ID));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.taskdetail_frag, container, false);
        viewDataBinding = TaskdetailFragBinding.bind(view);
        viewDataBinding.setViewmodel(((TaskDetailActivity) getActivity()).obtainViewModel());
        viewDataBinding.setListener(new TaskDetailUserActionsListener() {
            @Override
            public void onCompleteChanged(View v) {
                if (viewDataBinding.getViewModel() != null) {
                    viewDataBinding.getViewModel().setCompleted(((CheckBox) v).isChecked());
                }
            }
        });
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete:
                if (viewDataBinding.getViewModel() != null) {
                    viewDataBinding.getViewModel().deleteTask();
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.taskdetail_fragment_menu, menu);
    }

    public static final String ARGUMENT_TASK_ID = "TASK_ID";
    public static final int REQUEST_EDIT_TASK = 1;

    public static TaskDetailFragment newInstance(String taskId) {
        TaskDetailFragment fragment = new TaskDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARGUMENT_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }
}
