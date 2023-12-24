
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import androidx.lifecycle.Observer;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.addedittask.AddEditTaskActivity;
import com.example.android.architecture.blueprints.todoapp.addedittask.AddEditTaskFragment;
import com.example.android.architecture.blueprints.todoapp.taskdetail.TaskDetailFragment.Companion.REQUEST_EDIT_TASK;
import com.example.android.architecture.blueprints.todoapp.util.ADD_EDIT_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.DELETE_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.EDIT_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelUtilsKt;
import com.example.android.architecture.blueprints.todoapp.util.replaceFragmentInActivity;
import com.example.android.architecture.blueprints.todoapp.util.setupActionBar;

public class TaskDetailActivity extends AppCompatActivity implements TaskDetailNavigator {

    private TaskDetailViewModel taskViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.taskdetail_act);

        setupActionBar(R.id.toolbar, actionBar -> {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        });

        replaceFragmentInActivity(findOrCreateViewFragment(), R.id.contentFrame);

        taskViewModel = obtainViewModel();

        subscribeToNavigationChanges(taskViewModel);
    }

    private TaskDetailFragment findOrCreateViewFragment() {
        TaskDetailFragment fragment = (TaskDetailFragment) getSupportFragmentManager().findFragmentById(R.id.contentFrame);
        if (fragment == null) {
            fragment = TaskDetailFragment.newInstance(getIntent().getStringExtra(EXTRA_TASK_ID));
        }
        return fragment;
    }

    private void subscribeToNavigationChanges(TaskDetailViewModel viewModel) {
        final TaskDetailActivity activity = this;
        viewModel.getEditTaskCommand().observe(activity, new Observer<Void>() {
            @Override
            public void onChanged(Void aVoid) {
                activity.onStartEditTask();
            }
        });
        viewModel.getDeleteTaskCommand().observe(activity, new Observer<Void>() {
            @Override
            public void onChanged(Void aVoid) {
                activity.onTaskDeleted();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_TASK) {
            if (resultCode == ADD_EDIT_RESULT_OK) {
                setResult(EDIT_RESULT_OK);
                finish();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onTaskDeleted() {
        setResult(DELETE_RESULT_OK);
        finish();
    }

    @Override
    public void onStartEditTask() {
        String taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
        Intent intent = new Intent(this, AddEditTaskActivity.class);
        intent.putExtra(AddEditTaskFragment.ARGUMENT_EDIT_TASK_ID, taskId);
        startActivityForResult(intent, REQUEST_EDIT_TASK);
    }

    private TaskDetailViewModel obtainViewModel() {
        return ViewModelUtilsKt.obtainViewModel(this, TaskDetailViewModel.class);
    }

    public static final String EXTRA_TASK_ID = "TASK_ID";
}
