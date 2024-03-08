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
    private MutableLiveData<Result<Boolean?>> result = new MutableLiveData<>();

    public StagingRegisteredUserDataSource(boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    @Override
    public void listenToUserChanges(String userId) {
        result.postValue(new Result.Success<>(isRegistered));
    }

    @Override
    public LiveData<Result<Boolean?>> observeResult() {
        return result;
    }

    @Override
    public void clearListener() {

    }
}

public class StagingAuthenticatedUserInfo implements AuthenticatedUserInfo {
    private Context context;
    private boolean registered;
    private boolean loggedIn;
    private String userId;

    public StagingAuthenticatedUserInfo(Context context, boolean registered, boolean loggedIn, String userId) {
        this.context = context;
        this.registered = registered;
        this.loggedIn = loggedIn;
        this.userId = userId;
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
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public MutableList<? extends UserInfo> getProviderData() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isAnonymous() {
        return !loggedIn;
    }

    @Override
    public String getPhoneNumber() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getUid() {
        return userId;
    }

    @Override
    public boolean isEmailVerified() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getDisplayName() {
        throw new UnsupportedOperationException("Not implemented");
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
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getProviderId() {
        throw new UnsupportedOperationException("Not implemented");
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

public class StagingAuthStateUserDataSource implements AuthStateUserDataSource {
    private boolean isLoggedIn;
    private boolean isRegistered;
    private String userId;
    private Context context;
    private MutableLiveData<String?> _userId = new MutableLiveData<>();
    private MutableLiveData<Result<AuthenticatedUserInfoBasic?>> _firebaseUser = new MutableLiveData<>();
    private StagingAuthenticatedUserInfo user;

    public StagingAuthStateUserDataSource(boolean isLoggedIn, boolean isRegistered, String userId, Context context) {
        this.isLoggedIn = isLoggedIn;
        this.isRegistered = isRegistered;
        this.userId = userId;
        this.context = context;
        this.user = new StagingAuthenticatedUserInfo(context, isRegistered, isLoggedIn, userId);
    }

    @Override
    public void startListening() {
        _userId.postValue(userId);
        _firebaseUser.postValue(new Result.Success<>(user));
    }

    @Override
    public MutableLiveData<String?> getUserId() {
        return _userId;
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic?>> getBasicUserInfo() {
        return _firebaseUser;
    }

    @Override
    public void clearListener() {

    }
}