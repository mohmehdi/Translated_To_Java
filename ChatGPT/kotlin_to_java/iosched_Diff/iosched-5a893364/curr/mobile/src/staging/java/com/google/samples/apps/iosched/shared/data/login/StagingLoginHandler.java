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

    private final StagingAuthenticatedUser user;

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
    public void handleLogin(int resultCode, Intent data, OnComplete<LoginResult> onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.invoke(LoginSuccess);
    }

    @Override
    public void logout(Context context, OnComplete<Void> onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.invoke(null);
        user.logOut();
    }
}

class StagingAuthenticatedUser {

    private final StagingAuthenticatedUserInfo stagingLoggedInFirebaseUser;
    private final StagingLoggedOutFirebaseUserInfo stagingLoggedOutFirebaseUser;
    final MutableLiveData<Result<AuthenticatedUserInfo>> currentUserResult;
    private boolean loggedIn;

    public StagingAuthenticatedUser(Context context) {
        this.stagingLoggedInFirebaseUser = new StagingAuthenticatedUserInfo(context);
        this.stagingLoggedOutFirebaseUser = new StagingLoggedOutFirebaseUserInfo(context);
        this.currentUserResult = new MutableLiveData<>();
        this.currentUserResult.setValue(new Result.Success<>(stagingLoggedInFirebaseUser));
    }

    public void logIn() {
        loggedIn = true;
        currentUserResult.postValue(new Result.Success<>(stagingLoggedInFirebaseUser));
    }

    public void logOut() {
        loggedIn = false;
        currentUserResult.postValue(new Result.Success<>(stagingLoggedOutFirebaseUser));
    }
}

class StagingLoggedOutFirebaseUserInfo extends StagingAuthenticatedUserInfo {

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
