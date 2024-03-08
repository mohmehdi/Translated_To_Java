package com.google.samples.apps.iosched.shared.data.login;

import android.net.Uri;
import com.google.firebase.auth.UserInfo;

public interface AuthenticatedUserInfo extends AuthenticatedUserInfoBasic, AuthenticatedUserInfoRegistered {
}

public interface AuthenticatedUserInfoBasic {

    boolean isLoggedIn();

    String getEmail();

    MutableList<? extends UserInfo> getProviderData();

    Long getLastSignInTimestamp();

    Long getCreationTimestamp();

    Boolean isAnonymous();

    String getPhoneNumber();

    String getUid();

    Boolean isEmailVerified();

    String getDisplayName();

    Uri getPhotoUrl();

    MutableList<String> getProviders();

    String getProviderId();
}

public interface AuthenticatedUserInfoRegistered {

    boolean isRegistered();
}