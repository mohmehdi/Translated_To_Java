
package com.example.android.architecture.blueprints.todoapp.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import androidx.annotation.IdRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProviders;
import com.example.android.architecture.blueprints.todoapp.TodoApplication;
import com.example.android.architecture.blueprints.todoapp.ViewModelFactory;

public class Util {

    public static final int ADD_EDIT_RESULT_OK = Activity.RESULT_FIRST_USER + 1;
    public static final int DELETE_RESULT_OK = Activity.RESULT_FIRST_USER + 2;
    public static final int EDIT_RESULT_OK = Activity.RESULT_FIRST_USER + 3;

    public static void setupActionBar(AppCompatActivity activity, @IdRes int toolbarId, ActionBar.() -> Unit action) {
        activity.setSupportActionBar(activity.findViewById(toolbarId));
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            action.invoke(actionBar);
        }
    }

    public static <T extends ViewModel> T obtainViewModel(Fragment fragment, Class<T> viewModelClass) {
        TodoApplication application = (TodoApplication) fragment.requireContext().getApplicationContext();
        TaskRepository repository = application.getTaskRepository();
        return ViewModelProviders.of(fragment, new ViewModelFactory(repository)).get(viewModelClass);
    }

    private static void rotateToLandscape(AppCompatActivity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private static void rotateToPortrait(AppCompatActivity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public static void rotateOrientation(AppCompatActivity activity) {
        int orientation = activity.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                rotateToPortrait(activity);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                rotateToLandscape(activity);
                break;
            default:
                rotateToLandscape(activity);
                break;
        }
    }
}
