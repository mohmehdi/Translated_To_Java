
package com.google.samples.apps.iosched.ui.schedule;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.samples.apps.iosched.test.util.fakes.FakeAnalyticsHelper;
import com.google.samples.apps.iosched.test.util.fakes.FakePreferenceStorage;
import com.google.samples.apps.iosched.test.util.fakes.FakeSignInViewModelDelegate;
import com.google.samples.apps.iosched.test.util.fakes.FakeStarEventUseCase;
import com.google.samples.apps.iosched.test.util.fakes.FakeThemedActivityDelegate;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.ThemedActivityDelegate;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.day.TestUserEventDataSource;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase;
import com.google.samples.apps.iosched.ui.signin.FirebaseSignInViewModelDelegate;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.verify;
import com.nhaarman.mockito_kotlin.when;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ScheduleViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private ObserveUserAuthStateUseCase observeUserAuthStateUseCase;

    @Mock
    private LoadUserSessionsByDayUseCase loadSessionsUseCase;

    @Mock
    private LoadEventFiltersUseCase loadTagsUseCase;

    @Mock
    private SignInViewModelDelegate signInViewModelDelegate;

    @Mock
    private UserEventDataSource userEventDataSource;

    @Mock
    private SnackbarMessageManager snackbarMessageManager;

    private ScheduleViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<Block>>() {
           
        });

        viewModel.getEventFilters().observeForever(new Observer<List<EventFilter>>() {
            
        });
    }

    @Test
    public void testDataIsLoaded_ObservablesUpdated() throws ExecutionException, InterruptedException {
        when(loadSessionsUseCase.execute(any())).thenReturn(TestData.TestConferenceDays);
        when(loadTagsUseCase.execute(any())).thenReturn(MobileTestData.tagFiltersList);

        viewModel.loadData();

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0)),
                is(equalTo(TestData.userSessionMap.get(TestData.TestConferenceDays.get(0))))
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.getEventFilters()),
                is(CoreMatchers.notNullValue())
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.getEventFilters()).containsAll(MobileTestData.tagFiltersList),
                is(true)
        );
    }

    @Test
    public void profileClicked_whileLoggedIn_showsSignOutDialog() {
        when(signInViewModelDelegate.isSignedIn()).thenReturn(true);

        viewModel.onProfileClicked();

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.navigateToSignOutDialogAction),
                is(not(nullValue()))
        );
    }

    @Test
    public void profileClicked_whileLoggedOut_showsSignInDialog() {
        when(signInViewModelDelegate.isSignedIn()).thenReturn(false);

        viewModel.onProfileClicked();

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.navigateToSignInDialogAction),
                is(not(nullValue()))
        );
    }

    @Test
    public void loggedInUser_setsProfileContentDescription() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        when(mockUser.getUid()).thenReturn("123");
        when(mockUser.getPhotoUrl()).thenReturn(mock(Uri.class));
        when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observeUserAuthStateUseCaseSpy = spy(observeUserAuthStateUseCase);
        doReturn(Result.success(mockUser)).when(observeUserAuthStateUseCaseSpy).execute(anyString());

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                new FirebaseSignInViewModelDelegate(observeUserAuthStateUseCaseSpy, mock(AnalyticsHelper.class)),
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.profileContentDesc),
                is(equalTo(R.string.sign_out))
        );
    }

    @Test
    public void noLoggedInUser_setsProfileContentDescription() {
        ObserveUserAuthStateUseCase observeUserAuthStateUseCaseSpy = spy(observeUserAuthStateUseCase);
        doReturn(Result.success(null)).when(observeUserAuthStateUseCaseSpy).execute(anyString());

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                new FirebaseSignInViewModelDelegate(observeUserAuthStateUseCaseSpy, mock(AnalyticsHelper.class)),
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.profileContentDesc),
                is(equalTo(R.string.sign_in))
        );
    }

    @Test
    public void errorLoggingIn_setsProfileContentDescription() {
        ObserveUserAuthStateUseCase observeUserAuthStateUseCaseSpy = spy(observeUserAuthStateUseCase);
        doReturn(Result.error(new Exception())).when(observeUserAuthStateUseCaseSpy).execute(anyString());

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                new FirebaseSignInViewModelDelegate(observeUserAuthStateUseCaseSpy, mock(AnalyticsHelper.class)),
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.profileContentDesc),
                is(equalTo(R.string.sign_in))
        );
    }

    @Test
    public void testDataIsLoaded_Fails() {
        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.errorMessage),
                is(not(nullValue()))
        );
    }

    @Test
    public void testStarEvent() {
        viewModel.onStarClicked(TestData.userSession0);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(viewModel.snackBarMessage);
        SnackbarMessage message = nextMessageEvent.getContentIfNotHandled();
        MatcherAssert.assertThat(message.messageId, is(equalTo(R.string.event_starred)));
        MatcherAssert.assertThat(message.actionId, is(equalTo(R.string.dont_show)));
    }

    @Test
    public void testUnstarEvent() {
        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(viewModel.snackBarMessage);
        SnackbarMessage message = nextMessageEvent.getContentIfNotHandled();
        MatcherAssert.assertThat(message.messageId, is(equalTo(R.string.event_unstarred)));
        MatcherAssert.assertThat(message.actionId, is(equalTo(R.string.dont_show)));
    }

    @Test
    public void testStar_notLoggedInUser() {
        when(signInViewModelDelegate.isSignedIn()).thenReturn(false);

        viewModel.onStarClicked(TestData.userSession1);

        Event<SnackbarMessage> starEvent = LiveDataTestUtil.getValue(viewModel.snackBarMessage);
        MatcherAssert.assertThat(
                starEvent.getContentIfNotHandled().messageId,
                is(not(equalTo(R.string.reservation_request_succeeded)))
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.navigateToSignInDialogAction),
                is(not(nullValue()))
        );
    }

    @Test
    public void reservationReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        userEventsResult.setValue(new UserEventsResult(
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_RESERVATIONS),
                null
        ));

        viewModel.userEventDataSource = new TestUserEventDataSource(userEventsResult);

        viewModel.loadData();

        Event<SnackbarMessage> reservationMessage = LiveDataTestUtil.getValue(viewModel.snackBarMessage);
        MatcherAssert.assertThat(
                reservationMessage.getContentIfNotHandled().messageId,
                is(equalTo(R.string.reservation_new))
        );
    }

    @Test
    public void waitlistReceived() {
        MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
        userEventsResult.setValue(new UserEventsResult(
                new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_WAITLIST),
                null
        ));

        viewModel.userEventDataSource = new TestUserEventDataSource(userEventsResult);

        viewModel.loadData();

        Event<SnackbarMessage> waitlistMessage = LiveDataTestUtil.getValue(viewModel.snackBarMessage);

        MatcherAssert.assertThat(
                waitlistMessage.getContentIfNotHandled().messageId,
                is(equalTo(R.string.waitlist_new))
        );
    }

    @Test
    public void noLoggedInUser_showsReservationButton() {
        ObserveUserAuthStateUseCase observeUserAuthStateUseCaseSpy = spy(observeUserAuthStateUseCase);
        doReturn(Result.success(null)).when(observeUserAuthStateUseCaseSpy).execute(anyString());

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                new FirebaseSignInViewModelDelegate(observeUserAuthStateUseCaseSpy, mock(AnalyticsHelper.class)),
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.showReservations),
                is(true)
        );
    }

    @Test
    public void loggedInUser_registered_showsReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observeUserAuthStateUseCaseSpy = spy(observeUserAuthStateUseCase);
        doReturn(Result.success(mockUser)).when(observeUserAuthStateUseCaseSpy).execute(anyString());
        doReturn(Result.success(true)).when(observeUserAuthStateUseCaseSpy).isRegistered();

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                new FirebaseSignInViewModelDelegate(observeUserAuthStateUseCaseSpy, mock(AnalyticsHelper.class)),
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.showReservations),
                is(true)
        );
    }

    @Test
    public void loggedInUser_notRegistered_hidesReservationButton() {
        AuthenticatedUserInfoBasic mockUser = mock(AuthenticatedUserInfoBasic.class);
        when(mockUser.isSignedIn()).thenReturn(true);

        ObserveUserAuthStateUseCase observeUserAuthStateUseCaseSpy = spy(observeUserAuthStateUseCase);
        doReturn(Result.success(mockUser)).when(observeUserAuthStateUseCaseSpy).execute(anyString());
        doReturn(Result.success(false)).when(observeUserAuthStateUseCaseSpy).isRegistered();

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                new FirebaseSignInViewModelDelegate(observeUserAuthStateUseCaseSpy, mock(AnalyticsHelper.class)),
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.showReservations),
                is(false)
        );
    }

    @Test
    public void scheduleHints_notShown_on_launch() {
        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.scheduleUiHintsShown),
                is(false)
        );
    }

    @Test
    public void swipeRefresh_refreshesRemoteConfData() {
        RefreshConferenceDataUseCase refreshConferenceDataUseCaseSpy = spy(new RefreshConferenceDataUseCase(new TestDataRepository()));
        doNothing().when(refreshConferenceDataUseCaseSpy).execute();

        viewModel = new ScheduleViewModel(
                loadSessionsUseCase,
                loadTagsUseCase,
                signInViewModelDelegate,
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                refreshConferenceDataUseCaseSpy,
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        viewModel.onSwipeRefresh();

        verify(refreshConferenceDataUseCaseSpy).execute();

        MatcherAssert.assertThat(
                LiveDataTestUtil.getValue(viewModel.swipeRefreshing),
                is(false)
        );
    }

    @Test
    public void newDataFromConfRepo_scheduleUpdated() {
        ConferenceDataRepository repo = new ConferenceDataRepository(
                new TestConfDataSourceSession0(),
                new BootstrapDataSourceSession3()
        );

        LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase = createTestLoadUserSessionsByDayUseCase(
                conferenceDataRepo: repo
        );

        viewModel = new ScheduleViewModel(
                loadUserSessionsByDayUseCase,
                new LoadAgendaUseCase(new AgendaRepository()),
                new LoadEventFiltersUseCase(new TagRepository(new TestDataRepository())),
                signInViewModelDelegate,
                new FakeStarEventUseCase(),
                snackbarMessageManager,
                new FakeScheduleUiHintsShownUseCase(),
                new GetTimeZoneUseCase(new FakePreferenceStorage()),
                mock(TopicSubscriber.class),
                new RefreshConferenceDataUseCase(new TestDataRepository()),
                new ObserveConferenceDataUseCase(new TestDataRepository()),
                new LoadSelectedFiltersUseCase(new FakePreferenceStorage()),
                new SaveSelectedFiltersUseCase(new FakePreferenceStorage()),
                new FakeAnalyticsHelper()
        );

        viewModel.getSessionTimeDataForDay(0).observeForever(new Observer<List<Block>>() {
            
        });

        repo.refreshCacheWithRemoteConferenceData();

        List<Block> newValue = LiveDataTestUtil.getValue(viewModel.getSessionTimeDataForDay(0));

        MatcherAssert.assertThat(
                newValue.get(0).getSession(),
                is(TestData.session0)
        );
    }
    private ScheduleViewModel createScheduleViewModel(LoadUserSessionsByDayUseCase loadSessionsUseCase,
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
    AnalyticsHelper analyticsHelper,
    ThemedActivityDelegate themedActivityDelegate) {
    return new ScheduleViewModel(loadSessionsUseCase, loadAgendaUseCase, loadTagsUseCase, signInViewModelDelegate,
        starEventUseCase, scheduleUiHintsShownUseCase, topicSubscriber, snackbarMessageManager,
        getTimeZoneUseCase, refreshConferenceDataUseCase, observeConferenceDataUseCase,
        loadSelectedFiltersUseCase, saveSelectedFiltersUseCase, analyticsHelper,
        themedActivityDelegate);
    }
    private LoadUserSessionsByDayUseCase createTestLoadUserSessionsByDayUseCase(
    UserEventDataSource userEventDataSource = new TestUserEventDataSource(),
    ConferenceDataRepository conferenceDataRepo = new TestDataRepository()
    ) {
        SessionRepository sessionRepository = new DefaultSessionRepository(conferenceDataRepo);
        SessionAndUserEventRepository userEventRepository = new DefaultSessionAndUserEventRepository(
            userEventDataSource, sessionRepository);

        return new LoadUserSessionsByDayUseCase(userEventRepository);
    }

    private LoadEventFiltersUseCase createEventFiltersExceptionUseCase() {
        return new LoadEventFiltersUseCase(new TagRepository(new TestDataRepository())) {
            @Override
            public List execute(UserSessionMatcher parameters) {
                throw new Exception("Testing exception");
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
        return new GetTimeZoneUseCase(new FakePreferenceStorage()) {};
    }

}

class TestRegisteredUserDataSource implements RegisteredUserDataSource {
    private final Result<Boolean?> isRegistered;

    TestRegisteredUserDataSource(Result<Boolean?> isRegistered) {
        this.isRegistered = isRegistered;
    }

    @NonNull
    @Override
    public LiveData<Result<Boolean?>> observeResult() {
        MutableLiveData<Result<Boolean?>> liveData = new MutableLiveData<>();
        liveData.setValue(isRegistered);
        return liveData;
    }

    @Override
    public void listenToUserChanges(String userId) {

    }

    @Override
    public void setAnonymousValue() {

    }
}

class TestAuthStateUserDataSource implements AuthStateUserDataSource {
    private final Result<AuthenticatedUserInfoBasic?> user;

    TestAuthStateUserDataSource(Result<AuthenticatedUserInfoBasic?> user) {
        this.user = user;
    }

    @NonNull
    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic?>> getBasicUserInfo() {
        MutableLiveData<Result<AuthenticatedUserInfoBasic?>> liveData = new MutableLiveData<>();
        liveData.setValue(user);
        return liveData;
    }

    @Override
    public void startListening() {

    }

    @Override
    public void clearListener() {

    }
}

class FakeObserveUserAuthStateUseCase implements ObserveUserAuthStateUseCase {
    private final Result<AuthenticatedUserInfoBasic?> user;
    private final Result<Boolean?> isRegistered;
    private final RegisteredUserDataSource registeredUserDataSource;
    private final AuthStateUserDataSource authStateUserDataSource;

    FakeObserveUserAuthStateUseCase(Result<AuthenticatedUserInfoBasic?> user, Result<Boolean?> isRegistered) {
        this.user = user;
        this.isRegistered = isRegistered;
        this.registeredUserDataSource = new TestRegisteredUserDataSource(isRegistered);
        this.authStateUserDataSource = new TestAuthStateUserDataSource(user);
    }

    @NonNull
    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic?>> execute(String userId) {
        return new MutableLiveData<Result<AuthenticatedUserInfoBasic?>>() {{
            setValue(user);
        }};
    }

}

class FakeScheduleUiHintsShownUseCase implements ScheduleUiHintsShownUseCase {
    private final PreferenceStorage preferenceStorage;

    FakeScheduleUiHintsShownUseCase() {
        this.preferenceStorage = new FakePreferenceStorage();
    }

    @Override
    public boolean execute() {
        return false;
    }
}

class TestConfDataSourceSession0 implements ConferenceDataSource {
    @NonNull
    @Override
    public ConferenceData getRemoteConferenceData() {
        return conferenceData;
    }

    @NonNull
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
    @NonNull
    @Override
    public ConferenceData getRemoteConferenceData() {
        throw new NotImplementedError();
    }

    @NonNull
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