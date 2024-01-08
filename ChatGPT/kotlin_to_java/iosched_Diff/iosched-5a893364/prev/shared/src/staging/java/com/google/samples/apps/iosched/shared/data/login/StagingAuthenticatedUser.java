package com.google.samples.apps.iosched.shared.data.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import com.google.firebase.auth.UserInfo;
import com.google.samples.apps.iosched.shared.R;
import com.google.samples.apps.iosched.shared.result.Result;

public class StagingAuthenticatedUser implements AuthenticatedUser {

    private Context context;
    private StagingLoggedInFirebaseUser stagingLoggedInFirebaseUser;
    private StagingLoggedOutFirebaseUser stagingLoggedOutFirebaseUser;
    private MutableLiveData<Result<AuthenticatedUserInfo>> currentUserResult;

    public StagingAuthenticatedUser(Context context) {
        this.context = context;
        stagingLoggedInFirebaseUser = new StagingLoggedInFirebaseUser(context);
        stagingLoggedOutFirebaseUser = new StagingLoggedOutFirebaseUser(context);
        currentUserResult = new MutableLiveData<>();
        currentUserResult.setValue(new Result.Success<>(stagingLoggedInFirebaseUser));
    }

    @Override
    public LiveData<Result<String>> getToken() {
        MutableLiveData<Result<String>> result = new MutableLiveData<>();
        result.setValue(new Result.Success<>("123"));
        return result;
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfo>> getCurrentUser() {
        return currentUserResult;
    }

    private boolean loggedIn;

    public void logIn() {
        loggedIn = true;
        currentUserResult.postValue(new Result.Success<>(stagingLoggedInFirebaseUser));
    }

    public void logOut() {
        loggedIn = false;
        currentUserResult.postValue(new Result.Success<>(stagingLoggedOutFirebaseUser));
    }
}

class StagingLoggedInFirebaseUser implements AuthenticatedUserInfo {

    private Context context;

    public StagingLoggedInFirebaseUser(Context context) {
        this.context = context;
    }

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public String getEmail() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public MutableList<? extends UserInfo> getProviderData() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isAnonymous() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getPhoneNumber() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getUid() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isEmailVerified() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getDisplayName() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Uri getPhotoUrl() {
        Resources resources = context.getResources();
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(R.drawable.staging_user_profile))
                .appendPath(resources.getResourceTypeName(R.drawable.staging_user_profile))
                .appendPath(resources.getResourceEntryName(R.drawable.staging_user_profile))
                .build();
        return uri;
    }

    @Override
    public MutableList<String> getProviders() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getProviderId() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Long getLastSignInTimestamp() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Long getCreationTimestamp() {
        throw new UnsupportedOperationException("not implemented");
    }
}

class StagingLoggedOutFirebaseUser extends StagingLoggedInFirebaseUser {

    public StagingLoggedOutFirebaseUser(Context context) {
        super(context);
    }

    @Override
    public boolean isLoggedIn() {
        return false;
    }

    @Override
    public Uri getPhotoUrl() {
        return null;
    }
}