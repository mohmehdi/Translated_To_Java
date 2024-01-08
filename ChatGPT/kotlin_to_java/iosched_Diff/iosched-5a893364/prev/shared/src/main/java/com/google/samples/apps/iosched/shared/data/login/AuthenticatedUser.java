package com.google.samples.apps.iosched.shared.data.login;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.samples.apps.iosched.shared.result.Result;
import timber.log.Timber;

public interface AuthenticatedUser {

    LiveData<Result<String>> getToken();

    LiveData<Result<AuthenticatedUserInfo>> getCurrentUser();
}

class FirebaseAuthenticatedUser implements AuthenticatedUser {

    private MutableLiveData<Result<String>> tokenChangedObservable = new MutableLiveData<>();

    private MutableLiveData<Result<AuthenticatedUserInfo>> currentUserObservable = new MutableLiveData<>();

    public FirebaseAuthenticatedUser(FirebaseAuth firebase) {
        currentUserObservable.setValue(null);

        firebase.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth auth) {
                currentUserObservable.postValue(new Result.Success<>(new FirebaseUserInfo(auth.getCurrentUser())));
                FirebaseUser currentUser = auth.getCurrentUser();
                if (currentUser != null) {
                    Task<GetTokenResult> tokenTask = currentUser.getIdToken(false);
                    tokenTask.addOnCompleteListener(new OnCompleteListener<GetTokenResult>() {
                        @Override
                        public void onComplete(@NonNull Task<GetTokenResult> result) {
                            if (result.isSuccessful() && result.getResult().getToken() != null) {
                                tokenChangedObservable.postValue(new Result.Success<>(result.getResult().getToken()));
                            } else {
                                Timber.e(result.getException() != null ? result.getException().getMessage() : "Error getting ID token");
                                tokenChangedObservable.postValue(new Result.Error<>(result.getException() != null ? result.getException() : new RuntimeException("Error getting ID token")));
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public LiveData<Result<String>> getToken() {
        return tokenChangedObservable;
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfo>> getCurrentUser() {
        return currentUserObservable;
    }
}