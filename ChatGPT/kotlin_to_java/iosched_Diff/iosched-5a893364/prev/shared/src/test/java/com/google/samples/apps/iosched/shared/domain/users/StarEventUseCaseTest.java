package com.google.samples.apps.iosched.shared.domain.users;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository;
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.repository.TestUserEventDataSource;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult;
import com.google.samples.apps.iosched.shared.firestore.entity.LastReservationRequested;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.TestData;
import com.google.samples.apps.iosched.shared.model.TestDataRepository;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.SyncExecutorRule;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class StarEventUseCaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncExecutorRule syncExecutorRule = new SyncExecutorRule();

    @Test
    public void sessionIsStarredSuccessfully() {
        SessionAndUserEventRepository testUserEventRepository = new DefaultSessionAndUserEventRepository(
                new TestUserEventDataSource(), new DefaultSessionRepository(TestDataRepository.INSTANCE));
        StarEventUseCase useCase = new StarEventUseCase(testUserEventRepository);

        LiveData<Result<StarUpdatedStatus>> resultLiveData = useCase.observe();

        useCase.execute(new StarEventParameter("userIdTest", TestData.INSTANCE.getSession0(), true));

        Result<StarUpdatedStatus> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertEquals(result, Result.Success(StarUpdatedStatus.STARRED));
    }

    @Test
    public void sessionIsStarredUnsuccessfully() {
        StarEventUseCase useCase = new StarEventUseCase(FailingSessionAndUserEventRepository.INSTANCE);

        LiveData<Result<StarUpdatedStatus>> resultLiveData = useCase.observe();

        useCase.execute(new StarEventParameter("userIdTest", TestData.INSTANCE.getSession0(), true));

        Result<StarUpdatedStatus> result = LiveDataTestUtil.getValue(resultLiveData);
        Assert.assertTrue(result instanceof Result.Error);
    }

    private static class FailingSessionAndUserEventRepository implements SessionAndUserEventRepository {

        private MutableLiveData<Result<StarUpdatedStatus>> result = new MutableLiveData<>();

        @Override
        public LiveData<Result<StarUpdatedStatus>> updateIsStarred(String userId, Session session, boolean isStarred) {
            result.postValue(new Result.Error(new Exception("Test")));
            return result;
        }

        @Override
        public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LiveData<Result<LastReservationRequested>> changeReservation(String userId, Session session, ReservationRequestAction action) {
            throw new UnsupportedOperationException();
        }
    }
}