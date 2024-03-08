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
                new DefaultSessionAndUserEventRepository(new TestUserEventDataSource(), new DefaultSessionRepository(TestDataRepository))
        );
        LoadEventFiltersUseCase loadTagsUseCase = new LoadEventFiltersUseCase(new TagRepository(TestDataRepository));
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();

        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase, loadTagsUseCase, signInDelegate
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(null);

        for (int index = 0; index < TestData.TestConferenceDays.size(); index++) {
            assertEquals(TestData.userSessionMap.get(TestData.TestConferenceDays.get(index)),
                    LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(index)).list);
        }
        assertFalse(LiveDataTestUtil.getValue(viewModel.isLoading));
        assertTrue(LiveDataTestUtil.getValue(viewModel.eventFilters).containsAll(MobileTestData.tagFiltersList));
    }

    @Test
    public void testDataIsLoaded_Fails() {

        ScheduleViewModel viewModel = createScheduleViewModel();
        assertTrue(LiveDataTestUtil.getValue(viewModel.errorMessage).peekContent().isNotEmpty());
    }

    @Test
    public void testStarEvent() {

        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(snackbarMessageManager);

        viewModel.onStarClicked(TestData.userSession0);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        SnackbarMessage message = nextMessageEvent.getContentIfNotHandled();
        assertThat(message.messageId, IsEqual.equalTo(R.string.event_starred));
        assertThat(message.actionId, IsEqual.equalTo(R.string.dont_show));
    }

    @Test
    public void testUnstarEvent() {

        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(snackbarMessageManager);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        SnackbarMessage message = nextMessageEvent.getContentIfNotHandled();
        assertThat(message.messageId, IsEqual.equalTo(R.string.event_unstarred));
        assertThat(message.actionId, IsEqual.equalTo(R.string.dont_show));
    }

    @Test
    public void testStar_notLoggedInUser() {

        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        signInDelegate.injectIsSignedIn = false;

        ScheduleViewModel viewModel = createScheduleViewModel(signInDelegate);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> starEvent = LiveDataTestUtil.getValue(viewModel.snackBarMessage);

        assertThat(starEvent.getContentIfNotHandled().messageId, Is.is(not(equalTo(R.string.reservation_request_succeeded))));

        LiveData<Event<Void>> signInEvent = LiveDataTestUtil.getValue(viewModel.navigateToSignInDialogAction);
        assertNotNull(signInEvent.getContentIfNotHandled());
    }

    @Test
    public void reservationReceived() {

        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(source);
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase, signInDelegate, snackbarMessageManager
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(null);

        viewModel.snackBarMessage.observeForever(null);

        UserEventsResult oldValue = LiveDataTestUtil.getValue(userEventsResult);
        UserEventsResult newValue = oldValue.copy(
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_RESERVATIONS)
        );

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> reservationMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        assertThat(reservationMessage.getContentIfNotHandled().messageId, IsEqual.equalTo(R.string.reservation_new));
    }

    @Test
    public void waitlistReceived() {

        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(source);
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadSessionsUseCase, signInDelegate, snackbarMessageManager
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(null);

        viewModel.snackBarMessage.observeForever(null);

        UserEventsResult oldValue = LiveDataTestUtil.getValue(userEventsResult);
        UserEventsResult newValue = oldValue.copy(
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_WAITLIST)
        );

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> waitlistMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());

        assertThat(waitlistMessage.getContentIfNotHandled().messageId, IsEqual.equalTo(R.string.waitlist_new));
    }

    @Test
    public void noLoggedInUser_showsReservationButton() {

        AuthenticatedUserInfoBasic noFirebaseUser = null;

        FakeObserveUserAuthStateUseCase observableFirebaseUserUseCase = new FakeObserveUserAuthStateUseCase(
                new Result.Success<>(noFirebaseUser), new Result.Success<>(false)
        );
        FirebaseSignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase, mock(Void.class)
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        assertEquals(true, LiveDataTestUtil.getValue(viewModel.showReservations));
    }

    @Test
    public void loggedInUser_registered_showsReservationButton() {

        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        doReturn(true).when(mockUser).isSignedIn();

        FakeObserveUserAuthStateUseCase observableFirebaseUserUseCase = new FakeObserveUserAuthStateUseCase(
                new Result.Success<>(mockUser), new Result.Success<>(true)
        );
        FirebaseSignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase, mock(Void.class)
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        assertEquals(true, LiveDataTestUtil.getValue(viewModel.showReservations));
    }

    @Test
    public void loggedInUser_notRegistered_hidesReservationButton() {

        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        doReturn(true).when(mockUser).isSignedIn();

        FakeObserveUserAuthStateUseCase observableFirebaseUserUseCase = new FakeObserveUserAuthStateUseCase(
                new Result.Success<>(mockUser), new Result.Success<>(false)
        );
        FirebaseSignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
                observableFirebaseUserUseCase, mock(Void.class)
        );

        ScheduleViewModel viewModel = createScheduleViewModel(signInViewModelComponent);

        assertEquals(false, LiveDataTestUtil.getValue(viewModel.showReservations));
    }

    @Test
    public void scheduleHints_notShown_on_launch() {
        ScheduleViewModel viewModel = createScheduleViewModel();

        Event<Boolean> event = LiveDataTestUtil.getValue(viewModel.scheduleUiHintsShown);
        assertEquals(event.getContentIfNotHandled(), false);
    }

    @Test
    public void swipeRefresh_refreshesRemoteConfData() {

        ConferenceDataSource remoteDataSource = mock(ConferenceDataSource.class);
        ScheduleViewModel viewModel = createScheduleViewModel(
                new RefreshConferenceDataUseCase(new ConferenceDataRepository(remoteDataSource, TestDataSource))
        );

        viewModel.onSwipeRefresh();

        verify(remoteDataSource).getRemoteConferenceData();

        assertEquals(false, LiveDataTestUtil.getValue(viewModel.swipeRefreshing));
    }

    @Test
    public void newDataFromConfRepo_scheduleUpdated() {
        ConferenceDataRepository repo = new ConferenceDataRepository(new TestConfDataSourceSession0(), new BootstrapDataSourceSession3());
        LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase = createTestLoadUserSessionsByDayUseCase(repo);
        ObserveConferenceDataUseCase observeConferenceDataUseCase = new ObserveConferenceDataUseCase(repo);
        ScheduleViewModel viewModel = createScheduleViewModel(
                loadUserSessionsByDayUseCase, observeConferenceDataUseCase
        );

        viewModel.getSessionTimeDataForDay(0).observeForever(null);

        repo.refreshCacheWithRemoteConferenceData();

        LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0));

        assertThat(viewModel.getSessionTimeDataForDay(0).list.get(0).session, IsEqual.equalTo(TestData.session0));
    }

    private ScheduleViewModel createScheduleViewModel(
            LoadUserSessionsByDayUseCase loadSessionsUseCase,
            LoadEventFiltersUseCase loadTagsUseCase,
            SignInViewModelDelegate signInViewModelDelegate
    ) {
        return new ScheduleViewModel(loadSessionsUseCase, loadTagsUseCase, signInViewModelDelegate);
    }

    private LoadUserSessionsByDayUseCase createTestLoadUserSessionsByDayUseCase(
            UserEventDataSource userEventDataSource, ConferenceDataRepository conferenceDataRepo
    ) {
        DefaultSessionRepository sessionRepository = new DefaultSessionRepository(conferenceDataRepo);
        DefaultSessionAndUserEventRepository userEventRepository = new DefaultSessionAndUserEventRepository(userEventDataSource, sessionRepository);

        return new LoadUserSessionsByDayUseCase(userEventRepository);
    }

    private LoadEventFiltersUseCase createEventFiltersExceptionUseCase() {
        return new LoadEventFiltersUseCase(new TagRepository(TestDataRepository)) {
            @Override
            public List<EventFilter> execute(UserSessionMatcher parameters) {
                throw new Exception("Testing exception");
            }
        };
    }

    private LoadAgendaUseCase createAgendaExceptionUseCase() {
        return new LoadAgendaUseCase(new AgendaRepository()) {
            @Override
            public List<Block> execute(Void parameters) {
                throw new Exception("Testing exception");
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
        return new GetTimeZoneUseCase(new FakePreferenceStorage()) {
        };
    }
}

public class TestRegisteredUserDataSource implements RegisteredUserDataSource {
    private final Result<Boolean?> isRegistered;

    public TestRegisteredUserDataSource(Result<Boolean?> isRegistered) {
        this.isRegistered = isRegistered;
    }

    @Override
    public void listenToUserChanges(String userId) {
        // Implementation not provided in Kotlin code
    }

    @Override
    public LiveData<Result<Boolean?>> observeResult() {
        return new MutableLiveData<Result<Boolean?>>().apply(input -> input.setValue(isRegistered));
    }

    @Override
    public void setAnonymousValue() {
        // Implementation not provided in Kotlin code
    }
}

public class TestAuthStateUserDataSource implements AuthStateUserDataSource {
    private final Result<AuthenticatedUserInfoBasic?> user;

    public TestAuthStateUserDataSource(Result<AuthenticatedUserInfoBasic?> user) {
        this.user = user;
    }

    @Override
    public void startListening() {
        // Implementation not provided in Kotlin code
    }

    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic?>> getBasicUserInfo() {
        return new MutableLiveData<Result<AuthenticatedUserInfoBasic?>>().apply(input -> input.setValue(user));
    }

    @Override
    public void clearListener() {
        // Implementation not provided in Kotlin code
    }
}

public class FakeObserveUserAuthStateUseCase extends ObserveUserAuthStateUseCase {
    public FakeObserveUserAuthStateUseCase(Result<AuthenticatedUserInfoBasic?> user, Result<Boolean?> isRegistered) {
        super(new TestRegisteredUserDataSource(isRegistered), new TestAuthStateUserDataSource(user), () -> mock());
    }
}

public class FakeScheduleUiHintsShownUseCase extends ScheduleUiHintsShownUseCase {
    public FakeScheduleUiHintsShownUseCase() {
        super(new FakePreferenceStorage());
    }
}

public class TestConfDataSourceSession0 implements ConferenceDataSource {
    private final ConferenceData conferenceData = new ConferenceData(
            listOf(TestData.session0),
            listOf(TestData.androidTag, TestData.webTag),
            listOf(TestData.speaker1),
            emptyList(),
            42
    );

    @Override
    public ConferenceData getRemoteConferenceData() {
        return conferenceData;
    }

    @Override
    public ConferenceData getOfflineConferenceData() {
        return conferenceData;
    }
}

public class BootstrapDataSourceSession3 implements ConferenceDataSource {
    @Override
    public ConferenceData getRemoteConferenceData() {
        throw new NotImplementedError(); // Not used
    }

    @Override
    public ConferenceData getOfflineConferenceData() {
        return new ConferenceData(
                listOf(TestData.session3),
                listOf(TestData.androidTag, TestData.webTag),
                listOf(TestData.speaker1),
                emptyList(),
                42
        );
    }
}
