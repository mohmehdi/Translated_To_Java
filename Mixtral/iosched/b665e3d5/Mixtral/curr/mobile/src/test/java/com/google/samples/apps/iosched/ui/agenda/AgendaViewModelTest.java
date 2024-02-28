

package com.google.samples.apps.iosched.ui.agenda;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.samples.apps.iosched.androidtest.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.data.agenda.AgendaRepository;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase;
import com.google.samples.apps.iosched.test.data.TestData;
import com.google.samples.apps.iosched.test.util.fakes.FakePreferenceStorage;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.fakes.FakeAgendaRepository;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class AgendaViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    private AgendaViewModel viewModel;

    @Before
    public void setup() {
        viewModel = new AgendaViewModel(
                new LoadAgendaUseCase(new FakeAgendaRepository()),
                new GetTimeZoneUseCase(new FakePreferenceStorage())
        );
    }

    @Test
    public void agendaDataIsLoaded() throws ExecutionException, InterruptedException {
        List<Block> blocks = LiveDataTestUtil.getValue(viewModel.getAgenda());
        MatcherAssert.assertThat("Agenda data is not equal", blocks, Matchers.equalTo(TestData.agenda));
    }

    public static class FakeAgendaRepository extends AgendaRepository {
        @Override
        public List<Block> getAgenda() {
            return TestData.agenda;
        }
    }
}