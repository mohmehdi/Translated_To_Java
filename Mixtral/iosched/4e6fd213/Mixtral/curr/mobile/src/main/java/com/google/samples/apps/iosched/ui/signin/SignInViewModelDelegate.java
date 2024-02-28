
package com.google.samples.apps.iosched.ui.signin;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.NotificationsPrefIsShownUseCase;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.signin.SignInEvent.RequestSignOut;

import javax.inject.Inject;

public enum class SignInEvent {
    RequestSignIn, RequestSignOut
}

public interface SignInViewModelDelegate {

    @NonNull
    LiveData<AuthenticatedUserInfo?> currentUserInfo();

    @NonNull
    LiveData<Uri?> currentUserImageUri();

    @NonNull
    LiveData<Event<SignInEvent>> performSignInEvent();

    @NonNull
    LiveData<Event<Boolean>> shouldShowNotificationsPrefAction();

    void emitSignInRequest();

    void emitSignOutRequest();

    @NonNull
    LiveData<Boolean> observeSignedInUser();

    @NonNull
    LiveData<Boolean> observeRegisteredUser();

    boolean isSignedIn();

    boolean isRegistered();

    @Nullable
    String getUserId();
}

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.result.Result;

import javax.inject.Inject;

public class FirebaseSignInViewModelDelegate implements SignInViewModelDelegate {

    @NonNull
    private final ObserveUserAuthStateUseCase observeUserAuthStateUseCase;
    @NonNull
    private final NotificationsPrefIsShownUseCase notificationsPrefIsShownUseCase;
    @NonNull
    private final MutableLiveData<Event<SignInEvent>> performSignInEvent = new MutableLiveData<>();
    @NonNull
    private final LiveData<AuthenticatedUserInfo?> currentUserInfo;
    @NonNull
    private final LiveData<Uri?> currentUserImageUri;
    @NonNull
    private final MediatorLiveData<Event<Boolean>> shouldShowNotificationsPrefAction = new MediatorLiveData<>();
    @NonNull
    private final LiveData<Boolean> _isRegistered;
    @NonNull
    private final LiveData<Boolean> _isSignedIn;
    @NonNull
    private final MutableLiveData<Result<Boolean>> notificationsPrefIsShown = new MutableLiveData<>();

    @Inject
    public FirebaseSignInViewModelDelegate(@NonNull ObserveUserAuthStateUseCase observeUserAuthStateUseCase,
                                           @NonNull NotificationsPrefIsShownUseCase notificationsPrefIsShownUseCase) {
        this.observeUserAuthStateUseCase = observeUserAuthStateUseCase;
        this.notificationsPrefIsShownUseCase = notificationsPrefIsShownUseCase;

        currentUserInfo = Transformations.map(observeUserAuthStateUseCase.observe(), result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<AuthenticatedUserInfo>) result).getData();
            }
            return null;
        });

        currentUserImageUri = Transformations.map(currentUserInfo, user -> {
            if (user != null) {
                return user.getPhotoUrl();
            }
            return null;
        });

        _isSignedIn = Transformations.map(currentUserInfo, isSignedIn -> {
            if (isSignedIn != null) {
                return isSignedIn.isSignedIn();
            }
            return false;
        });

        _isRegistered = Transformations.map(currentUserInfo, isRegistered -> {
            if (isRegistered != null) {
                return isRegistered.isRegistered();
            }
            return false;
        });

        observeUserAuthStateUseCase.execute(new Object());

        shouldShowNotificationsPrefAction.addSource(notificationsPrefIsShown, this::showNotificationPref);
        shouldShowNotificationsPrefAction.addSource(_isSignedIn, isSignedInBooleanLiveData -> {
            notificationsPrefIsShown.setValue(null);
            notificationsPrefIsShownUseCase.execute(new Object(), notificationsPrefIsShown);
        });
    }

    private void showNotificationPref() {
        Boolean result = (notificationsPrefIsShown.getValue() != null &&
                (notificationsPrefIsShown.getValue().getData() == false)) && isSignedIn();

        if (result && (shouldShowNotificationsPrefAction.getValue() == null ||
                !shouldShowNotificationsPrefAction.getValue().hasBeenHandled())) {
            shouldShowNotificationsPrefAction.setValue(new Event<>(true));
        }
    }

    @Override
    public void emitSignInRequest() {
        notificationsPrefIsShownUseCase.execute(new Object(), notificationsPrefIsShown);
        performSignInEvent.postValue(new Event<>(SignInEvent.RequestSignIn));
    }

    @Override
    public void emitSignOutRequest() {
        performSignInEvent.postValue(new Event<>(new RequestSignOut()));
    }

    @Override
    public boolean isSignedIn() {
        if (currentUserInfo.getValue() != null) {
            return currentUserInfo.getValue().isSignedIn();
        }
        return false;
    }

    @Override
    public boolean isRegistered() {
        if (currentUserInfo.getValue() != null) {
            return currentUserInfo.getValue().isRegistered();
        }
        return false;
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
        if (currentUserInfo.getValue() != null) {
            return currentUserInfo.getValue().getUid();
        }
        return null;
    }
}