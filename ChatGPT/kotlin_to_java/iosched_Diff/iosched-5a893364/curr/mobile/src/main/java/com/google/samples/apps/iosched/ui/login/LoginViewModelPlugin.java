package com.google.samples.apps.iosched.ui.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.schedule.Event;

import javax.inject.Inject;

public enum LoginEvent {
    RequestLogin, RequestLogout
}

interface LoginViewModelPlugin {

    LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;

    LiveData<Uri> currentUserImageUri;

    MutableLiveData<Event<LoginEvent>> performLoginEvent;

    void emitLoginRequest();

    void emitLogoutRequest();

    LiveData<Boolean> observeLoggedInUser();

    LiveData<Boolean> observeRegisteredUser();

    boolean isLoggedIn();

    boolean isRegistered();
}

public class FirebaseLoginViewModelPlugin implements LoginViewModelPlugin {

    private final MutableLiveData<Event<LoginEvent>> performLoginEvent = new MutableLiveData<>();
    private final LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private final LiveData<Uri> currentUserImageUri;
    private final LiveData<Boolean> isLoggedIn;
    private final LiveData<Boolean> isRegistered;

    @Inject
    public FirebaseLoginViewModelPlugin(ObserveUserAuthStateUseCase observeUserAuthStateUseCase) {
        this.currentFirebaseUser = observeUserAuthStateUseCase.observe();

        this.currentUserImageUri = map(currentFirebaseUser, result ->
                ((Result.Success<AuthenticatedUserInfo>) result).getData().getPhotoUrl()
        );

        this.isLoggedIn = map(currentFirebaseUser, value -> isLoggedIn());

        this.isRegistered = map(currentFirebaseUser, value -> isRegistered());

        observeUserAuthStateUseCase.execute(new Object());
    }

    @Override
    public void emitLoginRequest() {
        performLoginEvent.postValue(new Event<>(LoginEvent.RequestLogin));
    }

    @Override
    public void emitLogoutRequest() {
        performLoginEvent.postValue(new Event<>(LoginEvent.RequestLogout));
    }

    @Override
    public LiveData<Boolean> observeLoggedInUser() {
        return isLoggedIn;
    }

    @Override
    public LiveData<Boolean> observeRegisteredUser() {
        return isRegistered;
    }

    @Override
    public boolean isLoggedIn() {
        return ((Result.Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isLoggedIn();
    }

    @Override
    public boolean isRegistered() {
        return ((Result.Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isRegistered();
    }
}
