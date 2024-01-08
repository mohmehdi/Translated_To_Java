import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.model.TestData;
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
import com.google.samples.apps.iosched.ui.schedule.agenda.TestAgendaDataSource;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testDataIsLoaded_ObservablesUpdated() {

        LoadSessionsByDayUseCase loadSessionsUseCase = createSessionsUseCase(TestData.sessionsMap);
        LoadAgendaUseCase loadAgendaUseCase = createAgendaUseCase(TestData.agenda);
        LoadTagsByCategoryUseCase loadTagsUseCase = createTagsUseCase(TestData.tagsList);

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
        LoadSessionsByDayUseCase loadSessionsUseCase = createSessionsExceptionUseCase();
        LoadAgendaUseCase loadAgendaUseCase = createAgendaUseCase(TestData.agenda);
        LoadTagsByCategoryUseCase loadTagsUseCase = createTagsExceptionUseCase();

        ScheduleViewModel viewModel = new ScheduleViewModel(loadSessionsUseCase, loadAgendaUseCase, loadTagsUseCase);

        Assert.assertTrue(!LiveDataTestUtil.getValue(viewModel.errorMessage).isNullOrEmpty());
    }

    private LoadSessionsByDayUseCase createSessionsUseCase(
            final Map<ConferenceDay, List<Session>> sessions) {
        return new LoadSessionsByDayUseCase(new SessionRepository(TestSessionDataSource())) {
            @Override
            public Map<ConferenceDay, List<Session>> execute(SessionFilters filters) {
                return sessions;
            }
        };
    }

    private LoadSessionsByDayUseCase createSessionsExceptionUseCase() {
        return new LoadSessionsByDayUseCase(new SessionRepository(TestSessionDataSource())) {
            @Override
            public Map<ConferenceDay, List<Session>> execute(SessionFilters filters) {
                throw new Exception("Testing exception");
            }
        };
    }

    private LoadAgendaUseCase createAgendaUseCase(final List<Block> agenda) {
        return new LoadAgendaUseCase(new AgendaRepository(TestAgendaDataSource())) {
            @Override
            public List<Block> execute(Void parameters) {
                return agenda;
            }
        };
    }

    private LoadTagsByCategoryUseCase createTagsUseCase(final List<Tag> tags) {
        return new LoadTagsByCategoryUseCase(new TagRepository(TestTagDataSource())) {
            @Override
            public List<Tag> execute(Void parameters) {
                return tags;
            }
        };
    }

    private LoadTagsByCategoryUseCase createTagsExceptionUseCase() {
        return new LoadTagsByCategoryUseCase(new TagRepository(TestTagDataSource())) {
            @Override
            public List<Tag> execute(Void parameters) {
                throw new Exception("Testing exception");
            }
        };
    }
}