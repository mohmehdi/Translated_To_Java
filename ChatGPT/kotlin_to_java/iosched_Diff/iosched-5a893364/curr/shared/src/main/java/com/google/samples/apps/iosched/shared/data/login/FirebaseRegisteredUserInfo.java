package com.google.samples.apps.iosched.shared.data.login;

import android.net.Uri;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

public class FirebaseRegisteredUserInfo implements AuthenticatedUserInfo {

    private AuthenticatedUserInfoBasic basicUserInfo;
    private Boolean isRegistered;

    public FirebaseRegisteredUserInfo(AuthenticatedUserInfoBasic basicUserInfo, Boolean isRegistered) {
        this.basicUserInfo = basicUserInfo;
        this.isRegistered = isRegistered;
    }

    @Override
    public boolean isRegistered() {
        return isRegistered != null ? isRegistered : false;
    }

    @Override
    public boolean isLoggedIn() {
        return basicUserInfo != null && basicUserInfo.isLoggedIn();
    }

    @Override
    public String getEmail() {
        return basicUserInfo != null ? basicUserInfo.getEmail() : null;
    }

    @Override
    public MutableList<? extends UserInfo> getProviderData() {
        return basicUserInfo != null ? basicUserInfo.getProviderData() : null;
    }

    @Override
    public Boolean isAnonymous() {
        return basicUserInfo != null ? basicUserInfo.isAnonymous() : null;
    }

    @Override
    public String getPhoneNumber() {
        return basicUserInfo != null ? basicUserInfo.getPhoneNumber() : null;
    }

    @Override
    public String getUid() {
        return basicUserInfo != null ? basicUserInfo.getUid() : null;
    }

    @Override
    public Boolean isEmailVerified() {
        return basicUserInfo != null ? basicUserInfo.isEmailVerified() : null;
    }

    @Override
    public String getDisplayName() {
        return basicUserInfo != null ? basicUserInfo.getDisplayName() : null;
    }

    @Override
    public Uri getPhotoUrl() {
        return basicUserInfo != null ? basicUserInfo.getPhotoUrl() : null;
    }

    @Override
    public MutableList<String> getProviders() {
        return basicUserInfo != null ? basicUserInfo.getProviders() : null;
    }

    @Override
    public String getProviderId() {
        return basicUserInfo != null ? basicUserInfo.getProviderId() : null;
    }

    @Override
    public Long getLastSignInTimestamp() {
        return basicUserInfo != null ? basicUserInfo.getLastSignInTimestamp() : null;
    }

    @Override
    public Long getCreationTimestamp() {
        return basicUserInfo != null ? basicUserInfo.getCreationTimestamp() : null;
    }
}

public class FirebaseUserInfo implements AuthenticatedUserInfoBasic {

    private FirebaseUser firebaseUser;

    public FirebaseUserInfo(FirebaseUser firebaseUser) {
        this.firebaseUser = firebaseUser;
    }

    @Override
    public boolean isLoggedIn() {
        return firebaseUser != null;
    }

    @Override
    public String getEmail() {
        return firebaseUser != null ? firebaseUser.getEmail() : null;
    }

    @Override
    public MutableList<? extends UserInfo> getProviderData() {
        return firebaseUser != null ? firebaseUser.getProviderData() : null;
    }

    @Override
    public Boolean isAnonymous() {
        return firebaseUser != null ? firebaseUser.isAnonymous() : null;
    }

    @Override
    public String getPhoneNumber() {
        return firebaseUser != null ? firebaseUser.getPhoneNumber() : null;
    }

    @Override
    public String getUid() {
        return firebaseUser != null ? firebaseUser.getUid() : null;
    }

    @Override
    public Boolean isEmailVerified() {
        return firebaseUser != null ? firebaseUser.isEmailVerified() : null;
    }

    @Override
    public String getDisplayName() {
        return firebaseUser != null ? firebaseUser.getDisplayName() : null;
    }

    @Override
    public Uri getPhotoUrl() {
        return firebaseUser != null ? firebaseUser.getPhotoUrl() : null;
    }

    @Override
    public MutableList<String> getProviders() {
        return firebaseUser != null ? firebaseUser.getProviders() : null;
    }

    @Override
    public String getProviderId() {
        return firebaseUser != null ? firebaseUser.getProviderId() : null;
    }

    @Override
    public Long getLastSignInTimestamp() {
        return firebaseUser != null ? firebaseUser.getMetadata().getLastSignInTimestamp() : null;
    }

    @Override
    public Long getCreationTimestamp() {
        return firebaseUser != null ? firebaseUser.getMetadata().getCreationTimestamp() : null;
    }
}