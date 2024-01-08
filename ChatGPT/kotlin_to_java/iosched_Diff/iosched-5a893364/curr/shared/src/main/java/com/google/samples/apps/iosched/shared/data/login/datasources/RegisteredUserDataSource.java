package com.google.samples.apps.iosched.shared.data.login.datasources;

import android.arch.lifecycle.LiveData;
import com.google.samples.apps.iosched.shared.result.Result;

public interface RegisteredUserDataSource {

    void listenToUserChanges(String userId);

    LiveData<Result<Boolean>> observeResult();

    void clearListener();
}