

package com.google.samples.apps.iosched.shared.data.login.datasources;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import com.google.firebase.auth.UserInfo;
import com.google.samples.apps.iosched.shared.R;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.result.Result;

public class StagingRegisteredUserDataSource implements RegisteredUserDataSource {
    private MutableLiveData<Result<Boolean?>> result;
    private final boolean isRegistered;

    public StagingRegisteredUserDataSource(boolean isRegistered) {
        this.isRegistered = isRegistered;
        this.result = new MutableLiveData<>();
    }

    @Override
    public void listenToUserChanges(String userId) {
        result.postValue(Result.Success(isRegistered));
    }

    @Override
    public LiveData<Result<Boolean?>> observeResult() {
        return result;
    }

    @Override
    public void clearListener() {
        // No-op
    }
}

abstract class StagingAuthenticatedUserInfo implements AuthenticatedUserInfo {
    private final Context context;
    private final boolean registered;
    private final boolean loggedIn;
    private final String userId;

    public StagingAuthenticatedUserInfo(Context context, boolean registered, boolean loggedIn, String userId) {
        this.context = context;
        this.registered = registered;
        this.loggedIn = loggedIn;
        this.userId = userId != null ? userId : "StagingUser";
    }

    @Override
    public boolean isLoggedIn() {
        return loggedIn;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public String getEmail() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public MutableList<UserInfo> getProviderData() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isAnonymous() {
        return !loggedIn;
    }

    @Override
    public String getPhoneNumber() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getUid() {
        return userId;
    }

    @Override
    public boolean isEmailVerified() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getDisplayName() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Uri getPhotoUrl() {
        ContentResolver resolver = context.getContentResolver();
        String packageName = context.getResources().getResourcePackageName(R.drawable.staging_user_profile);
        String typeName = context.getResources().getResourceTypeName(R.drawable.staging_user_profile);
        String entryName = context.getResources().getResourceEntryName(R.drawable.staging_user_profile);
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(packageName)
                .appendPath(typeName)
                .appendPath(entryName)
                .build();
        return uri;
    }

    @Override
    public MutableList<String> getProviders() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getProviderId() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Long getLastSignInTimestamp() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public Long getCreationTimestamp() {
        throw new RuntimeException("not implemented");
    }
}

class StagingAuthStateUserDataSource implements AuthStateUserDataSource {
    private final MutableLiveData<String> _userId;
    private final MutableLiveData<Result<AuthenticatedUserInfoBasic>> _firebaseUser;
    private final StagingAuthenticatedUserInfo user;
    private final boolean isLoggedIn;
    private final boolean isRegistered;
    private final String userId;
    private final Context context;

    public StagingAuthStateUserDataSource(boolean isLoggedIn, boolean isRegistered, String userId, Context context) {
        this.isLoggedIn = isLoggedIn;
        this.isRegistered = isRegistered;
        this.userId = userId != null ? userId : "StagingUser";
        this.context = context;
        this._userId = new MutableLiveData<>();
        this._firebaseUser = new MutableLiveData<>();
        this.user = new StagingAuthenticatedUserInfo(context, isRegistered, isLoggedIn, userId);
    }

    @Override
    public void startListening() {
        _userId.postValue(userId);
        _firebaseUser.postValue(Result.Success(user));
    }

    @Override
    public MutableLiveData<String> getUserId() {
        return _userId;
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic>> getBasicUserInfo() {
        return _firebaseUser;
    }

    @Override
    public void clearListener() {
        // No-op
    }
}