

package com.google.samples.apps.iosched.ui.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.login.LoginEvent;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogin;
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogout;
import com.google.samples.apps.iosched.ui.schedule.Event;

import javax.inject.Inject;

public enum LoginEvent {
    RequestLogin,
    RequestLogout
}

public interface LoginViewModelPlugin {



    MutableLiveData<Event<LoginEvent>> performLoginEvent = new MutableLiveData<>();

    default void emitLoginRequest() {
        performLoginEvent.postValue(new Event<>(RequestLogin));
    }

    default void emitLogoutRequest() {
        performLoginEvent.postValue(new Event<>(RequestLogout));
    }

    LiveData<Boolean> observeLoggedInUser();

    LiveData<Boolean> observeRegisteredUser();

    boolean isLoggedIn();

    boolean isRegistered();
}

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import javax.inject.Inject;

public class FirebaseLoginViewModelPlugin implements LoginViewModelPlugin {

    private final ObserveUserAuthStateUseCase observeUserAuthStateUseCase;

    @NonNull
    private final MutableLiveData<Event<LoginEvent>> performLoginEvent;
    @NonNull
    private final LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    @NonNull
    private final LiveData<Uri> currentUserImageUri;

    @NonNull
    private final LiveData<Boolean> _isRegistered;
    @NonNull
    private final LiveData<Boolean> _isLoggedIn;

    @Inject
    public FirebaseLoginViewModelPlugin(@NonNull ObserveUserAuthStateUseCase observeUserAuthStateUseCase) {
        this.observeUserAuthStateUseCase = observeUserAuthStateUseCase;

        currentFirebaseUser = observeUserAuthStateUseCase.observe();

        currentUserImageUri = new MutableLiveData<Uri>() {
            {
                currentFirebaseUser.observeForever(result -> {
                    setValue(map.call(result, r -> ((AuthenticatedUserInfo) r.getValue()).getPhotoUrl()));
                });
            }
        };

        _isLoggedIn = new MutableLiveData<Boolean>() {
            {
                currentFirebaseUser.observeForever(value -> {
                    setValue(isLoggedIn());
                });
            }
        };

        _isRegistered = new MutableLiveData<Boolean>() {
            {
                currentFirebaseUser.observeForever(value -> {
                    setValue(isRegistered());
                });
            }
        };

        observeUserAuthStateUseCase.execute(new Object());
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser() {
        return currentFirebaseUser;
    }

    @Override
    public LiveData<Uri> currentUserImageUri() {
        return currentUserImageUri;
    }

    @Override
    public boolean isLoggedIn() {
        return ((currentFirebaseUser.getValue() != null) && (currentFirebaseUser.getValue().isSuccess()))
                ? ((AuthenticatedUserInfo) ((Result) currentFirebaseUser.getValue()).getValue()).isLoggedIn()
                : false;
    }

    @Override
    public boolean isRegistered() {
        return ((currentFirebaseUser.getValue() != null) && (currentFirebaseUser.getValue().isSuccess()))
                ? ((AuthenticatedUserInfo) ((Result) currentFirebaseUser.getValue()).getValue()).isRegistered()
                : false;
    }

    @Override
    public LiveData<Boolean> observeLoggedInUser() {
        return _isLoggedIn;
    }

    @Override
    public LiveData<Boolean> observeRegisteredUser() {
        return _isRegistered;
    }

    @Override
    public void emitLoginRequest() {
        performLoginEvent.postValue(new Event<>(RequestLogin));
    }

    @Override
    public void emitLogoutRequest() {
        performLoginEvent.postValue(new Event<>(RequestLogout));
    }
}
