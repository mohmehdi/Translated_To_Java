package io.plaidapp.core.designernews.login.ui;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.login.data.LoginRepository;
import io.plaidapp.core.util.exhaustive;
import kotlinx.coroutines.experimental.Job;
import kotlinx.coroutines.experimental.launch;

public class LoginViewModel extends ViewModel {

    private LoginRepository loginRepository;
    private CoroutinesContextProvider contextProvider;
    private Job currentJob;

    private MutableLiveData<Result<LoginUiModel>> _uiState = new MutableLiveData<>();
    public LiveData<Result<LoginUiModel>> uiState = _uiState;

    public LoginViewModel(LoginRepository loginRepository, CoroutinesContextProvider contextProvider) {
        this.loginRepository = loginRepository;
        this.contextProvider = contextProvider;
    }

    public void login(String username, String password) {
        if (currentJob != null && currentJob.isActive()) {
            return;
        }
        currentJob = launchLogin(username, password);
    }

    private Job launchLogin(String username, String password) {
        launch(contextProvider.io, () -> {
            _uiState.postValue(Result.Loading.INSTANCE);
            Result<User> result = loginRepository.login(username, password);

            if (result instanceof Result.Success) {
                User user = ((Result.Success<User>) result).getData();
                LoginUiModel uiModel = new LoginUiModel(
                        user.getDisplayName().toLowerCase(),
                        user.getPortraitUrl()
                );
                _uiState.postValue(new Result.Success<>(uiModel));
            } else if (result instanceof Result.Error) {
                _uiState.postValue((Result.Error) result);
            } else if (result instanceof Result.Loading) {
                
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
}

public class LoginUiModel {
    private String displayName;
    private String portraitUrl;

    public LoginUiModel(String displayName, String portraitUrl) {
        this.displayName = displayName;
        this.portraitUrl = portraitUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPortraitUrl() {
        return portraitUrl;
    }
}