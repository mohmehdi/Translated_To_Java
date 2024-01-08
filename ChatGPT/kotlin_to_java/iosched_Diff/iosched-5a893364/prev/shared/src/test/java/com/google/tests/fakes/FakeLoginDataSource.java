package com.google.samples.apps.iosched.test.util.fakes;

import com.google.samples.apps.iosched.shared.data.login.LoginDataSource;

public class FakeLoginDataSource implements LoginDataSource {
    public String token;

    @Override
    public void getUserForToken(String token) {
        this.token = token;
    }
}