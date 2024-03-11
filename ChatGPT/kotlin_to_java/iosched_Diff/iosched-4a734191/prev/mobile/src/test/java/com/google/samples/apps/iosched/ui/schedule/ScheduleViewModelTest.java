package com.google.samples.apps.iosched.ui.schedule;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.model.TestData;
import com.google.samples.apps.iosched.shared.data.session.SessionRepository;
import com.google.samples.apps.iosched.shared.data.tag.TagRepository;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testDataIsLoaded_ObservablesUpdated() {

        LoadSessionsByDayUseCase loadSessionsUseCase = createSessionsUseCase(TestData.sessionsMap);
        LoadTagsByCategoryUseCase loadTagsUseCase = createTagsUseCase(TestData.tagsList);

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadTagsUseCase);

        for (ConferenceDay day : ConferenceDay.values()) {
            Assert.assertEquals(TestData.sessionsMap.get(day),
                    LiveDataTestUtil.getValue(viewModel.getSessionsForDay(day)));
        }
        Assert.assertFalse(LiveDataTestUtil.getValue(viewModel.isLoading()));
        Assert.assertEquals(TestData.tagsList, LiveDataTestUtil.getValue(viewModel.getTags()));
    }

    @Test
    public void testDataIsLoaded_Fails() {
        LoadSessionsByDayUseCase loadSessionsUseCase = createSessionsExceptionUseCase();
        LoadTagsByCategoryUseCase loadTagsUseCase = createTagsExceptionUseCase();

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadTagsUseCase);

        Assert.assertTrue(!LiveDataTestUtil.getValue(viewModel.getErrorMessage()).isNullOrEmpty());
    }

    private LoadSessionsByDayUseCase createSessionsUseCase(
            Map<ConferenceDay, List<Session>> sessions) {
        return new LoadSessionsByDayUseCase(new SessionRepository(TestSessionDataSource)) {
            @Override
            public Map<ConferenceDay, List<Session>> execute(SessionFilters filters) {
                return sessions;
            }
        };
    }

    private LoadSessionsByDayUseCase createSessionsExceptionUseCase() {
        return new LoadSessionsByDayUseCase(new SessionRepository(TestSessionDataSource)) {
            @Override
            public Map<ConferenceDay, List<Session>> execute(SessionFilters filters) {
                throw new Exception("Testing exception");
            }
        };
    }

    private LoadTagsByCategoryUseCase createTagsUseCase(List<Tag> tags) {
        return new LoadTagsByCategoryUseCase(new TagRepository(TestSessionDataSource)) {
            @Override
            public List<Tag> execute(Unit parameters) {
                return tags;
            }
        };
    }

    private LoadTagsByCategoryUseCase createTagsExceptionUseCase() {
        return new LoadTagsByCategoryUseCase(new TagRepository(TestSessionDataSource)) {
            @Override
            public List<Tag> execute(Unit parameters) {
                throw new Exception("Testing exception");
            }
        };
    }
}