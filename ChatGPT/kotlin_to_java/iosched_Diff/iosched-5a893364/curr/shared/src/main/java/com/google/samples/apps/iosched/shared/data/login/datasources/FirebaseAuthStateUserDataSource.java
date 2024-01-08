package com.google.samples.apps.iosched.shared.data.login.datasources;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserRegistration;
import com.google.samples.apps.iosched.shared.data.login.FirebaseUserInfo;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.result.Result;
import timber.log.Timber;
import javax.inject.Inject;

public class FirebaseAuthStateUserDataSource implements AuthStateUserDataSource {

    private MutableLiveData<String> userId = new MutableLiveData<>();

    private MutableLiveData<Result<String>> tokenChangedObservable = new MutableLiveData<>();

    private MutableLiveData<Result<AuthenticatedUserInfoBasic>> currentFirebaseUserObservable =
            new MutableLiveData<>();

    private FirebaseAuth firebase;

    private FirebaseAuth.AuthStateListener authStateListener = new FirebaseAuth.AuthStateListener() {
        @Override
        public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
            DefaultScheduler.execute(() -> {
                currentFirebaseUserObservable.postValue(Result.Success(
                        new FirebaseUserInfo(auth.getCurrentUser())));

                FirebaseUser currentUser = auth.getCurrentUser();
                if (currentUser != null) {
                    Tasks.await(currentUser.getIdToken(true)).addOnCompleteListener(task -> {
                        try {
                            GetTokenResult await = task.getResult();
                            if (await != null) {
                                String token = await.getToken();
                                if (token != null) {
                                    tokenChangedObservable.postValue(Result.Success(token));

                                    Timber.d("User authenticated, hitting registration endpoint");
                                    AuthenticatedUserRegistration.callRegistrationEndpoint(token);
                                }
                            }
                        } catch (Exception e) {
                            Timber.e(e);
                            tokenChangedObservable.postValue(Result.Error(e));
                        }
                    });

                    userId.postValue(currentUser.getUid());
                }
            });
        }
    };

    @Inject
    public FirebaseAuthStateUserDataSource(FirebaseAuth firebase) {
        this.firebase = firebase;
    }

    @Override
    public void startListening() {
        clearListener();
        firebase.addAuthStateListener(authStateListener);
    }

    @Override
    public LiveData<String> getUserId() {
        return userId;
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic>> getBasicUserInfo() {
        return currentFirebaseUserObservable;
    }

    @Override
    public void clearListener() {
        firebase.removeAuthStateListener(authStateListener);
    }
}