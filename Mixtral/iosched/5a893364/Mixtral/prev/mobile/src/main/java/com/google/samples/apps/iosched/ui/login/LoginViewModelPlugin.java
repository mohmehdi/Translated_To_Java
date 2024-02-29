

package com.google.samples.apps.iosched.ui.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.LiveDataUtil;
import com.google.samples.apps.iosched.ui.login.LoginEvent;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogin;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogout;
import com.google.samples.apps.iosched.ui.schedule.Event;

import javax.inject.Inject;

public enum class LoginEvent {
    RequestLogin,
    RequestLogout
}

public interface LoginViewModelPlugin {

    LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser();


    boolean isLoggedIn();

    default void emitLoginRequest() {
        performLoginEvent().postValue(new Event<>(new RequestLogin()));
    }

    default void emitLogoutRequest() {
        performLoginEvent().postValue(new Event<>(new RequestLogout()));
    }
}

import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;

import javax.inject.Inject;

public class DefaultLoginViewModelPlugin implements LoginViewModelPlugin {

    private final MutableLiveData<Event<LoginEvent>> performLoginEvent = new MutableLiveData<>();
    private final LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private final LiveData<Uri> currentUserImageUri;

    @Inject
    public DefaultLoginViewModelPlugin(AuthenticatedUser dataSource) {
        this.currentFirebaseUser = dataSource.getCurrentUser();
        this.currentUserImageUri = LiveDataUtil.mapLiveData(currentFirebaseUser, result -> {
            if (result instanceof Result.Success) {
                AuthenticatedUserInfo data = ((Result.Success<AuthenticatedUserInfo>) result).getData();
                return data != null ? data.getPhotoUrl() : null;
            }
            return null;
        });
    }



    @Override
    public LiveData<Uri> currentUserImageUri() {
        return currentUserImageUri;
    }

    @Override
    public LiveData<Event<LoginEvent>> performLoginEvent() {
        return performLoginEvent;
    }

    @Override
    public boolean isLoggedIn() {
        Result<AuthenticatedUserInfo> value = currentFirebaseUser.getValue();
        if (value instanceof Result.Success) {
            AuthenticatedUserInfo data = ((Result.Success<AuthenticatedUserInfo>) value).getData();
            return data != null && data.isLoggedIn();
        }
        return false;
    }

    @Override
    public void emitLoginRequest() {
        performLoginEvent.postValue(new Event<>(new RequestLogin()));
    }

    @Override
    public void emitLogoutRequest() {
        performLoginEvent.postValue(new Event<>(new RequestLogout()));
    }
}