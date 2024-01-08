package com.example.android.architecture.blueprints.todoapp.util;

import androidx.fragment.app.Fragment;
import com.example.android.architecture.blueprints.todoapp.TodoApplication;
import com.example.android.architecture.blueprints.todoapp.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.di.TaskRepositoryModule;

public class FragmentExt {

  public static ViewModelFactory getVmFactory(Fragment fragment) {
    TodoApplication application = (TodoApplication) fragment
      .requireContext()
      .getApplicationContext();
    TaskRepositoryModule repositoryModule = application.getTaskRepositoryModule();
    return new ViewModelFactory(repositoryModule.getTaskRepository());
  }
}
