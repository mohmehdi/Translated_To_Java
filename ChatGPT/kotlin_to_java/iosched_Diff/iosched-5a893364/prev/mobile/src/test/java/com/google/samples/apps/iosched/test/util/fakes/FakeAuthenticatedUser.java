package com.google.samples.apps.iosched.test.util.fakes;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;

public class FakeAuthenticatedUser implements AuthenticatedUser {

    private static final String FAKE_TOKEN = "3141592";
    private AuthenticatedUserInfo user;

    public FakeAuthenticatedUser(AuthenticatedUserInfo user) {
        this.user = user;
    }

    @Override
    public LiveData<Result<String>> getToken() {
        MutableLiveData<Result<String>> liveData = new MutableLiveData<>();
        liveData.setValue(new Result.Success<>(FAKE_TOKEN));
        return liveData;
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfo>> getCurrentUser() {
        MutableLiveData<Result<AuthenticatedUserInfo>> liveData = new MutableLiveData<>();
        if (user != null) {
            liveData.setValue(new Result.Success<>(user));
        }
        return liveData;
    }
}