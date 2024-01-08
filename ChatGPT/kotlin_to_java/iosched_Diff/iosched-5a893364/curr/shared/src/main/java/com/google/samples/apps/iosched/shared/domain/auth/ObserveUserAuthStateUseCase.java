package com.google.samples.apps.iosched.shared.domain.auth;

import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.data.login.FirebaseRegisteredUserInfo;
import com.google.samples.apps.iosched.shared.data.login.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.domain.MediatorUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import timber.log.Timber;
import javax.inject.Inject;

public class ObserveUserAuthStateUseCase extends MediatorUseCase<Object, AuthenticatedUserInfo> {

    private AuthStateUserDataSource authStateUserDataSource;
    private RegisteredUserDataSource registeredUserDataSource;
    private LiveData<AuthenticatedUserInfo> currentFirebaseUserObservable;
    private LiveData<String> userIdObservable;
    private LiveData<Result<Boolean>> isUserRegisteredObservable;

    @Inject
    public ObserveUserAuthStateUseCase(RegisteredUserDataSource registeredUserDataSource, AuthStateUserDataSource authStateUserDataSource) {
        this.registeredUserDataSource = registeredUserDataSource;
        this.authStateUserDataSource = authStateUserDataSource;
        this.currentFirebaseUserObservable = authStateUserDataSource.getBasicUserInfo();
        this.userIdObservable = authStateUserDataSource.getUserId();
        this.isUserRegisteredObservable = registeredUserDataSource.observeResult();

        result.addSource(userIdObservable, userId -> {
            if (userId != null) {
                registeredUserDataSource.listenToUserChanges(userId);
            }
        });

        result.addSource(currentFirebaseUserObservable, this::updateUserObservable);

        result.addSource(isUserRegisteredObservable, this::updateUserObservable);
    }

    @Override
    public void execute(Object parameters) {
        authStateUserDataSource.startListening();
    }

    private void updateUserObservable() {
        Timber.d("Updating observable user");
        AuthenticatedUserInfo currentFbUser = currentFirebaseUserObservable.getValue();
        Result<Boolean> isRegistered = isUserRegisteredObservable.getValue();

        if (currentFbUser instanceof Result.Success) {
            boolean isRegisteredValue = ((Result.Success<Boolean>) isRegistered).getData() == true;

            result.postValue(new Result.Success<>(new FirebaseRegisteredUserInfo(currentFbUser.getData(), isRegisteredValue)));
        } else {
            Timber.e("There was a registration error.");
            result.postValue(new Result.Error(new Exception("Registration error")));
        }
    }
}