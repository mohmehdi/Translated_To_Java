

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.samples.apps.iosched.model.TestData;
import com.google.samples.apps.iosched.shared.data.session.SessionFilters;
import com.google.samples.apps.iosched.shared.data.session.SessionRepository;
import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaRepository;
import com.google.samples.apps.iosched.shared.data.tag.TagRepository;
import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.test.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.ui.schedule.agenda.LoadAgendaUseCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    private LoadSessionsByDayUseCase loadSessionsUseCase;
    private LoadAgendaUseCase loadAgendaUseCase;
    private LoadTagsByCategoryUseCase loadTagsUseCase;

    @Before
    public void setup() {
        loadSessionsUseCase = createSessionsUseCase(TestData.sessionsMap);
        loadAgendaUseCase = createAgendaUseCase(TestData.agenda);
        loadTagsUseCase = createTagsUseCase(TestData.tagsList);
    }

    @Test
    public void testDataIsLoaded_ObservablesUpdated() {
        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadAgendaUseCase, loadTagsUseCase);

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

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadAgendaUseCase, loadTagsUseCase);

        Assert.assertTrue(!LiveDataTestUtil.getValue(viewModel.errorMessage).isEmpty());
    }

    @NonNull
    private LoadSessionsByDayUseCase createSessionsUseCase(
            Map<ConferenceDay, List<Session>> sessions) {
        return new LoadSessionsByDayUseCase(new SessionRepository(new TestSessionDataSource())) {
            @NonNull
            @Override
            public Map<ConferenceDay, List<Session>> execute(@NonNull SessionFilters filters) {
                return sessions;
            }
        };
    }

    @NonNull
    private LoadSessionsByDayUseCase createSessionsExceptionUseCase() {
        return new LoadSessionsByDayUseCase(new SessionRepository(new TestSessionDataSource())) {
            @NonNull
            @Override
            public Map<ConferenceDay, List<Session>> execute(@NonNull SessionFilters filters) {
                throw new Exception("Testing exception");
            }
        };
    }

    @NonNull
    private LoadAgendaUseCase createAgendaUseCase(List<Block> agenda) {
        return new LoadAgendaUseCase(new AgendaRepository(new TestAgendaDataSource())) {
            @NonNull
            @Override
            public List<Block> execute(@NonNull Unit parameters) {
                return agenda;
            }
        };
    }

    @NonNull
    private LoadTagsByCategoryUseCase createTagsUseCase(List<Tag> tags) {
        return new LoadTagsByCategoryUseCase(new TagRepository(new TestTagDataSource())) {
            @NonNull
            @Override
            public List<Tag> execute(@NonNull Unit parameters) {
                return tags;
            }
        };
    }

    @NonNull
    private LoadTagsByCategoryUseCase createTagsExceptionUseCase() {
        return new LoadTagsByCategoryUseCase(new TagRepository(new TestTagDataSource())) {
            @NonNull
            @Override
            public List<Tag> execute(@NonNull Unit parameters) {
                throw new Exception("Testing exception");
            }
        };
    }
}