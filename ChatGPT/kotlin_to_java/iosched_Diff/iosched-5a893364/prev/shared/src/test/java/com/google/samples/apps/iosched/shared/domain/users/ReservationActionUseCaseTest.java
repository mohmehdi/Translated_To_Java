package com.google.samples.apps.iosched.shared.domain.users;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult;
import com.google.samples.apps.iosched.shared.firestore.entity.LastReservationRequested;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.TestData;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.SyncExecutorRule;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ReservationActionUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncExecutorRule syncExecutorRule = new SyncExecutorRule();

    @Test
    public void sessionIsRequestedSuccessfully() {
        ReservationActionUseCase useCase = new ReservationActionUseCase(TestUserEventRepository.INSTANCE);

        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.INSTANCE.getSession0(), ReservationRequestAction.REQUEST));

        Result<LastReservationRequested> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertEquals(result, Result.Success(LastReservationRequested.RESERVATION));
    }

    @Test
    public void sessionIsCanceledSuccessfully() {
        ReservationActionUseCase useCase = new ReservationActionUseCase(TestUserEventRepository.INSTANCE);

        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.INSTANCE.getSession0(), ReservationRequestAction.CANCEL));

        Result<LastReservationRequested> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertEquals(result, Result.Success(LastReservationRequested.CANCEL));
    }

    @Test
    public void requestFails() {
        ReservationActionUseCase useCase = new ReservationActionUseCase(FailingUserEventRepository.INSTANCE);

        LiveData<Result<LastReservationRequested>> resultLiveData = useCase.observe();

        useCase.execute(new ReservationRequestParameters(
                "userTest", TestData.INSTANCE.getSession0(), ReservationRequestAction.CANCEL));

        Result<LastReservationRequested> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertTrue(result instanceof Result.Error);
    }
}

class TestUserEventRepository implements SessionAndUserEventRepository {

    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(String userId, Session session, boolean isStarred) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(String userId, Session session, ReservationRequestAction action) {
        MutableLiveData<Result<LastReservationRequested>> result = new MutableLiveData<>();
        result.postValue(new Result.Success<>(
                action == ReservationRequestAction.REQUEST ? LastReservationRequested.RESERVATION : LastReservationRequested.CANCEL));
        return result;
    }
}

class FailingUserEventRepository implements SessionAndUserEventRepository {

    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(String userId, Session session, boolean isStarred) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(String userId, Session session, ReservationRequestAction action) {
        throw new Exception("Test");
    }
}