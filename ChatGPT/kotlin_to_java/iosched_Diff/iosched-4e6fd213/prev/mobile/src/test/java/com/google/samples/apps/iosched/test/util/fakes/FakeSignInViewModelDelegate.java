package com.google.samples.apps.iosched.test.util.fakes;

import android.net.Uri;
import androidx.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.ui.signin.SignInEvent;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;

public class FakeSignInViewModelDelegate implements SignInViewModelDelegate {

    public MutableLiveData<Result<AuthenticatedUserInfo>> currentFirebaseUser = new MutableLiveData<>();
    public MutableLiveData<Uri> currentUserImageUri = new MutableLiveData<>();
    public MutableLiveData<Event<SignInEvent>> performSignInEvent = new MutableLiveData<>();
    public MutableLiveData<Event<Boolean>> shouldShowNotificationsPrefAction = new MutableLiveData<>();

    public boolean injectIsSignedIn = true;
    public int signInRequestsEmitted = 0;
    public int signOutRequestsEmitted = 0;

    @Override
    public boolean isSignedIn() {
        return injectIsSignedIn;
    }

    @Override
    public MutableLiveData<Boolean> observeSignedInUser() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public MutableLiveData<Boolean> observeRegisteredUser() {
        MutableLiveData<Boolean> data = new MutableLiveData<>();
        data.setValue(injectIsSignedIn);
        return data;
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

    @Override
    public String getUserId() {
        Result<AuthenticatedUserInfo> user = currentFirebaseUser.getValue();
        return ((Result.Success) user).getData().getUid();
    }

    public void loadUser(String id) {
        AuthenticatedUserInfo mockUser = mock(AuthenticatedUserInfo.class);
        doReturn(id).when(mockUser).getUid();
        doReturn(mock(Uri.class)).when(mockUser).getPhotoUrl();
        doReturn(true).when(mockUser).isSignedIn();
        doReturn(true).when(mockUser).isRegistrationDataReady();
        currentFirebaseUser.postValue(new Result.Success<>(mockUser));
    }
}