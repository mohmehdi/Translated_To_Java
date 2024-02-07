package io.plaidapp.designernews.ui;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.designernews.login.data.LoginRepository;
import io.plaidapp.core.designernews.login.ui.LoginViewModel;

public class ViewModelFactory extends ViewModelProvider.Factory {

  private final LoginRepository loginRepository;
  private final CoroutinesContextProvider contextProvider;

  public ViewModelFactory(
    LoginRepository loginRepository,
    CoroutinesContextProvider contextProvider
  ) {
    this.loginRepository = loginRepository;
    this.contextProvider = contextProvider;
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (modelClass.isAssignableFrom(LoginViewModel.class)) {
      return (T) new LoginViewModel(loginRepository, contextProvider);
    }
    throw new IllegalArgumentException("Unknown ViewModel class");
  }
}
