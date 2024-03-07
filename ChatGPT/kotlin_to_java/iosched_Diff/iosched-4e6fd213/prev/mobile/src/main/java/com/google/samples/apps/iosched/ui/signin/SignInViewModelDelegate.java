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
import com.google.samples.apps.iosched.shared.result.Result.Success;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.signin.SignInEvent;
import javax.inject.Inject;

enum SignInEvent {
    RequestSignIn, RequestSignOut
}

interface SignInViewModelDelegate {
    
    LiveData<Result<AuthenticatedUserInfo>> getCurrentFirebaseUser();
    
    LiveData<Uri> getCurrentUserImageUri();
    
    MutableLiveData<Event<SignInEvent>> getPerformSignInEvent();
    
    LiveData<Event<Boolean>> getShouldShowNotificationsPrefAction();
    
    void emitSignInRequest();
    
    void emitSignOutRequest();
    
    LiveData<Boolean> observeSignedInUser();
    
    LiveData<Boolean> observeRegisteredUser();
    
    boolean isSignedIn();
    
    boolean isRegistered();
    
    String getUserId();
}

class FirebaseSignInViewModelDelegate implements SignInViewModelDelegate {

    private MutableLiveData<Event<SignInEvent>> performSignInEvent = new MutableLiveData<>();
    private LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private LiveData<Uri> currentUserImageUri;
    private MediatorLiveData<Event<Boolean>> shouldShowNotificationsPrefAction = new MediatorLiveData<>();
    private LiveData<Boolean> _isRegistered;
    private LiveData<Boolean> _isSignedIn;
    private MutableLiveData<Result<Boolean>> notificationsPrefIsShown = new MutableLiveData<>();

    public FirebaseSignInViewModelDelegate(ObserveUserAuthStateUseCase observeUserAuthStateUseCase, NotificationsPrefIsShownUseCase notificationsPrefIsShownUseCase) {
        currentFirebaseUser = observeUserAuthStateUseCase.observe();

        currentUserImageUri = map(currentFirebaseUser, result -> {
            return ((result instanceof Success) ? ((Success<AuthenticatedUserInfo>) result).getData().getPhotoUrl() : null);
        });

        _isSignedIn = map(currentFirebaseUser, this::isSignedIn);

        _isRegistered = map(currentFirebaseUser, this::isRegistered);

        observeUserAuthStateUseCase.execute(new Object());

        shouldShowNotificationsPrefAction.addSource(notificationsPrefIsShown, this::showNotificationPref);

        shouldShowNotificationsPrefAction.addSource(_isSignedIn, value -> {
            notificationsPrefIsShown.setValue(null);
            notificationsPrefIsShownUseCase.invoke(Unit, notificationsPrefIsShown);
        });
    }

    private void showNotificationPref() {
        boolean result = (((notificationsPrefIsShown.getValue() instanceof Success) ? ((Success<Boolean>) notificationsPrefIsShown.getValue()).getData() : false) && isSignedIn());

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
        return (((currentFirebaseUser.getValue() instanceof Success) ? ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isSignedIn() : false));
    }

    @Override
    public boolean isRegistered() {
        return (((currentFirebaseUser.getValue() instanceof Success) ? ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isRegistered() : false));
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
        Result<AuthenticatedUserInfo> user = currentFirebaseUser.getValue();
        return ((user instanceof Success) ? ((Success<AuthenticatedUserInfo>) user).getData().getUid() : null);
    }
}