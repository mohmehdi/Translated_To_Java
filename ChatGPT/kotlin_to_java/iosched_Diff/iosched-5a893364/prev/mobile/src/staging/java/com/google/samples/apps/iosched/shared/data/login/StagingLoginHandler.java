package com.google.samples.apps.iosched.shared.data.login;

import android.content.Context;
import android.content.Intent;
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
    public void handleLogin(int resultCode, Intent data, final LoginHandler.OnCompleteCallback onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.onComplete(LoginSuccess.INSTANCE);
    }

    @Override
    public void logout(Context context, final LoginHandler.OnCompleteCallback onComplete) {
        Timber.d("staging handleLogin called");
        onComplete.onComplete();
        user.logOut();
    }
}