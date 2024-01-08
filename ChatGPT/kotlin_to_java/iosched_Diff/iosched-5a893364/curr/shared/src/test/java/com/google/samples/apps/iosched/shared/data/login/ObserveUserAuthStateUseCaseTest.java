package com.google.samples.apps.iosched.shared.data.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.login.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

public class ObserveUserAuthStateUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    @Test
    public void userSuccessRegistered() {

        ObserveUserAuthStateUseCase subject = createObserveUserAuthStateUseCase(
                false,
                true,
                true,
                TEST_USER_ID
        );


        subject.execute(new Object());

        Result.Success value = (Result.Success) LiveDataTestUtil.getValue(subject.observe());

        MatcherAssert.assertThat(
                value.getData().getUid(),
                Matchers.is(TEST_USER_ID)
        );
        MatcherAssert.assertThat(
                value.getData().isLoggedIn(),
                Matchers.is(true)
        );
        MatcherAssert.assertThat(
                value.getData().isRegistered(),
                Matchers.is(true)
        );

    }

    @Test
    public void userSuccessNotRegistered() {

        ObserveUserAuthStateUseCase subject = createObserveUserAuthStateUseCase(
                false,
                false,
                true,
                TEST_USER_ID
        );


        subject.execute(new Object());

        Result.Success value = (Result.Success) LiveDataTestUtil.getValue(subject.observe());

        MatcherAssert.assertThat(
                value.getData().getUid(),
                Matchers.is(TEST_USER_ID)
        );
        MatcherAssert.assertThat(
                value.getData().isLoggedIn(),
                Matchers.is(true)
        );
        MatcherAssert.assertThat(
                value.getData().isRegistered(),
                Matchers.is(false)
        );
    }

    @Test
    public void userErrorNotRegistered() {

        ObserveUserAuthStateUseCase subject = createObserveUserAuthStateUseCase(
                false,
                true,
                false,
                TEST_USER_ID
        );


        subject.execute(new Object());

        Result value = LiveDataTestUtil.getValue(subject.observe());

        MatcherAssert.assertThat(
                value,
                Matchers.is(Matchers.instanceOf(Result.Error.class))
        );
    }

    private ObserveUserAuthStateUseCase createObserveUserAuthStateUseCase(
            boolean isAnonymous,
            boolean isRegistered,
            boolean isSuccess,
            String userId
    ) {
        RegisteredUserDataSource registeredUserDataSource = new FakeRegisteredUserDataSource(isRegistered);
        AuthStateUserDataSource authStateUserDataSource = new FakeAuthStateUserDataSource(isAnonymous, isSuccess, userId);

        ObserveUserAuthStateUseCase subject = new ObserveUserAuthStateUseCase(registeredUserDataSource, authStateUserDataSource);
        return subject;
    }
}

class FakeRegisteredUserDataSource implements RegisteredUserDataSource {
    private boolean isRegistered;
    private MutableLiveData<Result<Boolean>> result = new MutableLiveData<>();

    @Override
    public void listenToUserChanges(String userId) {
        result.postValue(new Result.Success<>(isRegistered));
    }

    @Override
    public LiveData<Result<Boolean>> observeResult() {
        return result;
    }

    @Override
    public void clearListener() {

    }
}

class FakeAuthStateUserDataSource implements AuthStateUserDataSource {
    private boolean isAnonymous;
    private boolean successFirebaseUser;
    private String userId;
    private MutableLiveData<String> _userId = new MutableLiveData<>();
    private MutableLiveData<Result<AuthenticatedUserInfoBasic>> _firebaseUser = new MutableLiveData<>();

    public FakeAuthStateUserDataSource(boolean isAnonymous, boolean successFirebaseUser, String userId) {
        this.isAnonymous = isAnonymous;
        this.successFirebaseUser = successFirebaseUser;
        this.userId = userId;
    }

    @Override
    public void startListening() {
        _userId.postValue(userId);

        if (successFirebaseUser) {
            AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
            doReturn(isAnonymous).when(mockUser).isAnonymous();
            doReturn(TEST_USER_ID).when(mockUser).getUid();
            doReturn(true).when(mockUser).isLoggedIn();
            _firebaseUser.postValue(new Result.Success<>(mockUser));
        } else {
            _firebaseUser.postValue(new Result.Error<>(new Exception("Test")));
        }
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

    }
}