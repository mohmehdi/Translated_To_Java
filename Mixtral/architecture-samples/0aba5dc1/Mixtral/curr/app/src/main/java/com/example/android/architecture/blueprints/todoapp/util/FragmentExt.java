package com.example.android.architecture.blueprints.todoapp.util;

import androidx.fragment.app.Fragment;
import com.example.android.architecture.blueprints.todoapp.TodoApplication;
import com.example.android.architecture.blueprints.todoapp.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.data.TaskRepository;

public class FragmentExt {

  public static ViewModelFactory getViewModelFactory(Fragment fragment) {
    TaskRepository repository =
      (
        (TodoApplication) fragment.requireContext().getApplicationContext()
      ).getTaskRepository();
    return new ViewModelFactory(repository);
  }
}
