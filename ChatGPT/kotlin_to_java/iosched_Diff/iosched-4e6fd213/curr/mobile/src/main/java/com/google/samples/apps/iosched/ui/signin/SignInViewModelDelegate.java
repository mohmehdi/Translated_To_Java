package com.google.samples.apps.iosched.ui.signin;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.NotificationsPrefIsShownUseCase;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.signin.SignInEvent;
import javax.inject.Inject;

import static com.google.samples.apps.iosched.shared.result.Result.Success;

enum SignInEvent {
    RequestSignIn, RequestSignOut
}

interface SignInViewModelDelegate {

    LiveData<AuthenticatedUserInfo> currentUserInfo;

    LiveData<Uri> currentUserImageUri;

    MutableLiveData<Event<SignInEvent>> performSignInEvent;

    LiveData<Event<Boolean>> shouldShowNotificationsPrefAction;

    void emitSignInRequest();

    void emitSignOutRequest();

    LiveData<Boolean> observeSignedInUser();

    LiveData<Boolean> observeRegisteredUser();

    boolean isSignedIn();

    boolean isRegistered();

    String getUserId();
}

class FirebaseSignInViewModelDelegate implements SignInViewModelDelegate {

    MutableLiveData<Event<SignInEvent>> performSignInEvent;
    LiveData<AuthenticatedUserInfo> currentUserInfo;
    LiveData<Uri> currentUserImageUri;
    MediatorLiveData<Event<Boolean>> shouldShowNotificationsPrefAction;

    LiveData<Boolean> _isRegistered;
    LiveData<Boolean> _isSignedIn;

    MutableLiveData<Result<Boolean>> notificationsPrefIsShown;

    FirebaseSignInViewModelDelegate(ObserveUserAuthStateUseCase observeUserAuthStateUseCase, NotificationsPrefIsShownUseCase notificationsPrefIsShownUseCase) {
        this.performSignInEvent = new MutableLiveData<>();
        this.currentUserInfo = null;
        this.currentUserImageUri = null;
        this.shouldShowNotificationsPrefAction = new MediatorLiveData<>();

        this._isRegistered = null;
        this._isSignedIn = null;
        this.notificationsPrefIsShown = new MutableLiveData<>();

        this.currentUserInfo = observeUserAuthStateUseCase.observe().map(result -> {
            return (result instanceof Success) ? ((Success) result).getData() : null;
        });

        this.currentUserImageUri = currentUserInfo.map(user -> {
            return (user != null) ? user.getPhotoUrl() : null;
        });

        this._isSignedIn = currentUserInfo.map(this::isSignedIn);

        this._isRegistered = currentUserInfo.map(this::isRegistered);

        observeUserAuthStateUseCase.execute(new Object());

        shouldShowNotificationsPrefAction.addSource(notificationsPrefIsShown, this::showNotificationPref);

        shouldShowNotificationsPrefAction.addSource(_isSignedIn, value -> {
            notificationsPrefIsShown.setValue(null);
            notificationsPrefIsShownUseCase.invoke(Unit, notificationsPrefIsShown);
        });
    }

    private void showNotificationPref() {
        boolean result = ((notificationsPrefIsShown.getValue() instanceof Success) && !((Boolean) ((Success) notificationsPrefIsShown.getValue()).getData()) && isSignedIn());

        if (result && (shouldShowNotificationsPrefAction.getValue() == null || !shouldShowNotificationsPrefAction.getValue().hasBeenHandled())) {
            shouldShowNotificationsPrefAction.setValue(new Event<>(true));
        }
    }

    @Override
    public void emitSignInRequest() {
        notificationsPrefIsShownUseCase.invoke(Unit, notificationsPrefIsShown);

        performSignInEvent.postValue(new Event<>(SignInEvent.RequestSignIn));
    }

    @Override
    public void emitSignOutRequest() {
        performSignInEvent.postValue(new Event<>(SignInEvent.RequestSignOut));
    }

    @Override
    public boolean isSignedIn() {
        return (currentUserInfo.getValue() != null) && currentUserInfo.getValue().isSignedIn();
    }

    @Override
    public boolean isRegistered() {
        return (currentUserInfo.getValue() != null) && currentUserInfo.getValue().isRegistered();
    }

    @Override
    public LiveData<Boolean> observeSignedInUser() {
        return _isSignedIn;
    }

    @Override
    public LiveData<Boolean> observeRegisteredUser() {
        return _isRegistered;
    }

    @Override
    public String getUserId() {
        return (currentUserInfo.getValue() != null) ? currentUserInfo.getValue().getUid() : null;
    }
}