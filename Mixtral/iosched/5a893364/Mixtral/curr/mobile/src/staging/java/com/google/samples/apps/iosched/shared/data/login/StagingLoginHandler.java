

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
    public void handleLogin(int resultCode, Intent data, OnComplete onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.onComplete(new LoginSuccess());
    }

    @Override
    public void logout(Context context, OnComplete onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.onComplete();
        user.logOut();
    }
}

class StagingAuthenticatedUser {

    private final StagingAuthenticatedUserInfo stagingLoggedInFirebaseUser;
    private final StagingLoggedOutFirebaseUserInfo stagingLoggedOutFirebaseUser;
    private final MutableLiveData<Result<AuthenticatedUserInfo>> currentUserResult;
    private boolean loggedIn;

    public StagingAuthenticatedUser(Context context) {
        stagingLoggedInFirebaseUser = new StagingAuthenticatedUserInfo(context);
        stagingLoggedOutFirebaseUser = new StagingLoggedOutFirebaseUserInfo(context);
        currentUserResult = new MutableLiveData<>();
        currentUserResult.setValue(Result.success(stagingLoggedInFirebaseUser));
        loggedIn = true;
    }

    public void logIn() {
        loggedIn = true;
        currentUserResult.postValue(Result.success(stagingLoggedInFirebaseUser));
    }

    public void logOut() {
        loggedIn = false;
        currentUserResult.postValue(Result.success(stagingLoggedOutFirebaseUser));
    }


    public boolean isLoggedIn() {
        return loggedIn;
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