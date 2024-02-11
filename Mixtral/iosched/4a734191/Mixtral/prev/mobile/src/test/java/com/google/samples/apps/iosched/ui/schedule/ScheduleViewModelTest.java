

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.model.TestData;
import com.google.samples.apps.iosched.shared.data.session.SessionRepository;
import com.google.samples.apps.iosched.shared.data.tag.TagRepository;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    private LoadSessionsByDayUseCase loadSessionsUseCase;
    private LoadTagsByCategoryUseCase loadTagsUseCase;

    @Before
    public void setup() {
        loadSessionsUseCase = createSessionsUseCase(TestData.sessionsMap);
        loadTagsUseCase = createTagsUseCase(TestData.tagsList);
    }

    @Test
    public void testDataIsLoaded_ObservablesUpdated() {
        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadTagsUseCase);

        for (ConferenceDay day : ConferenceDay.values()) {
            Assert.assertEquals(TestData.sessionsMap.get(day),
                    LiveDataTestUtil.getValue(viewModel.getSessionsForDay(day)));
        }
        Assert.assertFalse(LiveDataTestUtil.getValue(viewModel.isLoading));

        Assert.assertEquals(TestData.tagsList, LiveDataTestUtil.getValue(viewModel.tags));
    }

    @Test
    public void testDataIsLoaded_Fails() {
        loadSessionsUseCase = createSessionsExceptionUseCase();
        loadTagsUseCase = createTagsExceptionUseCase();

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadTagsUseCase);

        Assert.assertTrue(!LiveDataTestUtil.getValue(viewModel.errorMessage).isEmpty());
    }

    private LoadSessionsByDayUseCase createSessionsUseCase(
            Map<ConferenceDay, List<Session>> sessions) {
        return new LoadSessionsByDayUseCase(new SessionRepository(new TestSessionDataSource())) {
            @Override
            public Map<ConferenceDay, List<Session>> execute(SessionFilters filters) {
                return sessions;
            }
        };
    }

    private LoadSessionsByDayUseCase createSessionsExceptionUseCase() {
        return new LoadSessionsByDayUseCase(new SessionRepository(new TestSessionDataSource())) {
            @Override
            public Map<ConferenceDay, List<Session>> execute(SessionFilters filters) {
                throw new Exception("Testing exception");
            }
        };
    }

    private LoadTagsByCategoryUseCase createTagsUseCase(List<Tag> tags) {
        return new LoadTagsByCategoryUseCase(new TagRepository(new TestSessionDataSource())) {
            @Override
            public List<Tag> execute(Unit parameters) {
                return tags;
            }
        };
    }

    private LoadTagsByCategoryUseCase createTagsExceptionUseCase() {
        return new LoadTagsByCategoryUseCase(new TagRepository(new TestSessionDataSource())) {
            @Override
            public List<Tag> execute(Unit parameters) {
                throw new Exception("Testing exception");
            }
        };
    }
}