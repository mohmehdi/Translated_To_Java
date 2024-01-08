package com.google.samples.apps.iosched.shared.data.login;

import android.net.Uri;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

import java.util.List;

public class FirebaseUserInfo implements AuthenticatedUserInfo {
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
    public List<? extends UserInfo> getProviderData() {
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
    public List<String> getProviders() {
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