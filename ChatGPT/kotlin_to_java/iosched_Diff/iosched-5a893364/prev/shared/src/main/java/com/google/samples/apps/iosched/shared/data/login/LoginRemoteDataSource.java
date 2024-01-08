package com.google.samples.apps.iosched.shared.data.login;

import timber.log.Timber;

public class LoginRemoteDataSource implements LoginDataSource {
    @Override
    public void getUserForToken(String token) {
        Timber.d("getUserForToken: " + token);
    }
}