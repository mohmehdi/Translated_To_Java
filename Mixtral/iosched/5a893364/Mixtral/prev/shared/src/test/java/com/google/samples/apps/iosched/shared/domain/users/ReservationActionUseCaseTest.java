

package com.google.samples.apps.iosched.shared.domain.users;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.rule.SyncExecutorRule;

import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult;
import com.google.samples.apps.iosched.shared.firestore.entity.LastReservationRequested;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.TestData;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.SyncExecutor;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.TestCoroutineRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReservationActionUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncExecutorRule syncExecutorRule = new SyncExecutorRule();

    @Rule
    public TestCoroutineRule testCoroutineRule = new TestCoroutineRule();

    @Mock
    private SessionAndUserEventRepository sessionAndUserEventRepository;

    private ReservationActionUseCase useCase;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        useCase = new ReservationActionUseCase(sessionAndUserEventRepository);
    }

    @Test
    public void sessionIsRequestedSuccessfully() {
        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.session0, ReservationRequestAction.REQUEST));

        Result<LastReservationRequested> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertEquals(result, Result.Success(LastReservationRequested.RESERVATION));
    }

    @Test
    public void sessionIsCanceledSuccessfully() {
        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.session0, ReservationRequestAction.CANCEL));

        Result<LastReservationRequested> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertEquals(result, Result.Success(LastReservationRequested.CANCEL));
    }

    @Test
    public void requestFails() {
        useCase = new ReservationActionUseCase(new FailingUserEventRepository());

        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.session0, ReservationRequestAction.CANCEL));

        Result<LastReservationRequested> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertTrue(result instanceof Result.Error);
    }
}

class TestUserEventRepository implements SessionAndUserEventRepository {
    private MutableLiveData<Result<LastReservationRequested>> result = new MutableLiveData<>();

    @NonNull
    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @NonNull String userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @NonNull
    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            @NonNull String userId, @NonNull Session session, boolean isStarred) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @NonNull
    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(
            @NonNull String userId, @NonNull Session session,
            @NonNull ReservationRequestAction action) {
        result.postValue(Result.Success(
                action == ReservationRequestAction.REQUEST ? LastReservationRequested.RESERVATION
                        : LastReservationRequested.CANCEL));
        return result;
    }
}

class FailingUserEventRepository implements SessionAndUserEventRepository {
    @NonNull
    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @NonNull String userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @NonNull
    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            @NonNull String userId, @NonNull Session session, boolean isStarred) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @NonNull
    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(
            @NonNull String userId, @NonNull Session session,
            @NonNull ReservationRequestAction action) {
        throw new RuntimeException("Test");
    }
}