

package com.google.samples.apps.iosched.tv.ui.schedule;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.rule.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.RxSchedulersOverrideRule;
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

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

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
            Map<String, Map<String, ? extends Object>> actual = LiveDataTestUtil.getValue(
                    viewModel.getSessionsGroupedByTimeForDay(day));
            Assert.assertEquals(actual, TestData.sessionsByDayGroupedByTimeMap.get(day));
        }

        Assert.assertThat("Once sessions are loaded, isLoading should be false",
                LiveDataTestUtil.getValue(viewModel.isLoading),
                CoreMatchers.`is`(false));
    }

    @Test
    public void testDataIsLoaded_ErrorMessageOnFailure() {
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createSessionsExceptionUseCase();

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase);

        Assert.assertFalse(LiveDataTestUtil.getValue(viewModel.errorMessage) == null ||
                LiveDataTestUtil.getValue(viewModel.errorMessage).isEmpty());
    }

    @NonNull
    private LoadUserSessionsByDayUseCase createUseCase() {
        return new LoadUserSessionsByDayUseCase(
                new DefaultSessionAndUserEventRepository(
                        new TestUserEventDataSource(),
                        new DefaultSessionRepository(new TestDataRepository())));
    }

    @NonNull
    private LoadUserSessionsByDayUseCase createSessionsExceptionUseCase() {
        DefaultSessionRepository sessionRepository = new DefaultSessionRepository(new TestDataRepository());
        DefaultSessionAndUserEventRepository userEventRepository = new DefaultSessionAndUserEventRepository(
                new TestUserEventDataSource(), sessionRepository);

        return new LoadUserSessionsByDayUseCase(userEventRepository) {
            @Override
            public void execute(Pair<UserSessionMatcher, String> parameters) {
                result.postValue(new Result.Error<>(new Exception("Testing exception")));
            }
        };
    }
}