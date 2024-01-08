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
import javax.inject.Inject;

public enum SignInEvent {
    RequestSignIn, RequestSignOut
}

public interface SignInViewModelDelegate {
    
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

internal class FirebaseSignInViewModelDelegate implements SignInViewModelDelegate {
    
    private MutableLiveData<Event<SignInEvent>> performSignInEvent = new MutableLiveData<>();
    private LiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser;
    private LiveData<Uri> currentUserImageUri;
    private MediatorLiveData<Event<Boolean>> shouldShowNotificationsPrefAction = new MediatorLiveData<>();
    private LiveData<Boolean> isRegistered;
    private LiveData<Boolean> isSignedIn;
    private MutableLiveData<Result<Boolean>> notificationsPrefIsShown = new MutableLiveData<>();

    public FirebaseSignInViewModelDelegate(ObserveUserAuthStateUseCase observeUserAuthStateUseCase, NotificationsPrefIsShownUseCase notificationsPrefIsShownUseCase) {
        currentFirebaseUser = observeUserAuthStateUseCase.observe();

        currentUserImageUri = currentFirebaseUser.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<AuthenticatedUserInfo>) result).getData().getPhotoUrl();
            }
            return null;
        });

        isSignedIn = currentFirebaseUser.map(result -> isSignedIn());

        isRegistered = currentFirebaseUser.map(result -> isRegistered());

        observeUserAuthStateUseCase.execute(new Object());

        shouldShowNotificationsPrefAction.addSource(notificationsPrefIsShown, this::showNotificationPref);

        shouldShowNotificationsPrefAction.addSource(isSignedIn, value -> {
            notificationsPrefIsShown.setValue(null);
            notificationsPrefIsShownUseCase.invoke(Unit, notificationsPrefIsShown);
        });
    }

    private void showNotificationPref() {
        boolean result = notificationsPrefIsShown.getValue() instanceof Result.Success &&
                !((Result.Success<Boolean>) notificationsPrefIsShown.getValue()).getData() &&
                isSignedIn();

        if (result && (shouldShowNotificationsPrefAction.getValue() == null ||
                !shouldShowNotificationsPrefAction.getValue().hasBeenHandled())
        ) {
            shouldShowNotificationsPrefAction.setValue(new Event<>(true));
        }
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
        performSignInEvent.postValue(new Event<>(SignInEvent.RequestSignOut));
    }

    @Override
    public boolean isSignedIn() {
        return currentFirebaseUser.getValue() instanceof Result.Success &&
                ((Result.Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isSignedIn();
    }

    @Override
    public boolean isRegistered() {
        return currentFirebaseUser.getValue() instanceof Result.Success &&
                ((Result.Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().isRegistered();
    }

    @Override
    public LiveData<Boolean> observeSignedInUser() {
        return isSignedIn;
    }

    @Override
    public LiveData<Boolean> observeRegisteredUser() {
        return isRegistered;
    }

    @Override
    public String getUserId() {
        Result<AuthenticatedUserInfo> user = currentFirebaseUser.getValue();
        return user instanceof Result.Success ? ((Result.Success<AuthenticatedUserInfo>) user).getData().getUid() : null;
    }
}