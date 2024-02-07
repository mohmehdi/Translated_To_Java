package io.plaidapp.core.designernews.login.ui;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.Nullable;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.login.data.LoginRepository;
import java.lang.ref.WeakReference;

public class LoginViewModel extends ViewModel {

  private WeakReference<CoroutinesContextProvider> contextProviderWeakReference;
  private WeakReference<LoginRepository> loginRepositoryWeakReference;
  private MutableLiveData<Result<LoginUiModel>> _uiState;
  private Job currentJob;

  public LoginViewModel(
    LoginRepository loginRepository,
    CoroutinesContextProvider contextProvider
  ) {
    this.loginRepositoryWeakReference = new WeakReference<>(loginRepository);
    this.contextProviderWeakReference = new WeakReference<>(contextProvider);
    _uiState = new MutableLiveData<>();
  }

  public void login(String username, String password) {
    if (currentJob != null && currentJob.isActive()) {
      return;
    }
    currentJob = launchLogin(username, password);
  }

  private Job launchLogin(String username, String password) {
    return contextProviderWeakReference
      .get()
      .io()
      .launch(context -> {
        _uiState.postValue(Result.Loading());
        Result result = loginRepositoryWeakReference
          .get()
          .login(username, password);

        if (result instanceof Result.Success) {
          LoginUiModel uiModel = new LoginUiModel(
            (
              (Result.Success<io.plaidapp.core.designernews.login.data.User>) result
            ).getData()
              .getDisplayName()
              .toLowerCase(),
            (
              (Result.Success<io.plaidapp.core.designernews.login.data.User>) result
            ).getData()
              .getPortraitUrl()
          );
          _uiState.postValue(Result.Success(uiModel));
        } else if (result instanceof Result.Error) {
          _uiState.postValue(result);
        }
      });
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    if (currentJob != null) {
      currentJob.cancel();
    }
  }

  public static class LoginUiModel {

    private final String displayName;

    @Nullable
    private final String portraitUrl;

    public LoginUiModel(String displayName, String portraitUrl) {
      this.displayName = displayName;
      this.portraitUrl = portraitUrl;
    }
  }
}
