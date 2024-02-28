

package com.google.samples.apps.iosched.test.util.fakes;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.ui.signin.SignInEvent;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.nhaarman.mockitokotlin2.Mockito;

import java.util.concurrent.Callable;

public class FakeSignInViewModelDelegate implements SignInViewModelDelegate {

    private MutableLiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser = new MutableLiveData<>();
    private MutableLiveData<Uri> currentUserImageUri = new MutableLiveData<>();
    private MutableLiveData<Event<SignInEvent>> performSignInEvent = new MutableLiveData<>();
    private MutableLiveData<Event<Boolean>> shouldShowNotificationsPrefAction = new MutableLiveData<>();

    private boolean injectIsSignedIn = true;
    private int signInRequestsEmitted = 0;
    private int signOutRequestsEmitted = 0;

    @Override
    public boolean isSignedIn() {
        return injectIsSignedIn;
    }

    @NonNull
    @Override
    public MutableLiveData<Result<AuthenticatedUserInfo>> observeSignedInUser() {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    @Override
    public MutableLiveData<Boolean> observeRegisteredUser() {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        result.postValue(injectIsSignedIn);
        return result;
    }

    @Override
    public boolean isRegistered() {
        return injectIsSignedIn;
    }

    @Override
    public void emitSignInRequest() {
        signInRequestsEmitted++;
    }

    @Override
    public void emitSignOutRequest() {
        signOutRequestsEmitted++;
    }

    @Nullable
    @Override
    public String getUserId() {
        Result<AuthenticatedUserInfo> user = currentFirebaseUser.getValue();
        if (user != null && user instanceof Result.Success) {
            AuthenticatedUserInfo data = ((Result.Success<AuthenticatedUserInfo>) user).getData();
            return data.getUid();
        }
        return null;
    }

    public void loadUser(String id) {
        AuthenticatedUserInfo mockUser = Mockito.mock(AuthenticatedUserInfo.class);
        Mockito.when(mockUser.getUid()).thenReturn(id);
        Mockito.when(mockUser.getPhotoUrl()).thenReturn(Mockito.mock(Uri.class));
        Mockito.when(mockUser.isSignedIn()).thenReturn(true);
        Mockito.when(mockUser.isRegistrationDataReady()).thenReturn(true);
        currentFirebaseUser.postValue(Result.success(mockUser));
    }
}