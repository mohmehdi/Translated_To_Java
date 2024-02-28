
package com.google.samples.apps.iosched.test.util.fakes;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.ui.signin.SignInEvent;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.doReturn;

public class FakeSignInViewModelDelegate implements SignInViewModelDelegate {

    @NonNull
    private MutableLiveData<AuthenticatedUserInfo?> currentUserInfo;
    @NonNull
    private MutableLiveData<Uri?> currentUserImageUri;
    @NonNull
    private MutableLiveData<Event<SignInEvent>> performSignInEvent;
    @NonNull
    private MutableLiveData<Event<Boolean>> shouldShowNotificationsPrefAction;

    private boolean injectIsSignedIn;
    private int signInRequestsEmitted;
    private int signOutRequestsEmitted;

    public FakeSignInViewModelDelegate() {
        currentUserInfo = new MutableLiveData<>();
        currentUserImageUri = new MutableLiveData<>();
        performSignInEvent = new MutableLiveData<>();
        shouldShowNotificationsPrefAction = new MutableLiveData<>();
    }

    @Override
    public boolean isSignedIn() {
        return injectIsSignedIn;
    }

    @Override
    public void observeSignedInUser() {
        // TODO: Not implemented
    }

    @Override
    public MutableLiveData<Boolean> observeRegisteredUser() {
        MutableLiveData<Boolean> liveData = new MutableLiveData<>();
        liveData.setValue(injectIsSignedIn);
        return liveData;
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
        AuthenticatedUserInfo userInfo = currentUserInfo.getValue();
        if (userInfo != null) {
            return userInfo.getUid();
        }
        return null;
    }

    public void loadUser(String id) {
        AuthenticatedUserInfo mockUser = mock(AuthenticatedUserInfo.class);
        when(mockUser.getUid()).thenReturn(id);
        when(mockUser.getPhotoUrl()).thenReturn(mock(Uri.class));
        when(mockUser.isSignedIn()).thenReturn(true);
        when(mockUser.isRegistrationDataReady()).thenReturn(true);
        currentUserInfo.postValue(mockUser);
    }
}