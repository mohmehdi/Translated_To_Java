package com.google.samples.apps.iosched.tv.ui.schedule;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository;
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.tv.model.TestData;
import com.google.samples.apps.iosched.tv.model.TestDataRepository;
import com.google.samples.apps.iosched.tv.model.TestUserEventDataSource;
import com.google.samples.apps.iosched.tv.util.SyncTaskExecutorRule;
import org.hamcrest.core.Is;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testDataIsLoaded_ObservablesUpdated() {

        LoadUserSessionsByDayUseCase loadSessionsUseCase = createUseCase();

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase);

        for (TimeUtils.ConferenceDay day : TimeUtils.ConferenceDay.values()) {
            Object actual = LiveDataTestUtil.getValue(viewModel.getSessionsGroupedByTimeForDay(day));
            assertEquals(actual, TestData.sessionsByDayGroupedByTimeMap.get(day));
        }

        assertThat("Once sessions are loaded, isLoading should be false",
                LiveDataTestUtil.getValue(viewModel.isLoading),
                Is.is(false));
    }

    @Test
    public void testDataIsLoaded_ErrorMessageOnFailure() {
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createSessionsExceptionUseCase();

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase);

        assertFalse(LiveDataTestUtil.getValue(viewModel.errorMessage).isNullOrBlank());
    }

    private LoadUserSessionsByDayUseCase createUseCase() {
        return new LoadUserSessionsByDayUseCase(
                new DefaultSessionAndUserEventRepository(
                        TestUserEventDataSource, new DefaultSessionRepository(TestDataRepository)));
    }

    private LoadUserSessionsByDayUseCase createSessionsExceptionUseCase() {
        DefaultSessionRepository sessionRepository = new DefaultSessionRepository(TestDataRepository);
        DefaultSessionAndUserEventRepository userEventRepository = new DefaultSessionAndUserEventRepository(
                TestUserEventDataSource, sessionRepository);

        return new LoadUserSessionsByDayUseCase(userEventRepository) {
            @Override
            public void execute(Pair<UserSessionMatcher, String> parameters) {
                result.postValue(new Result.Error(new Exception("Testing exception")));
            }
        };
    }
}