
package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.lifecycle.Observer;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.util.ADD_EDIT_RESULT_OK;
import com.example.android.architecture.blueprints.todoapp.util.obtainViewModel;
import com.example.android.architecture.blueprints.todoapp.util.replaceFragmentInActivity;
import com.example.android.architecture.blueprints.todoapp.util.setupActionBar;

public class AddEditTaskActivity extends AppCompatActivity implements AddEditTaskNavigator {

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onTaskSaved() {
        setResult(ADD_EDIT_RESULT_OK);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addtask_act);

        setupActionBar(R.id.toolbar, actionBar -> {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        });

        replaceFragmentInActivity(obtainViewFragment(), R.id.contentFrame);

        subscribeToNavigationChanges();
    }

    private void subscribeToNavigationChanges() {
        obtainViewModel().taskUpdatedEvent.observe(this, new Observer<Object>() {
            @Override
            public void onChanged(Object o) {
                AddEditTaskActivity.this.onTaskSaved();
            }
        });
    }

    private AddEditTaskFragment obtainViewFragment() {
        AddEditTaskFragment fragment = (AddEditTaskFragment) getSupportFragmentManager().findFragmentById(R.id.contentFrame);
        if (fragment == null) {
            fragment = AddEditTaskFragment.newInstance();
            Bundle args = new Bundle();
            args.putString(AddEditTaskFragment.ARGUMENT_EDIT_TASK_ID, getIntent().getStringExtra(AddEditTaskFragment.ARGUMENT_EDIT_TASK_ID));
            fragment.setArguments(args);
        }
        return fragment;
    }

    public AddEditTaskViewModel obtainViewModel() {
        return obtainViewModel(AddEditTaskViewModel.class);
    }

    public static final int REQUEST_CODE = 1;
}
