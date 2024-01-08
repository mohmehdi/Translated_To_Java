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
import com.google.samples.apps.iosched.ui.signin.SignInEvent.RequestSignOut;
import javax.inject.Inject;

public enum SignInEvent {
    RequestSignIn, RequestSignOut
}

public interface SignInViewModelDelegate {
    
    LiveData<AuthenticatedUserInfo> getCurrentUserInfo();
    
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

internal class FirebaseSignInViewModelDelegate implements SignInViewModelDelegate {
    
    private MutableLiveData<Event<SignInEvent>> performSignInEvent = new MutableLiveData<>();
    private LiveData<AuthenticatedUserInfo> currentUserInfo;
    private LiveData<Uri> currentUserImageUri;
    private MediatorLiveData<Event<Boolean>> shouldShowNotificationsPrefAction = new MediatorLiveData<>();
    private LiveData<Boolean> _isRegistered;
    private LiveData<Boolean> _isSignedIn;
    private MutableLiveData<Result<Boolean>> notificationsPrefIsShown = new MutableLiveData<>();

    @Inject
    public FirebaseSignInViewModelDelegate(ObserveUserAuthStateUseCase observeUserAuthStateUseCase, NotificationsPrefIsShownUseCase notificationsPrefIsShownUseCase) {
        currentUserInfo = observeUserAuthStateUseCase.observe().map(result -> {
            if (result instanceof Success) {
                return ((Success<AuthenticatedUserInfo>) result).getData();
            }
            return null;
        });

        currentUserImageUri = currentUserInfo.map(user -> {
            if (user != null) {
                return user.getPhotoUrl();
            }
            return null;
        });

        _isSignedIn = currentUserInfo.map(user -> isSignedIn());

        _isRegistered = currentUserInfo.map(user -> isRegistered());

        observeUserAuthStateUseCase.execute(new Object());

        shouldShowNotificationsPrefAction.addSource(notificationsPrefIsShown, value -> showNotificationPref());

        shouldShowNotificationsPrefAction.addSource(_isSignedIn, value -> {
            notificationsPrefIsShown.setValue(null);
            notificationsPrefIsShownUseCase.invoke(Unit, notificationsPrefIsShown);
        });
    }

    private void showNotificationPref() {
        boolean result = notificationsPrefIsShown.getValue() instanceof Success && ((Success<Boolean>) notificationsPrefIsShown.getValue()).getData() == false && isSignedIn();

        if (result && (shouldShowNotificationsPrefAction.getValue() == null || !shouldShowNotificationsPrefAction.getValue().hasBeenHandled())) {
            shouldShowNotificationsPrefAction.setValue(new Event<>(true));
        }
    }

    @Override
    public LiveData<AuthenticatedUserInfo> getCurrentUserInfo() {
        return currentUserInfo;
    }

    @Override
    public LiveData<Uri> getCurrentUserImageUri() {
        return currentUserImageUri;
    }

    @Override
    public MutableLiveData<Event<SignInEvent>> getPerformSignInEvent() {
        return performSignInEvent;
    }

    @Override
    public LiveData<Event<Boolean>> getShouldShowNotificationsPrefAction() {
        return shouldShowNotificationsPrefAction;
    }

    @Override
    public void emitSignInRequest() {
        notificationsPrefIsShownUseCase.invoke(Unit, notificationsPrefIsShown);
        performSignInEvent.postValue(new Event<>(SignInEvent.RequestSignIn));
    }

    @Override
    public void emitSignOutRequest() {
        performSignInEvent.postValue(new Event<>(RequestSignOut));
    }

    @Override
    public boolean isSignedIn() {
        return currentUserInfo.getValue() != null && currentUserInfo.getValue().isSignedIn();
    }

    @Override
    public boolean isRegistered() {
        return currentUserInfo.getValue() != null && currentUserInfo.getValue().isRegistered();
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
        return currentUserInfo.getValue() != null ? currentUserInfo.getValue().getUid() : null;
    }
}