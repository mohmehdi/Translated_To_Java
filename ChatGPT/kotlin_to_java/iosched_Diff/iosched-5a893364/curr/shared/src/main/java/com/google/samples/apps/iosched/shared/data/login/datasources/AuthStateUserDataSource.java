package com.google.samples.apps.iosched.shared.data.login.datasources;

import android.arch.lifecycle.LiveData;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.result.Result;

public interface AuthStateUserDataSource {

    void startListening();

    LiveData<String> getUserId();

    LiveData<Result<AuthenticatedUserInfoBasic>> getBasicUserInfo();

    void clearListener();
}