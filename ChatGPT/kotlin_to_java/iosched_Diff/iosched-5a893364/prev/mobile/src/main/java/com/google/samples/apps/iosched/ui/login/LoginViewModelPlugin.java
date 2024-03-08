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

    LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;

    LiveData<Uri> currentUserImageUri;

    MutableLiveData<Event<LoginEvent>> performLoginEvent;

    boolean isLoggedIn();

    default void emitLoginRequest() {
        getPerformLoginEvent().postValue(new Event<>(LoginEvent.RequestLogin));
    }

    default void emitLogoutRequest() {
        getPerformLoginEvent().postValue(new Event<>(LoginEvent.RequestLogout));
    }
}

class DefaultLoginViewModelPlugin implements LoginViewModelPlugin {

    private MutableLiveData<Event<LoginEvent>> performLoginEvent = new MutableLiveData<>();
    private LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private LiveData<Uri> currentUserImageUri;

    @Inject
    public DefaultLoginViewModelPlugin(AuthenticatedUser dataSource) {
        this.currentFirebaseUser = dataSource.getCurrentUser();
        this.currentUserImageUri = currentFirebaseUser.map(result -> {
            return ((Result.Success<AuthenticatedUserInfo>) result).getData().getPhotoUrl();
        });
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