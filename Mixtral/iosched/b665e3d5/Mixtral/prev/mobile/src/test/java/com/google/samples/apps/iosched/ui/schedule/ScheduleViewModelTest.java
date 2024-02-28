

package com.google.samples.apps.iosched.ui.schedule;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.androidtest.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.model.ConferenceData;
import com.google.samples.apps.iosched.model.MobileTestData;
import com.google.samples.apps.iosched.model.TestDataRepository;
import com.google.samples.apps.iosched.model.TestDataSource;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.data.ConferenceDataRepository;
import com.google.samples.apps.iosched.shared.data.ConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository;
import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaRepository;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfoBasic;
import com.google.samples.apps.iosched.shared.data.signin.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.signin.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.data.tag.TagRepository;
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.data.userevent.UserEventDataSource;
import com.google.samples.apps.iosched.shared.data.userevent.UserEventMessage;
import com.google.samples.apps.iosched.shared.data.userevent.UserEventMessageChangeType;
import com.google.samples.apps.iosched.shared.data.userevent.UserEventsResult;
import com.google.samples.apps.iosched.shared.domain.RefreshConferenceDataUseCase;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.LoadSelectedFiltersUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.SaveSelectedFiltersUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.ScheduleUiHintsShownUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.ObserveConferenceDataUseCase;
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase;
import com.google.samples.apps.iosched.shared.domain.users.StarEventUseCase;
import com.google.samples.apps.iosched.shared.fcm.TopicSubscriber;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.test.data.TestData;
import com.google.samples.apps.iosched.test.util.SyncTaskExecutorRule;
import com.google.samples.apps.iosched.test.util.fakes.FakeAnalyticsHelper;
import com.google.samples.apps.iosched.test.util.fakes.FakePreferenceStorage;
import com.google.samples.apps.iosched.test.util.fakes.FakeSignInViewModelDelegate;
import com.google.samples.apps.iosched.test.util.fakes.FakeStarEventUseCase;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.day.TestUserEventDataSource;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase;
import com.google.samples.apps.iosched.ui.signin.FirebaseSignInViewModelDelegate;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.nhaarman.mockito_kotlin.mock;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Mock
    private LoadUserSessionsByDayUseCase loadSessionsUseCase;

    @Mock
    private LoadEventFiltersUseCase loadTagsUseCase;

    private FakeSignInViewModelDelegate signInDelegate;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        signInDelegate = new FakeSignInViewModelDelegate();
    }

    @Test
    public void testDataIsLoaded_ObservablesUpdated() throws ExecutionException, InterruptedException {
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase, loadTagsUseCase, signInDelegate);

        signInDelegate.loadUser("test");

        LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0)).observeForever(new Observer<List<Block>>() {

        });

        List<Block> expectedBlocks = TestData.TestConferenceDays.stream()
                .map(day -> TestData.userSessionMap.get(day))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        for (int i = 0; i < TestData.TestConferenceDays.size(); i++) {
            Assert.assertEquals(expectedBlocks, LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(i)).get());
        }

        Assert.assertFalse(LiveDataTestUtil.getValue(viewModel.isLoading).get());

        List<EventFilter> loadedFilters = LiveDataTestUtil.getValue(viewModel.eventFilters).get();
        Assert.assertTrue(loadedFilters.containsAll(MobileTestData.tagFiltersList));
    }

    @Test
    public void testDataIsLoaded_Fails() {
        ScheduleViewModel viewModel = createScheduleViewModel();
        Assert.assertTrue(LiveDataTestUtil.getValue(viewModel.errorMessage).get().isPresent());
    }

    @Test
    public void testStarEvent() {
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(snackbarMessageManager);

        viewModel.onStarClicked(TestData.userSession0);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().messageId, CoreMatchers.equalTo(R.string.event_starred));
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().actionId, CoreMatchers.equalTo(R.string.dont_show));
    }

    @Test
    public void testUnstarEvent() {
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(snackbarMessageManager);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().messageId, CoreMatchers.equalTo(R.string.event_unstarred));
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().actionId, CoreMatchers.equalTo(R.string.dont_show));
    }

    @Test
    public void testStar_notLoggedInUser() {
        signInDelegate.injectIsSignedIn = false;

        ScheduleViewModel viewModel = createScheduleViewModel(signInDelegate);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> starEvent = LiveDataTestUtil.getValue(viewModel.snackBarMessage);
        Assert.assertThat(starEvent.getContentIfNotHandled().messageId, not(CoreMatchers.equalTo(R.string.reservation_request_succeeded)));

        Event<String> signInEvent = LiveDataTestUtil.getValue(viewModel.navigateToSignInDialogAction);
        Assert.assertNotNull(signInEvent.getContentIfNotHandled());
    }

    @Test
    public void reservationReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(source);
        signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase, signInDelegate, snackbarMessageManager);

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<Block>>() {

        });

        viewModel.snackBarMessage.observeForever(new Observer<Event<SnackbarMessage>>() {

        });

        UserEventsResult oldValue = userEventsResult.getValue();
        UserEventsResult newValue = new UserEventsResult(
                oldValue.userEvents,
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_RESERVATIONS)
        );

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> reservationMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        Assert.assertThat(reservationMessage.getContentIfNotHandled().messageId, CoreMatchers.equalTo(R.string.reservation_new));
    }

    @Test
    public void waitlistReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(source);
        signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase, signInDelegate, snackbarMessageManager);

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<Block>>() {

        });

        viewModel.snackBarMessage.observeForever(new Observer<Event<SnackbarMessage>>() {

        });

        UserEventsResult oldValue = userEventsResult.getValue();
        UserEventsResult newValue = new UserEventsResult(
                oldValue.userEvents,
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_WAITLIST)
        );

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> waitlistMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());

        Assert.assertThat(waitlistMessage.getContentIfNotHandled().messageId, CoreMatchers.equalTo(R.string.waitlist_new));
    }

    @Test
    public void noLoggedInUser_showsReservationButton() {
        AuthenticatedUserInfoBasic noFirebaseUser = null;

        ObserveUserAuthStateUseCase observableFirebaseUserUseCase =
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(noFirebaseUser),
                        new Result.Success<>(false)
                );
        SignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase,
                new Object()
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        Assert.assertEquals(true, LiveDataTestUtil.getValue(viewModel.showReservations).get());
    }

    @Test
    public void loggedInUser_registered_showsReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        Mockito.when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observableFirebaseUserUseCase =
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(mockUser),
                        new Result.Success<>(true)
                );
        SignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase,
                new Object()
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        Assert.assertEquals(true, LiveDataTestUtil.getValue(viewModel.showReservations).get());
    }

    @Test
    public void loggedInUser_notRegistered_hidesReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        Mockito.when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observableFirebaseUserUseCase =
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(mockUser),
                        new Result.Success<>(false)
                );
        SignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase,
                new Object()
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        Assert.assertEquals(false, LiveDataTestUtil.getValue(viewModel.showReservations).get());
    }

    @Test
    public void scheduleHints_notShown_on_launch() {
        ScheduleViewModel viewModel = createScheduleViewModel();

        Assert.assertEquals(false, LiveDataTestUtil.getValue(viewModel.scheduleUiHintsShown).get());
    }

    @Test
    public void swipeRefresh_refreshesRemoteConfData() {
        ConferenceDataSource remoteDataSource = mock(ConferenceDataSource.class);
        ScheduleViewModel viewModel = createScheduleViewModel(
                refreshConferenceDataUseCase = new RefreshConferenceDataUseCase(
                        new ConferenceDataRepository(
                                remoteDataSource,
                                new TestDataSource()
                        )
                )
        );

        viewModel.onSwipeRefresh();

        Mockito.verify(remoteDataSource).getRemoteConferenceData();

        Assert.assertEquals(false, LiveDataTestUtil.getValue(viewModel.swipeRefreshing).get());
    }

    @Test
    public void newDataFromConfRepo_scheduleUpdated() {
        ConferenceDataRepository repo = new ConferenceDataRepository(
                new TestConfDataSourceSession0(),
                new BootstrapDataSourceSession3()
        );

        LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase = createTestLoadUserSessionsByDayUseCase(
                conferenceDataRepo = repo
        );
        ObserveConferenceDataUseCase observeConferenceDataUseCase = new ObserveConferenceDataUseCase(repo);
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase = loadUserSessionsByDayUseCase,
                observeConferenceDataUseCase = observeConferenceDataUseCase
        );

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<Block>>() {

        });

        repo.refreshCacheWithRemoteConferenceData();

        List<Block> newValue = LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0));

        Assert.assertThat(newValue.get(0).getSession(), CoreMatchers.equalTo(TestData.session0));
    }

        @NonNull
    public ScheduleViewModel createScheduleViewModel(
            LoadUserSessionsByDayUseCase loadSessionsUseCase,
            LoadAgendaUseCase loadAgendaUseCase,
            LoadEventFiltersUseCase loadTagsUseCase,
            SignInViewModelDelegate signInViewModelDelegate,
            StarEventUseCase starEventUseCase,
            SnackbarMessageManager snackbarMessageManager,
            ScheduleUiHintsShownUseCase scheduleUiHintsShownUseCase,
            GetTimeZoneUseCase getTimeZoneUseCase,
            TopicSubscriber topicSubscriber,
            RefreshConferenceDataUseCase refreshConferenceDataUseCase,
            ObserveConferenceDataUseCase observeConferenceDataUseCase,
            LoadSelectedFiltersUseCase loadSelectedFiltersUseCase,
            SaveSelectedFiltersUseCase saveSelectedFiltersUseCase,
            AnalyticsHelper analyticsHelper) {
        return new ScheduleViewModel(
                loadSessionsUseCase,
                loadAgendaUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                starEventUseCase,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                snackbarMessageManager,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper);
    }

    private LoadUserSessionsByDayUseCase createTestLoadUserSessionsByDayUseCase(
            UserEventDataSource userEventDataSource,
            ConferenceDataRepository conferenceDataRepo) {
        DefaultSessionRepository sessionRepository = new DefaultSessionRepository(conferenceDataRepo);
        DefaultSessionAndUserEventRepository userEventRepository = new DefaultSessionAndUserEventRepository(
                userEventDataSource, sessionRepository
        );

        return new LoadUserSessionsByDayUseCase(userEventRepository);
    }

    private LoadEventFiltersUseCase createEventFiltersExceptionUseCase() {
      return new LoadEventFiltersUseCase(new TagRepository(new TestDataRepository())) {
        @Override
        public List execute(UserSessionMatcher parameters) {
          throw new RuntimeException("Testing exception");
        }
      };
    }

    private LoadAgendaUseCase createAgendaExceptionUseCase() {
      return new LoadAgendaUseCase(new AgendaRepository()) {
        @Override
        public List execute(Unit parameters) {
          throw new Exception("Testing exception");
        }
      };
    }

    private FakeSignInViewModelDelegate createSignInViewModelDelegate() {
      return new FakeSignInViewModelDelegate();
    }

    private FakeStarEventUseCase createStarEventUseCase() {
      return new FakeStarEventUseCase();
    }

    private GetTimeZoneUseCase createGetTimeZoneUseCase() {
      return new GetTimeZoneUseCase(new FakePreferenceStorage());
    }



    private static class FakeObserveUserAuthStateUseCase
            extends ObserveUserAuthStateUseCase {
        public FakeObserveUserAuthStateUseCase(
                Result<AuthenticatedUserInfoBasic?> user,
                Result<Boolean?> isRegistered) {
            super(new TestRegisteredUserDataSource(isRegistered),
                    new TestAuthStateUserDataSource(user),
                    new Object());
        }
    }

    private static class FakeScheduleUiHintsShownUseCase
            extends ScheduleUiHintsShownUseCase {
        public FakeScheduleUiHintsShownUseCase(
                PreferenceStorage preferenceStorage) {
            super(preferenceStorage);
        }
    }

    private static class TestConfDataSourceSession0 implements ConferenceDataSource {
        @Override
        public ConferenceData getRemoteConferenceData() {
            return conferenceData;
        }

        @Override
        public ConferenceData getOfflineConferenceData() {
            return conferenceData;
        }

        private final ConferenceData conferenceData = new ConferenceData(
                sessions = List.of(TestData.session0),
                tags = List.of(TestData.androidTag, TestData.webTag),
                speakers = List.of(TestData.speaker1),
                rooms = Collections.emptyList(),
                version = 42
        );
    }

    private static class BootstrapDataSourceSession3 implements ConferenceDataSource {
        @Override
        public ConferenceData getRemoteConferenceData() {
            throw new NotImplementedError();
        }

        @Override
        public ConferenceData getOfflineConferenceData() {
            return new ConferenceData(
                    sessions = List.of(TestData.session3),
                    tags = List.of(TestData.androidTag, TestData.webTag),
                    speakers = List.of(TestData.speaker1),
                    rooms = Collections.emptyList(),
                    version = 42
            );
        }
    }
}

class TestRegisteredUserDataSource implements RegisteredUserDataSource {
    private final Result<Boolean?> isRegistered;

    public TestRegisteredUserDataSource(Result<Boolean?> isRegistered) {
        this.isRegistered = isRegistered;
    }

    @Override
    public void listenToUserChanges(String userId) {
    }

    @Override
    public LiveData<Result<Boolean?>?> observeResult() {
        MutableLiveData<Result<Boolean?>?> liveData = new MutableLiveData<>();
        liveData.setValue(isRegistered);
        return liveData;
    }

    @Override
    public void setAnonymousValue() {
    }
}

class TestAuthStateUserDataSource implements AuthStateUserDataSource {
    private final Result<AuthenticatedUserInfoBasic?> user;

    public TestAuthStateUserDataSource(Result<AuthenticatedUserInfoBasic?> user) {
        this.user = user;
    }

    @Override
    public void startListening() {
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic?>> getBasicUserInfo() {
        MutableLiveData<Result<AuthenticatedUserInfoBasic?>> liveData = new MutableLiveData<>();
        liveData.setValue(user);
        return liveData;
    }

    @Override
    public void clearListener() {
    }
}