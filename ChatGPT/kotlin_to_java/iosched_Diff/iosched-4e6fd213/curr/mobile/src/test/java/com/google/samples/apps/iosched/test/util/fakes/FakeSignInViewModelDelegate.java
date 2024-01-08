package com.google.samples.apps.iosched.test.util.fakes;

import android.net.Uri;
import androidx.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.ui.signin.SignInEvent;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;

public class FakeSignInViewModelDelegate implements SignInViewModelDelegate {

    public MutableLiveData<AuthenticatedUserInfo> currentUserInfo = new MutableLiveData<>();
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
    public void observeSignedInUser() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public MutableLiveData<Boolean> observeRegisteredUser() {
        MutableLiveData<Boolean> registeredUser = new MutableLiveData<>();
        registeredUser.setValue(injectIsSignedIn);
        return registeredUser;
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
        return currentUserInfo.getValue().getUid();
    }

    public void loadUser(String id) {
        AuthenticatedUserInfo mockUser = mock(AuthenticatedUserInfo.class);
        doReturn(id).when(mockUser).getUid();
        doReturn(mock(Uri.class)).when(mockUser).getPhotoUrl();
        doReturn(true).when(mockUser).isSignedIn();
        doReturn(true).when(mockUser).isRegistrationDataReady();
        currentUserInfo.postValue(mockUser);
    }
}