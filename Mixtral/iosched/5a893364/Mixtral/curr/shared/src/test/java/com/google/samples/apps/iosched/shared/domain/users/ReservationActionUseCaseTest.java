

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
import com.google.samples.apps.iosched.test.util.StarUpdatedStatus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ReservationActionUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncExecutorRule syncExecutorRule = new SyncExecutorRule();

    private ReservationActionUseCase useCase;
    private SessionAndUserEventRepository testUserEventRepository;
    private SessionAndUserEventRepository failingUserEventRepository;

    @Before
    public void setUp() {
        testUserEventRepository = new TestUserEventRepository();
        failingUserEventRepository = new FailingUserEventRepository();
        useCase = new ReservationActionUseCase(testUserEventRepository);
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
    public void requestFails() throws InterruptedException {
        useCase = new ReservationActionUseCase(failingUserEventRepository);
        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        final CountDownLatch latch = new CountDownLatch(1);
        Observer<Result<LastReservationRequested>> observer = result -> {
            Assert.assertTrue(result instanceof Result.Error);
            latch.countDown();
        };

        resultLiveData.observeForever(observer);

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.session0, ReservationRequestAction.CANCEL));

        latch.await(5, TimeUnit.SECONDS);
    }
}

class TestUserEventRepository implements SessionAndUserEventRepository {

    @NonNull
    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @Nullable String userId) {
        throw new RuntimeException("not implemented");
    }

    @NonNull
    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            String userId, Session session, boolean isStarred) {
        throw new RuntimeException("not implemented");
    }

    @NonNull
    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(
            String userId, Session session, ReservationRequestAction action) {

        MutableLiveData<Result<LastReservationRequested>> result = new MutableLiveData<>();
        result.postValue(Result.Success(
                action == ReservationRequestAction.REQUEST
                        ? LastReservationRequested.RESERVATION
                        : LastReservationRequested.CANCEL));
        return result;
    }

}

class FailingUserEventRepository implements SessionAndUserEventRepository {
    @NonNull
    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @Nullable String userId) {
        throw new RuntimeException("not implemented");
    }

    @NonNull
    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            String userId, Session session, boolean isStarred) {
        throw new RuntimeException("not implemented");
    }

    @NonNull
    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(
            String userId, Session session, ReservationRequestAction action) {
        throw new RuntimeException("Test");
    }
}