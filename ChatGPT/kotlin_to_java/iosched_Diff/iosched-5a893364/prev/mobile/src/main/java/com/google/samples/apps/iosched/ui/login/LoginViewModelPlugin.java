package com.google.samples.apps.iosched.ui.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.login.LoginEvent;
import com.google.samples.apps.iosched.ui.schedule.Event;
import javax.inject.Inject;

public enum LoginEvent {
    RequestLogin, RequestLogout
}

public interface LoginViewModelPlugin {
    
    LiveData<Result<AuthenticatedUserInfo>> getCurrentFirebaseUser();
    
    LiveData<Uri> getCurrentUserImageUri();
    
    MutableLiveData<Event<LoginEvent>> getPerformLoginEvent();

    boolean isLoggedIn();

    void emitLoginRequest();
    
    void emitLogoutRequest();
}

internal class DefaultLoginViewModelPlugin implements LoginViewModelPlugin {
    private MutableLiveData<Event<LoginEvent>> performLoginEvent = new MutableLiveData<>();
    private LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private LiveData<Uri> currentUserImageUri;

    @Inject
    public DefaultLoginViewModelPlugin(AuthenticatedUser dataSource) {
        currentFirebaseUser = dataSource.getCurrentUser();
        currentUserImageUri = currentFirebaseUser.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<AuthenticatedUserInfo>) result).getData().getPhotoUrl();
            }
            return null;
        });
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfo>> getCurrentFirebaseUser() {
        return currentFirebaseUser;
    }

    @Override
    public LiveData<Uri> getCurrentUserImageUri() {
        return currentUserImageUri;
    }

    @Override
    public MutableLiveData<Event<LoginEvent>> getPerformLoginEvent() {
        return performLoginEvent;
    }

    @Override
    public boolean isLoggedIn() {
        return ((Result.Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isLoggedIn();
    }

    @Override
    public void emitLoginRequest() {
        performLoginEvent.postValue(new Event<>(LoginEvent.RequestLogin));
    }

    @Override
    public void emitLogoutRequest() {
        performLoginEvent.postValue(new Event<>(LoginEvent.RequestLogout));
    }
}