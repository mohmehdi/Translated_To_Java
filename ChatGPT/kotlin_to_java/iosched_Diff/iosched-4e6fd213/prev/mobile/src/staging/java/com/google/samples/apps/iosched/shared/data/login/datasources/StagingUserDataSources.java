package com.google.samples.apps.iosched.shared.data.login.datasources;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import com.google.firebase.auth.UserInfo;
import com.google.samples.apps.iosched.shared.R;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.data.signin.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.signin.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.result.Result;

public class StagingUserDataSources {
    public static class StagingRegisteredUserDataSource implements RegisteredUserDataSource {
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
        public void setAnonymousValue() {

        }
    }

    public static class StagingAuthenticatedUserInfo implements AuthenticatedUserInfo {
        private Context context;
        private boolean registered;
        private boolean signedIn;
        private String userId;

        public StagingAuthenticatedUserInfo(Context context, boolean registered, boolean signedIn, String userId) {
            this.context = context;
            this.registered = registered;
            this.signedIn = signedIn;
            this.userId = userId;
        }

        @Override
        public boolean isSignedIn() {
            return signedIn;
        }

        @Override
        public boolean isRegistered() {
            return registered;
        }

        @Override
        public boolean isRegistrationDataReady() {
            return true;
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
            return !signedIn;
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
        public String getProviderId() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Long getLastSignInTimestamp() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public Long getCreationTimestamp() {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    public static class StagingAuthStateUserDataSource implements AuthStateUserDataSource {
        private boolean isSignedIn;
        private boolean isRegistered;
        private String userId;
        private Context context;
        private MutableLiveData<String?> _userId = new MutableLiveData<>();
        private MutableLiveData<Result<AuthenticatedUserInfoBasic?>> _firebaseUser = new MutableLiveData<>();
        private StagingAuthenticatedUserInfo user;

        public StagingAuthStateUserDataSource(boolean isSignedIn, boolean isRegistered, String userId, Context context) {
            this.isSignedIn = isSignedIn;
            this.isRegistered = isRegistered;
            this.userId = userId;
            this.context = context;
            this.user = new StagingAuthenticatedUserInfo(isRegistered, isSignedIn, context);
        }

        @Override
        public void startListening() {
            _userId.postValue(userId);
            _firebaseUser.postValue(new Result.Success<>(user));
        }

        @Override
        public LiveData<Result<AuthenticatedUserInfoBasic?>> getBasicUserInfo() {
            return _firebaseUser;
        }

        @Override
        public void clearListener() {

        }
    }
}