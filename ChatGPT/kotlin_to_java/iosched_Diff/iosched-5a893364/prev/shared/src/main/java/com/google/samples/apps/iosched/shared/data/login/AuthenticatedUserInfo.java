package com.google.samples.apps.iosched.shared.data.login;

import android.net.Uri;
import com.google.firebase.auth.UserInfo;
import java.util.List;

public interface AuthenticatedUserInfo {

    String getEmail();

    List<? extends UserInfo> getProviderData();

    Long getLastSignInTimestamp();

    Long getCreationTimestamp();

    Boolean isAnonymous();

    String getPhoneNumber();

    String getUid();

    Boolean isEmailVerified();

    String getDisplayName();

    Uri getPhotoUrl();

    List<String> getProviders();

    String getProviderId();

    Boolean isLoggedIn();
}