
package com.example.android.architecture.blueprints.todoapp.util;

import androidx.fragment.app.Fragment;
import com.example.android.architecture.blueprints.todoapp.TodoApplication;
import com.example.android.architecture.blueprints.todoapp.ViewModelFactory;

public class FragmentUtil {
    public static ViewModelFactory getViewModelFactory(Fragment fragment) {
        TodoApplication application = (TodoApplication) fragment.requireContext().getApplicationContext();
        TaskRepository repository = application.getTaskRepository();
        return new ViewModelFactory(repository);
    }
}
