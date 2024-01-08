package com.google.samples.apps.iosched.test.util.fakes;

import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;

import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.ui.login.LoginEvent;
import com.google.samples.apps.iosched.ui.login.LoginViewModelPlugin;
import com.google.samples.apps.iosched.ui.schedule.Event;

import java.util.Objects;

public class FakeLoginViewModelPlugin implements LoginViewModelPlugin {
    public MutableLiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser = new MutableLiveData<>();
    public MutableLiveData<Uri> currentUserImageUri = new MutableLiveData<>();
    public MutableLiveData<Event<LoginEvent>> performLoginEvent = new MutableLiveData<>();

    public boolean injectIsLoggedIn = true;
    public int loginRequestsEmitted = 0;
    public int logoutRequestsEmitted = 0;

    @Override
    public boolean isLoggedIn() {
        return injectIsLoggedIn;
    }

    @Override
    public void observeLoggedInUser() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void observeRegisteredUser() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isRegistered() {
        return injectIsLoggedIn;
    }

    @Override
    public void emitLoginRequest() {
        loginRequestsEmitted++;
    }

    @Override
    public void emitLogoutRequest() {
        logoutRequestsEmitted++;
    }

    public void loadUser(String id) {
        AuthenticatedUserInfo mockUser = new AuthenticatedUserInfo() {
            @Override
            public String getUid() {
                return id;
            }

            @Override
            public Uri getPhotoUrl() {
                return Objects.requireNonNull(Uri.class.cast(mock(Uri.class)));
            }

            @Override
            public boolean isLoggedIn() {
                return true;
            }
        };
        currentFirebaseUser.postValue(new Result.Success<>(mockUser));
    }
}