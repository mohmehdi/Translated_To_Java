package com.google.samples.apps.iosched.ui.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.login.LoginEvent;
import com.google.samples.apps.iosched.ui.login.LoginViewModelPlugin;
import com.google.samples.apps.iosched.ui.schedule.Event;
import javax.inject.Inject;

public enum LoginEvent {
    RequestLogin, RequestLogout
}

public interface LoginViewModelPlugin {
    
    LiveData<Result<AuthenticatedUserInfo>> getCurrentFirebaseUser();
    
    LiveData<Uri> getCurrentUserImageUri();
    
    MutableLiveData<Event<LoginEvent>> getPerformLoginEvent();
    
    void emitLoginRequest();
    
    void emitLogoutRequest();
    
    LiveData<Boolean> observeLoggedInUser();
    
    LiveData<Boolean> observeRegisteredUser();
    
    boolean isLoggedIn();
    
    boolean isRegistered();
}

internal class FirebaseLoginViewModelPlugin implements LoginViewModelPlugin {

    private MutableLiveData<Event<LoginEvent>> performLoginEvent;
    private LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private LiveData<Uri> currentUserImageUri;
    private LiveData<Boolean> isRegistered;
    private LiveData<Boolean> isLoggedIn;
    private ObserveUserAuthStateUseCase observeUserAuthStateUseCase;

    @Inject
    public FirebaseLoginViewModelPlugin(ObserveUserAuthStateUseCase observeUserAuthStateUseCase) {
        this.observeUserAuthStateUseCase = observeUserAuthStateUseCase;
        performLoginEvent = new MutableLiveData<>();
        currentFirebaseUser = observeUserAuthStateUseCase.observe();
        currentUserImageUri = currentFirebaseUser.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<AuthenticatedUserInfo>) result).getData().getPhotoUrl();
            }
            return null;
        });
        isLoggedIn = currentFirebaseUser.map(value -> isLoggedIn());
        isRegistered = currentFirebaseUser.map(value -> isRegistered());
        observeUserAuthStateUseCase.execute(new Any());
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfo>> getCurrentFirebaseUser() {
        return currentFirebaseUser;
    }

    @Override
    public LiveData<Uri> getCurrentUserImageUri() {
        return currentUserImageUri;
    }

    @Override
    public MutableLiveData<Event<LoginEvent>> getPerformLoginEvent() {
        return performLoginEvent;
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