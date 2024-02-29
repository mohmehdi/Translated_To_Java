

package com.google.samples.apps.iosched.shared.data.login;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.rule.junit.JUnitRule;
import android.support.test.runner.AndroidJUnit4;
import com.google.samples.apps.iosched.shared.data.login.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.nhaarman.mockito_kotlin.mock;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(AndroidJUnit4.class)
public class ObserveUserAuthStateUseCaseTest {

    @Rule
    @JvmField
    public InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private static final String TEST_USER_ID = "testuser";

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
                is(equalTo(TEST_USER_ID))
        );
        MatcherAssert.assertThat(
                value.getData().isLoggedIn(),
                is(equalTo(true))
        );
        MatcherAssert.assertThat(
                value.getData().isRegistered(),
                is(equalTo(true))
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
                is(equalTo(TEST_USER_ID))
        );
        MatcherAssert.assertThat(
                value.getData().isLoggedIn(),
                is(equalTo(true))
        );
        MatcherAssert.assertThat(
                value.getData().isRegistered(),
                is(equalTo(false))
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

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(subject.observe()),
                is(instanceOf(Result.Error.class))
        );
    }

    private ObserveUserAuthStateUseCase createObserveUserAuthStateUseCase(
            boolean isAnonymous,
            boolean isRegistered,
            boolean isSuccess,
            String userId
    ) {
        AuthStateUserDataSource authStateUserDataSource = new FakeAuthStateUserDataSource(isAnonymous, isSuccess, userId);
        RegisteredUserDataSource registeredUserDataSource = new FakeRegisteredUserDataSource(isRegistered);

        ObserveUserAuthStateUseCase subject = new ObserveUserAuthStateUseCase(registeredUserDataSource, authStateUserDataSource);
        return subject;
    }

    private static class FakeRegisteredUserDataSource implements RegisteredUserDataSource {
        private final MutableLiveData<Result<Boolean>> result = new MutableLiveData<>();
        private final boolean isRegistered;

        FakeRegisteredUserDataSource(boolean isRegistered) {
            this.isRegistered = isRegistered;
        }

        @Override
        public void listenToUserChanges(@NonNull String userId) {
            result.postValue(new Result.Success(isRegistered));
        }

        @NonNull
        @Override
        public LiveData<Result<Boolean>> observeResult() {
            return result;
        }

        @Override
        public void clearListener() {

        }
    }

    private static class FakeAuthStateUserDataSource implements AuthStateUserDataSource {
        private final MutableLiveData<String> _userId = new MutableLiveData<>();
        private final MutableLiveData<Result<AuthenticatedUserInfoBasic>> _firebaseUser = new MutableLiveData<>();
        private final boolean isAnonymous;
        private final boolean successFirebaseUser;
        private final String userId;

        FakeAuthStateUserDataSource(boolean isAnonymous, boolean successFirebaseUser, String userId) {
            this.isAnonymous = isAnonymous;
            this.successFirebaseUser = successFirebaseUser;
            this.userId = userId;
        }

        @Override
        public void startListening() {
            _userId.postValue(userId);

            if (successFirebaseUser) {
                AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
                Mockito.when(mockUser.isAnonymous()).thenReturn(isAnonymous);
                Mockito.when(mockUser.getUid()).thenReturn(TEST_USER_ID);
                Mockito.when(mockUser.isLoggedIn()).thenReturn(true);
                _firebaseUser.postValue(new Result.Success(mockUser));
            } else {
                _firebaseUser.postValue(new Result.Error(new Exception("Test")));
            }
        }

        @NonNull
        @Override
        public MutableLiveData<String> getUserId() {
            return _userId;
        }

        @NonNull
        @Override
        public LiveData<Result<AuthenticatedUserInfoBasic>> getBasicUserInfo() {
            return _firebaseUser;
        }

        @Override
        public void clearListener() {

        }
    }
}