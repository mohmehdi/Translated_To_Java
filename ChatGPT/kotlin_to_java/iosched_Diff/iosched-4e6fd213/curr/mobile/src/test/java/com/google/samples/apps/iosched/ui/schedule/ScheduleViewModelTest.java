package com.google.samples.apps.iosched.ui.schedule;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
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
import com.nhaarman.mockito_kotlin.doReturn;
import com.nhaarman.mockito_kotlin.mock;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Test
    public void testDataIsLoaded_ObservablesUpdated() {
        LoadUserSessionsByDayUseCase loadSessionsUseCase = new LoadUserSessionsByDayUseCase(
                new DefaultSessionAndUserEventRepository(
                        new TestUserEventDataSource(), new DefaultSessionRepository(TestDataRepository)
                )
        );
        LoadEventFiltersUseCase loadTagsUseCase = new LoadEventFiltersUseCase(new TagRepository(TestDataRepository));
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();

        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInDelegate
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever();

        for (int index = 0; index < TestData.TestConferenceDays.size(); index++) {
            assertEquals(TestData.userSessionMap.get(TestData.TestConferenceDays.get(index)),
                    LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(index)).getList());
        }
        assertFalse(LiveDataTestUtil.getValue(viewModel.isLoading()));

        List<EventFilter> loadedFilters = LiveDataTestUtil.getValue(viewModel.getEventFilters());
        assertTrue(loadedFilters.containsAll(MobileTestData.tagFiltersList));
    }

    @Test
    public void testDataIsLoaded_Fails() {
        ScheduleViewModel viewModel = createScheduleViewModel();
        SnackbarMessage errorMsg = LiveDataTestUtil.getValue(viewModel.getErrorMessage());
        assertTrue(errorMsg.peekContent() != null && errorMsg.peekContent().isNotEmpty());
    }

    @Test
    public void testStarEvent() {
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(snackbarMessageManager);

        viewModel.onStarClicked(TestData.userSession0);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        SnackbarMessage message = nextMessageEvent != null ? nextMessageEvent.getContentIfNotHandled() : null;
        assertEquals(R.string.event_starred, message != null ? message.getMessageId() : 0);
        assertEquals(R.string.dont_show, message != null ? message.getActionId() : 0);
    }

    @Test
    public void testUnstarEvent() {
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(snackbarMessageManager);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        SnackbarMessage message = nextMessageEvent != null ? nextMessageEvent.getContentIfNotHandled() : null;
        assertEquals(R.string.event_unstarred, message != null ? message.getMessageId() : 0);
        assertEquals(R.string.dont_show, message != null ? message.getActionId() : 0);
    }

    @Test
    public void testStar_notLoggedInUser() {
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        signInDelegate.injectIsSignedIn(false);

        ScheduleViewModel viewModel = createScheduleViewModel(signInDelegate);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> starEvent = LiveDataTestUtil.getValue(viewModel.getSnackBarMessage());

        assertThat(starEvent != null ? starEvent.getContentIfNotHandled().getMessageId() : 0,
                Is.is(Is.is(Is.is(IsNot.not(equalTo(R.string.reservation_request_succeeded))))));

        Event<Void> signInEvent = LiveDataTestUtil.getValue(viewModel.getNavigateToSignInDialogAction());
        assertNotNull(signInEvent != null ? signInEvent.getContentIfNotHandled() : null);
    }

    @Test
    public void reservationReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(source);
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase,
                signInDelegate,
                snackbarMessageManager
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever();

        viewModel.getSnackBarMessage().observeForever(null);

        UserEventsResult oldValue = LiveDataTestUtil.getValue(userEventsResult);
        UserEventsResult newValue = oldValue != null ? oldValue.copy(
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_RESERVATIONS)
        ) : null;

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> reservationMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        assertThat(reservationMessage != null ? reservationMessage.getContentIfNotHandled().getMessageId() : 0,
                Is.is(R.string.reservation_new));
    }

    @Test
    public void waitlistReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(source);
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase,
                signInDelegate,
                snackbarMessageManager
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever();

        viewModel.getSnackBarMessage().observeForever(null);

        UserEventsResult oldValue = LiveDataTestUtil.getValue(userEventsResult);
        UserEventsResult newValue = oldValue != null ? oldValue.copy(
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_WAITLIST)
        ) : null;

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> waitlistMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());

        assertThat(waitlistMessage != null ? waitlistMessage.getContentIfNotHandled().getMessageId() : 0,
                Is.is(R.string.waitlist_new));
    }

    @Test
    public void noLoggedInUser_showsReservationButton() {
        AuthenticatedUserInfoBasic noFirebaseUser = null;

        FakeObserveUserAuthStateUseCase observableFirebaseUserUseCase =
                new FakeObserveUserAuthStateUseCase(Result.Success(noFirebaseUser), Result.Success(false));
        FirebaseSignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase,
                mock(TopicSubscriber.class)
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        assertTrue(LiveDataTestUtil.getValue(viewModel.getShowReservations()));
    }

    @Test
    public void loggedInUser_registered_showsReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        when(mockUser.isSignedIn()).thenReturn(true);

        FakeObserveUserAuthStateUseCase observableFirebaseUserUseCase =
                new FakeObserveUserAuthStateUseCase(Result.Success(mockUser), Result.Success(true));
        FirebaseSignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase,
                mock(TopicSubscriber.class)
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        assertTrue(LiveDataTestUtil.getValue(viewModel.getShowReservations()));
    }

    @Test
    public void loggedInUser_notRegistered_hidesReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        when(mockUser.isSignedIn()).thenReturn(true);

        FakeObserveUserAuthStateUseCase observableFirebaseUserUseCase =
                new FakeObserveUserAuthStateUseCase(Result.Success(mockUser), Result.Success(false));
        FirebaseSignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase,
                mock(TopicSubscriber.class)
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        assertFalse(LiveDataTestUtil.getValue(viewModel.getShowReservations()));
    }

    @Test
    public void scheduleHints_notShown_on_launch() {
        ScheduleViewModel viewModel = createScheduleViewModel();

        Event<Boolean> event = LiveDataTestUtil.getValue(viewModel.getScheduleUiHintsShown());
        assertEquals(Boolean.FALSE, event != null ? event.getContentIfNotHandled() : null);
    }

    @Test
    public void swipeRefresh_refreshesRemoteConfData() {
        ConferenceDataSource remoteDataSource = mock(ConferenceDataSource.class);
        ScheduleViewModel viewModel = createScheduleViewModel(
                new RefreshConferenceDataUseCase(
                        new ConferenceDataRepository(remoteDataSource, TestDataSource)
                )
        );

        viewModel.onSwipeRefresh();

        verify(remoteDataSource).getRemoteConferenceData();

        assertEquals(Boolean.FALSE, LiveDataTestUtil.getValue(viewModel.getSwipeRefreshing()));
    }

    @Test
    public void newDataFromConfRepo_scheduleUpdated() {
        ConferenceDataRepository repo = new ConferenceDataRepository(
                new TestConfDataSourceSession0(),
                new BootstrapDataSourceSession3()
        );

        LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase = createTestLoadUserSessionsByDayUseCase(repo);
        ObserveConferenceDataUseCase observeConferenceDataUseCase = new ObserveConferenceDataUseCase(repo);
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadUserSessionsByDayUseCase,
                observeConferenceDataUseCase
        );

        viewModel.getSessionTimeDataForDay(0).observeForever();

        repo.refreshCacheWithRemoteConferenceData();

        LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0));

        assertThat(
                LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0)).getList().get(0).getSession(),
                Is.is(TestData.session0)
        );
    }

    private ScheduleViewModel createScheduleViewModel(
            LoadUserSessionsByDayUseCase loadSessionsUseCase,
            LoadEventFiltersUseCase loadTagsUseCase,
            SignInViewModelDelegate signInViewModelDelegate) {
        return new ScheduleViewModel(
                loadSessionsUseCase,
                new LoadAgendaUseCase(new AgendaRepository()),
                loadTagsUseCase,
                signInViewModelDelegate,
                new FakeStarEventUseCase(),
                new FakeScheduleUiHintsShownUseCase(),
                mock(TopicSubscriber.class),
                new SnackbarMessageManager(new FakePreferenceStorage()),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                new RefreshConferenceDataUseCase(TestDataRepository),
                new ObserveConferenceDataUseCase(TestDataRepository),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );
    }


    private LoadUserSessionsByDayUseCase createTestLoadUserSessionsByDayUseCase(
            UserEventDataSource userEventDataSource,
            ConferenceDataRepository conferenceDataRepo) {
        DefaultSessionRepository sessionRepository = new DefaultSessionRepository(conferenceDataRepo);
        DefaultSessionAndUserEventRepository userEventRepository =
                new DefaultSessionAndUserEventRepository(userEventDataSource, sessionRepository);

        return new LoadUserSessionsByDayUseCase(userEventRepository);
    }

    private LoadEventFiltersUseCase createEventFiltersExceptionUseCase() {
        return new LoadEventFiltersUseCase(new TagRepository(TestDataRepository)) {
            @Override
            public List<EventFilter> execute(UserSessionMatcher parameters) {
                throw new RuntimeException("Testing exception");
            }
        };
    }

    private LoadAgendaUseCase createAgendaExceptionUseCase() {
        return new LoadAgendaUseCase(new AgendaRepository()) {
            @Override
            public List<Block> execute(Unit parameters) {
                throw new RuntimeException("Testing exception");
            }
        };
    }

    private SignInViewModelDelegate createSignInViewModelDelegate() {
        return new FakeSignInViewModelDelegate();
    }

    private StarEventUseCase createStarEventUseCase() {
        return new FakeStarEventUseCase();
    }

    private GetTimeZoneUseCase createGetTimeZoneUseCase() {
        return new GetTimeZoneUseCase(new FakePreferenceStorage());
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
        MutableLiveData<Result<Boolean?>?> resultLiveData = new MutableLiveData<>();
        resultLiveData.setValue(isRegistered);
        return resultLiveData;
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
        MutableLiveData<Result<AuthenticatedUserInfoBasic?>> resultLiveData = new MutableLiveData<>();
        resultLiveData.setValue(user);
        return resultLiveData;
    }

    @Override
    public void clearListener() {
    }
}

class FakeObserveUserAuthStateUseCase extends ObserveUserAuthStateUseCase {

    public FakeObserveUserAuthStateUseCase(Result<AuthenticatedUserInfoBasic?> user, Result<Boolean?> isRegistered) {
        super(
                new TestRegisteredUserDataSource(isRegistered),
                new TestAuthStateUserDataSource(user),
                mock(TopicSubscriber.class)
        );
    }
}

class FakeScheduleUiHintsShownUseCase extends ScheduleUiHintsShownUseCase {

    public FakeScheduleUiHintsShownUseCase() {
        super(new FakePreferenceStorage());
    }
}

class TestConfDataSourceSession0 implements ConferenceDataSource {

    private final ConferenceData conferenceData;

    public TestConfDataSourceSession0() {
        conferenceData = new ConferenceData(
                Collections.singletonList(TestData.session0),
                Arrays.asList(TestData.androidTag, TestData.webTag),
                Collections.singletonList(TestData.speaker1),
                Collections.emptyList(),
                42
        );
    }

    @Override
    public ConferenceData getRemoteConferenceData() {
        return conferenceData;
    }

    @Override
    public ConferenceData getOfflineConferenceData() {
        return conferenceData;
    }
}

class BootstrapDataSourceSession3 implements ConferenceDataSource {

    @Override
    public ConferenceData getRemoteConferenceData() {
        throw new NotImplementedError();
    }

    @Override
    public ConferenceData getOfflineConferenceData() {
        return new ConferenceData(
                Collections.singletonList(TestData.session3),
                Arrays.asList(TestData.androidTag, TestData.webTag),
                Collections.singletonList(TestData.speaker1),
                Collections.emptyList(),
                42
        );
    }
}
