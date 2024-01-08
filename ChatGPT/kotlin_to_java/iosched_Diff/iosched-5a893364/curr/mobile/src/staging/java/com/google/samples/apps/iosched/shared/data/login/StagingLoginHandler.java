package com.google.samples.apps.iosched.shared.data.login;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.datasources.StagingAuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.util.login.LoginHandler;
import com.google.samples.apps.iosched.util.login.LoginResult;
import com.google.samples.apps.iosched.util.login.LoginSuccess;
import timber.log.Timber;

public class StagingLoginHandler implements LoginHandler {

    private StagingAuthenticatedUser user;

    public StagingLoginHandler(StagingAuthenticatedUser user) {
        this.user = user;
    }

    @Override
    public Intent makeLoginIntent() {
        Timber.d("staging makeLoginIntent called");
        user.logIn();
        return null;
    }

    @Override
    public void handleLogin(int resultCode, Intent data, OnComplete onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.onComplete(LoginSuccess.INSTANCE);
    }

    @Override
    public void logout(Context context, OnComplete onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.onComplete();
        user.logOut();
    }
}

public class StagingAuthenticatedUser {

    private StagingAuthenticatedUserInfo stagingLoggedInFirebaseUser;
    private StagingLoggedOutFirebaseUserInfo stagingLoggedOutFirebaseUser;

    public MutableLiveData<Result<AuthenticatedUserInfo>> currentUserResult;

    public StagingAuthenticatedUser(Context context) {
        stagingLoggedInFirebaseUser = new StagingAuthenticatedUserInfo(context);
        stagingLoggedOutFirebaseUser = new StagingLoggedOutFirebaseUserInfo(context);
        currentUserResult = new MutableLiveData<>();
        currentUserResult.setValue(new Result.Success<>(stagingLoggedInFirebaseUser));
    }

    private boolean loggedIn;

    public void logIn() {
        loggedIn = true;
        currentUserResult.postValue(new Result.Success<>(stagingLoggedInFirebaseUser));
    }

    public void logOut() {
        loggedIn = false;
        currentUserResult.postValue(new Result.Success<>(stagingLoggedOutFirebaseUser));
    }
}

public class StagingLoggedOutFirebaseUserInfo extends StagingAuthenticatedUserInfo {

    public StagingLoggedOutFirebaseUserInfo(Context context) {
        super(context);
    }

    @Override
    public boolean isLoggedIn() {
        return false;
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public Uri getPhotoUrl() {
        return null;
    }
}