

package com.google.samples.apps.iosched.ui.schedule;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.androidtest.util.LiveDataTestUtil;
import com.google.samples.apps.iosched.model.ConferenceData;
import com.google.samples.apps.iosched.model.MobileTestData;
import com.google.samples.apps.iosched.model.TestDataRepository;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.data.ConferenceDataRepository;
import com.google.samples.apps.iosched.shared.data.ConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository;
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

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    private ScheduleViewModel viewModel;

    @Mock
    private ObserveUserAuthStateUseCase observeUserAuthStateUseCase;

    @Mock
    private LoadUserSessionsByDayUseCase loadSessionsUseCase;

    @Mock
    private LoadEventFiltersUseCase loadTagsUseCase;

    @Mock
    private SignInViewModelDelegate signInViewModelDelegate;

    @Mock
    private StarEventUseCase starEventUseCase;

    @Mock
    private SnackbarMessageManager snackbarMessageManager;

    @Mock
    private ScheduleUiHintsShownUseCase scheduleUiHintsShownUseCase;

    @Mock
    private GetTimeZoneUseCase getTimeZoneUseCase;

    @Mock
    private TopicSubscriber topicSubscriber;

    @Mock
    private RefreshConferenceDataUseCase refreshConferenceDataUseCase;

    @Mock
    private ObserveConferenceDataUseCase observeConferenceDataUseCase;

    @Mock
    private LoadSelectedFiltersUseCase loadSelectedFiltersUseCase;

    @Mock
    private SaveSelectedFiltersUseCase saveSelectedFiltersUseCase;

    @Mock
    private AnalyticsHelper analyticsHelper;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        viewModel = new ScheduleViewModel(
                loadUserSessionsByDayUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );
    }

    @Test
    public void testDataIsLoaded_ObservablesUpdated() throws ExecutionException, InterruptedException {
        DefaultSessionAndUserEventRepository defaultSessionAndUserEventRepository =
                new DefaultSessionAndUserEventRepository(
                        new TestUserEventDataSource(),
                        new DefaultSessionRepository(new TestDataRepository())
                );
        LoadUserSessionsByDayUseCase loadSessionsUseCase =
                new LoadUserSessionsByDayUseCase(defaultSessionAndUserEventRepository);
        LoadEventFiltersUseCase loadTagsUseCase = new LoadEventFiltersUseCase(new TagRepository(new TestDataRepository()));
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();

        Mockito.when(signInViewModelDelegate.loadUser("test"))
                .thenReturn(new AuthenticatedUserInfoBasic("test", "test@example.com", true));

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<UserSessionWithTimeData>>() {

        });

        LiveDataTestUtil.getValue(viewModel.isLoading);

        TestData.TestConferenceDays.forEachIndexed(
                (index, day) -> Assert.assertEquals(
                        TestData.userSessionMap.get(day),
                        LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(index))
                                .getValue().getList()
                )
        );
        Assert.assertFalse(LiveDataTestUtil.getValue(viewModel.isLoading));

        List<EventFilter> loadedFilters = LiveDataTestUtil.getValue(viewModel.eventFilters);
        Assert.assertTrue(loadedFilters.containsAll(MobileTestData.tagFiltersList));
    }

    @Test
    public void testDataIsLoaded_Fails() {
        Assert.assertTrue(LiveDataTestUtil.getValue(viewModel.errorMessage).getPeekContent().isNotEmpty());
    }

    @Test
    public void testStarEvent() {
        MutableLiveData<Event<SnackbarMessage>> snackBarMessage = new MutableLiveData<>();
        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                new FakeStarEventUseCase(snackBarMessage),
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        viewModel.onStarClicked(TestData.userSession0);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().getMessageId(), is(equalTo(R.string.event_starred)));
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().getActionId(), is(equalTo(R.string.dont_show)));
    }

    @Test
    public void testUnstarEvent() {
        MutableLiveData<Event<SnackbarMessage>> snackBarMessage = new MutableLiveData<>();
        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                new FakeStarEventUseCase(snackBarMessage),
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().getMessageId(), is(equalTo(R.string.event_unstarred)));
        Assert.assertThat(nextMessageEvent.getContentIfNotHandled().getActionId(), is(equalTo(R.string.dont_show)));
    }

    @Test
    public void testStar_notLoggedInUser() {
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        signInDelegate.injectIsSignedIn = false;

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInDelegate,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> starEvent = LiveDataTestUtil.getValue(viewModel.snackBarMessage);
        Assert.assertThat(
                starEvent.getContentIfNotHandled().getMessageId(),
                is(not(equalTo(R.string.reservation_request_succeeded)))
        );

        Event<Void> signInEvent = LiveDataTestUtil.getValue(viewModel.navigateToSignInDialogAction);
        Assert.assertNotNull(signInEvent.getContentIfNotHandled());
    }

    @Test
    public void reservationReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase =
                createTestLoadUserSessionsByDayUseCase(source);
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<UserSessionWithTimeData>>() {

        });

        viewModel.snackBarMessage.observeForever(new Observer<Event<SnackbarMessage>>() {

        });

        UserEventsResult oldValue = userEventsResult.getValue();
        UserEventsResult newValue = new UserEventsResult(
                oldValue.getUserEvents(),
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_RESERVATIONS)
        );

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> reservationMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());
        Assert.assertThat(
                reservationMessage.getContentIfNotHandled().getMessageId(),
                is(equalTo(R.string.reservation_new))
        );
    }

    @Test
    public void waitlistReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        TestUserEventDataSource source = new TestUserEventDataSource(userEventsResult);
        LoadUserSessionsByDayUseCase loadSessionsUseCase =
                createTestLoadUserSessionsByDayUseCase(source);
        FakeSignInViewModelDelegate signInDelegate = new FakeSignInViewModelDelegate();
        SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(new FakePreferenceStorage());

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        signInDelegate.loadUser("test");

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<UserSessionWithTimeData>>() {
 
        });

        viewModel.snackBarMessage.observeForever(new Observer<Event<SnackbarMessage>>() {

        });

        UserEventsResult oldValue = userEventsResult.getValue();
        UserEventsResult newValue = new UserEventsResult(
                oldValue.getUserEvents(),
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_WAITLIST)
        );

        userEventsResult.postValue(newValue);

        Event<SnackbarMessage> waitlistMessage = LiveDataTestUtil.getValue(snackbarMessageManager.observeNextMessage());

        Assert.assertThat(
                waitlistMessage.getContentIfNotHandled().getMessageId(),
                is(equalTo(R.string.waitlist_new))
        );
    }

    @Test
    public void noLoggedInUser_showsReservationButton() {
        AuthenticatedUserInfoBasic noFirebaseUser = null;

        ObserveUserAuthStateUseCase observeUserAuthStateUseCase =
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(noFirebaseUser),
                        new Result.Success<>(false)
                );
        FirebaseSignInViewModelDelegate signInViewModelComponent =
                new FirebaseSignInViewModelDelegate(
                        observeUserAuthStateUseCase,
                        new Observer<AuthenticatedUserInfoBasic>() {

                        }
                );

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelComponent,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        Assert.assertEquals(true, LiveDataTestUtil.getValue(viewModel.showReservations));
    }

    @Test
    public void loggedInUser_registered_showsReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        Mockito.when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observeUserAuthStateUseCase =
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(mockUser),
                        new Result.Success<>(true)
                );
        FirebaseSignInViewModelDelegate signInViewModelComponent =
                new FirebaseSignInViewModelDelegate(
                        observeUserAuthStateUseCase,
                        new Observer<AuthenticatedUserInfoBasic>() {

                        }
                );

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelComponent,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        Assert.assertEquals(true, LiveDataTestUtil.getValue(viewModel.showReservations));
    }

    @Test
    public void loggedInUser_notRegistered_hidesReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        Mockito.when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observeUserAuthStateUseCase =
                new FakeObserveUserAuthStateUseCase(
                        new Result.Success<>(mockUser),
                        new Result.Success<>(false)
                );
        FirebaseSignInViewModelDelegate signInViewModelComponent =
                new FirebaseSignInViewModelDelegate(
                        observeUserAuthStateUseCase,
                        new Observer<AuthenticatedUserInfoBasic>() {

                        }
                );

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelComponent,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        Assert.assertEquals(false, LiveDataTestUtil.getValue(viewModel.showReservations));
    }

    @Test
    public void scheduleHints_notShown_on_launch() {
        Assert.assertEquals(false, LiveDataTestUtil.getValue(viewModel.scheduleUiHintsShown).getContentIfNotHandled());
    }

    @Test
    public void swipeRefresh_refreshesRemoteConfData() {
        ConferenceDataSource remoteDataSource = mock(ConferenceDataSource.class);
        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                new RefreshConferenceDataUseCase(
                        new ConferenceDataRepository(
                                remoteDataSource,
                                new TestDataSource()
                        )
                ),
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        viewModel.onSwipeRefresh();

        Mockito.verify(remoteDataSource).getRemoteConferenceData();

        Assert.assertEquals(false, LiveDataTestUtil.getValue(viewModel.swipeRefreshing));
    }

    @Test
    public void newDataFromConfRepo_scheduleUpdated() {
        ConferenceDataRepository repo =
                new ConferenceDataRepository(
                        new TestConfDataSourceSession0(),
                        new BootstrapDataSourceSession3()
                );

        LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase =
                createTestLoadUserSessionsByDayUseCase(repo);
        ObserveConferenceDataUseCase observeConferenceDataUseCase =
                new ObserveConferenceDataUseCase(repo);
        viewModel = new ScheduleViewModel(
                loadUserSessionsByDayUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                starEventUseCase,
                snackbarMessageManager,
                scheduleUiHintsShownUseCase,
                topicSubscriber,
                getTimeZoneUseCase,
                refreshConferenceDataUseCase,
                observeConferenceDataUseCase,
                loadSelectedFiltersUseCase,
                saveSelectedFiltersUseCase,
                analyticsHelper
        );

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<UserSessionWithTimeData>>() {

        });

        repo.refreshCacheWithRemoteConferenceData();

        List<UserSessionWithTimeData> newValue = LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0));

        Assert.assertThat(
                newValue.get(0).getSession(),
                is(IsEqual.equalTo(TestData.session0))
        );
    }

    public ScheduleViewModel createScheduleViewModel(
  LoadUserSessionsByDayUseCase loadSessionsUseCase,
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
    loadUserSessionsByDayUseCase,
    loadEventFiltersUseCase,
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
    analyticsHelper
  );
}

    private LoadUserSessionsByDayUseCase createTestLoadUserSessionsByDayUseCase(
            UserEventDataSource userEventDataSource,
            ConferenceDataRepository conferenceDataRepo) {
        DefaultSessionRepository sessionRepository = new DefaultSessionRepository(conferenceDataRepo);
        DefaultSessionAndUserEventRepository userEventRepository =
                new DefaultSessionAndUserEventRepository(
                        userEventDataSource, sessionRepository
                );

        return new LoadUserSessionsByDayUseCase(userEventRepository);
    }

    private LoadEventFiltersUseCase createEventFiltersExceptionUseCase() {
        return new LoadEventFiltersUseCase(new TagRepository(new TestDataRepository())) {
            @Override
            protected List<EventFilter> execute(UserSessionMatcher parameters) {
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
        return new GetTimeZoneUseCase(new FakePreferenceStorage()) {

        };
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
    public LiveData<Result<Boolean?>> observeResult() {
        MutableLiveData<Result<Boolean?>> liveData = new MutableLiveData<>();
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

class FakeObserveUserAuthStateUseCase extends ObserveUserAuthStateUseCase {
    public FakeObserveUserAuthStateUseCase(
            Result<AuthenticatedUserInfoBasic?> user,
            Result<Boolean?> isRegistered) {
        super(new TestRegisteredUserDataSource(isRegistered),
                new TestAuthStateUserDataSource(user),
                new Observer<AuthenticatedUserInfoBasic>() {

                }
        );
    }
}

class FakeScheduleUiHintsShownUseCase extends ScheduleUiHintsShownUseCase {
    public FakeScheduleUiHintsShownUseCase(PreferenceStorage preferenceStorage) {
        super(preferenceStorage);
    }
}

class TestConfDataSourceSession0 implements ConferenceDataSource {
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

class BootstrapDataSourceSession3 implements ConferenceDataSource {
    @Override
    public ConferenceData getRemoteConferenceData() {
        throw new UnsupportedOperationException();
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