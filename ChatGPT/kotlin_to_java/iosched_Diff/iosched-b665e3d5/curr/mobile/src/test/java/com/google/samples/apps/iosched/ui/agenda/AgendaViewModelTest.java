package com.google.samples.apps.iosched.ui.agenda;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.androidtest.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.data.agenda.AgendaRepository;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase;
import com.google.samples.apps.iosched.test.data.TestData;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.fakes.FakePreferenceStorage;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;

public class AgendaViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void agendaDataIsLoaded() {
        AgendaViewModel viewModel = new AgendaViewModel(
                new LoadAgendaUseCase(new FakeAgendaRepository()),
                new GetTimeZoneUseCase(new FakePreferenceStorage())
        );

        List<Block> blocks = LiveDataTestUtil.getValue(viewModel.getAgenda());
        MatcherAssert.assertThat(blocks, equalTo(TestData.agenda));
    }

    public static class FakeAgendaRepository implements AgendaRepository {
        @Override
        public List<Block> getAgenda() {
            return TestData.agenda;
        }
    }
}