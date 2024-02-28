
package com.google.samples.apps.iosched.ui.schedule;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
import java.util.List;
import java.util.concurrent.ExecutionException;
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
  public void testDataIsLoaded_ObservablesUpdated()
    throws ExecutionException, InterruptedException {
    ScheduleViewModel viewModel = createScheduleViewModel(
      loadSessionsUseCase,
      loadTagsUseCase,
      signInDelegate
    );

    signInDelegate.loadUser("test");

    LiveDataTestUtil
      .getValue(viewModel.getSessionTimeDataForDay(0))
      .observeForever(new Observer<List<Block>>() {});

    List<EventFilter> loadedFilters = LiveDataTestUtil.getValue(
      viewModel.eventFilters
    );
    Assert.assertTrue(loadedFilters.containsAll(MobileTestData.tagFiltersList));
  }

  @Test
  public void testDataIsLoaded_Fails() {
    ScheduleViewModel viewModel = createScheduleViewModel();
    Assert.assertTrue(
      LiveDataTestUtil
        .getValue(viewModel.errorMessage)
        .peekContent()
        .isNotEmpty()
    );
  }

  @Test
  public void testStarEvent() {
    SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(
      new FakePreferenceStorage()
    );
    ScheduleViewModel viewModel = createScheduleViewModel(
      snackbarMessageManager
    );

    viewModel.onStarClicked(TestData.userSession0);

    Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(
      snackbarMessageManager.observeNextMessage()
    );
    Assert.assertThat(
      nextMessageEvent.getContentIfNotHandled().messageId,
      is(equalTo(R.string.event_starred))
    );
    Assert.assertThat(
      nextMessageEvent.getContentIfNotHandled().actionId,
      is(equalTo(R.string.dont_show))
    );
  }

  @Test
  public void testUnstarEvent() {
    SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(
      new FakePreferenceStorage()
    );
    ScheduleViewModel viewModel = createScheduleViewModel(
      snackbarMessageManager
    );

    viewModel.onStarClicked(TestData.userSession1);

    Event<SnackbarMessage> nextMessageEvent = LiveDataTestUtil.getValue(
      snackbarMessageManager.observeNextMessage()
    );
    Assert.assertThat(
      nextMessageEvent.getContentIfNotHandled().messageId,
      is(equalTo(R.string.event_unstarred))
    );
    Assert.assertThat(
      nextMessageEvent.getContentIfNotHandled().actionId,
      is(equalTo(R.string.dont_show))
    );
  }

  @Test
  public void testStar_notLoggedInUser() {
    signInDelegate.injectIsSignedIn = false;

    ScheduleViewModel viewModel = createScheduleViewModel(signInDelegate);

    viewModel.onStarClicked(TestData.userSession1);

    Event<SnackbarMessage> starEvent = LiveDataTestUtil.getValue(
      viewModel.snackBarMessage
    );
    Assert.assertThat(
      starEvent.getContentIfNotHandled().messageId,
      is(not(equalTo(R.string.reservation_request_succeeded)))
    );

    Event<Void> signInEvent = LiveDataTestUtil.getValue(
      viewModel.navigateToSignInDialogAction
    );
    Assert.assertNotNull(signInEvent.getContentIfNotHandled());
  }

  @Test
  public void reservationReceived() {
    MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
    TestUserEventDataSource source = new TestUserEventDataSource(
      userEventsResult
    );
    LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(
      source
    );
    signInDelegate = new FakeSignInViewModelDelegate();
    SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(
      new FakePreferenceStorage()
    );
    ScheduleViewModel viewModel = createScheduleViewModel(
      loadSessionsUseCase,
      signInDelegate,
      snackbarMessageManager
    );

    signInDelegate.loadUser("test");

    viewModel
      .getSessionTimeDataForDay(0)
      .observeForever(new Observer<List<Block>>() {});

    viewModel.snackBarMessage.observeForever(
      new Observer<Event<SnackbarMessage>>() {}
    );

    UserEventsResult oldValue = userEventsResult.getValue();
    UserEventsResult newValue = new UserEventsResult(
      oldValue.userEvents,
      new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_RESERVATIONS)
    );

    userEventsResult.postValue(newValue);

    Event<SnackbarMessage> reservationMessage = LiveDataTestUtil.getValue(
      snackbarMessageManager.observeNextMessage()
    );
    Assert.assertThat(
      reservationMessage.getContentIfNotHandled().messageId,
      is(equalTo(R.string.reservation_new))
    );
  }

  @Test
  public void waitlistReceived() {
    MutableLiveData<UserEventsResult> userEventsResult = new MutableLiveData<>();
    TestUserEventDataSource source = new TestUserEventDataSource(
      userEventsResult
    );
    LoadUserSessionsByDayUseCase loadSessionsUseCase = createTestLoadUserSessionsByDayUseCase(
      source
    );
    signInDelegate = new FakeSignInViewModelDelegate();
    SnackbarMessageManager snackbarMessageManager = new SnackbarMessageManager(
      new FakePreferenceStorage()
    );
    ScheduleViewModel viewModel = createScheduleViewModel(
      loadSessionsUseCase,
      signInDelegate,
      snackbarMessageManager
    );

    signInDelegate.loadUser("test");

    viewModel
      .getSessionTimeDataForDay(0)
      .observeForever(new Observer<List<Block>>() {});

    viewModel.snackBarMessage.observeForever(
      new Observer<Event<SnackbarMessage>>() {}
    );

    UserEventsResult oldValue = userEventsResult.getValue();
    UserEventsResult newValue = new UserEventsResult(
      oldValue.userEvents,
      new UserEventMessage(UserEventMessageChangeType.CHANGES_IN_WAITLIST)
    );

    userEventsResult.postValue(newValue);

    Event<SnackbarMessage> waitlistMessage = LiveDataTestUtil.getValue(
      snackbarMessageManager.observeNextMessage()
    );

    Assert.assertThat(
      waitlistMessage.getContentIfNotHandled().messageId,
      is(equalTo(R.string.waitlist_new))
    );
  }

  @Test
  public void noLoggedInUser_showsReservationButton() {
    ObserveUserAuthStateUseCase observableFirebaseUserUseCase = new FakeObserveUserAuthStateUseCase(
      null,
      Result.success(false)
    );
    SignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
      observableFirebaseUserUseCase,
      mock(AnalyticsHelper.class)
    );

    ScheduleViewModel viewModel = createScheduleViewModel(
      signInViewModelComponent
    );

    Assert.assertEquals(
      true,
      LiveDataTestUtil.getValue(viewModel.showReservations)
    );
  }

  @Test
  public void loggedInUser_registered_showsReservationButton() {
    AuthenticatedUserInfoBasic mockUser = mock(
      AuthenticatedUserInfoBasic.class
    );
    Mockito.when(mockUser.isSignedIn()).thenReturn(true);

    ObserveUserAuthStateUseCase observableFirebaseUserUseCase = new FakeObserveUserAuthStateUseCase(
      Result.success(mockUser),
      Result.success(true)
    );
    SignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
      observableFirebaseUserUseCase,
      mock(AnalyticsHelper.class)
    );

    ScheduleViewModel viewModel = createScheduleViewModel(
      signInViewModelComponent
    );

    Assert.assertEquals(
      true,
      LiveDataTestUtil.getValue(viewModel.showReservations)
    );
  }

  @Test
  public void loggedInUser_notRegistered_hidesReservationButton() {
    AuthenticatedUserInfoBasic mockUser = mock(
      AuthenticatedUserInfoBasic.class
    );
    Mockito.when(mockUser.isSignedIn()).thenReturn(true);

    ObserveUserAuthStateUseCase observableFirebaseUserUseCase = new FakeObserveUserAuthStateUseCase(
      Result.success(mockUser),
      Result.success(false)
    );
    SignInViewModelDelegate signInViewModelComponent = new FirebaseSignInViewModelDelegate(
      observableFirebaseUserUseCase,
      mock(AnalyticsHelper.class)
    );

    ScheduleViewModel viewModel = createScheduleViewModel(
      signInViewModelComponent
    );

    Assert.assertEquals(
      false,
      LiveDataTestUtil.getValue(viewModel.showReservations)
    );
  }

  @Test
  public void scheduleHints_notShown_on_launch() {
    ScheduleViewModel viewModel = createScheduleViewModel();

    Assert.assertEquals(
      false,
      LiveDataTestUtil
        .getValue(viewModel.scheduleUiHintsShown)
        .getContentIfNotHandled()
    );
  }

  @Test
  public void swipeRefresh_refreshesRemoteConfData() {
    ConferenceDataSource remoteDataSource = mock(ConferenceDataSource.class);
    ScheduleViewModel viewModel = createScheduleViewModel(
      refreshConferenceDataUseCase =
        new RefreshConferenceDataUseCase(
          new ConferenceDataRepository(
            remoteDataSource,
            TestDataSource.getInstance()
          )
        )
    );

    viewModel.onSwipeRefresh();

    Mockito.verify(remoteDataSource).getRemoteConferenceData();

    Assert.assertEquals(
      false,
      LiveDataTestUtil.getValue(viewModel.swipeRefreshing)
    );
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
    ObserveConferenceDataUseCase observeConferenceDataUseCase = new ObserveConferenceDataUseCase(
      repo
    );
    ScheduleViewModel viewModel = createScheduleViewModel(
      loadSessionsUseCase = loadUserSessionsByDayUseCase,
      observeConferenceDataUseCase = observeConferenceDataUseCase
    );

    viewModel
      .getSessionTimeDataForDay(0)
      .observeForever(new Observer<List<Block>>() {});

    repo.refreshCacheWithRemoteConferenceData();

    List<Block> newValue = LiveDataTestUtil.getValue(
      viewModel.getSessionTimeDataForDay(0)
    );

    Assert.assertThat(
      newValue.get(0).session,
      is(IsEqual.equalTo(TestData.session0))
    );
  }

  private ScheduleViewModel createScheduleViewModel(
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
    AnalyticsHelper analyticsHelper
  ) {
    return new ScheduleViewModel(
      loadUserSessionsByDayUseCase,
      loadAgendaUseCase,
      loadEventFiltersUseCase,
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

  private LoadUserSessionsByDayUseCase createTestLoadUserSessionsByDayUseCase(
    UserEventDataSource userEventDataSource,
    ConferenceDataRepository conferenceDataRepo
  ) {
    DefaultSessionRepository sessionRepository = new DefaultSessionRepository(
      conferenceDataRepo
    );
    DefaultSessionAndUserEventRepository userEventRepository = new DefaultSessionAndUserEventRepository(
      userEventDataSource,
      sessionRepository
    );

    return new LoadUserSessionsByDayUseCase(userEventRepository);
  }

  private LoadEventFiltersUseCase createEventFiltersExceptionUseCase() {
    return new LoadEventFiltersUseCase(
      new TagRepository(new TestDataRepository())
    ) {
      @Override
      public List<EventFilter> execute(UserSessionMatcher parameters) {
        throw new Exception("Testing exception");
      }
    };
  }

  private LoadAgendaUseCase createAgendaExceptionUseCase() {
    return new LoadAgendaUseCase(new AgendaRepository()) {
      @Override
      public List<Block> execute(Unit parameters) {
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

  private static class TestRegisteredUserDataSource
    implements RegisteredUserDataSource {

    private final Result<Boolean> isRegistered;

    TestRegisteredUserDataSource(Result<Boolean> isRegistered) {
      this.isRegistered = isRegistered;
    }

    @Override
    public void listenToUserChanges(String userId) {}

    @Override
    public LiveData<Result<Boolean>> observeResult() {
      MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
      liveData.setValue(isRegistered);
      return liveData;
    }

    @Override
    public void setAnonymousValue() {}
  }

  private static class TestAuthStateUserDataSource
    implements AuthStateUserDataSource {

    private final Result<AuthenticatedUserInfoBasic> user;

    TestAuthStateUserDataSource(Result<AuthenticatedUserInfoBasic> user) {
      this.user = user;
    }

    @Override
    public void startListening() {}

    @Override
    public LiveData<Result<AuthenticatedUserInfoBasic>> getBasicUserInfo() {
      MutableLiveData<Result<AuthenticatedUserInfoBasic>> liveData = new MutableLiveData<>();
      liveData.setValue(user);
      return liveData;
    }

    @Override
    public void clearListener() {}
  }

  private static class FakeObserveUserAuthStateUseCase
    extends ObserveUserAuthStateUseCase {

    public FakeObserveUserAuthStateUseCase(
      Result<AuthenticatedUserInfoBasic> user,
      Result<Boolean> isRegistered
    ) {
      super(
        new TestRegisteredUserDataSource(
          Result.success(isRegistered.getData())
        ),
        new TestAuthStateUserDataSource(user),
        mock(AnalyticsHelper.class)
      );
    }
  }

  private static class FakeScheduleUiHintsShownUseCase
    extends ScheduleUiHintsShownUseCase {

    public FakeScheduleUiHintsShownUseCase() {
      super(new FakePreferenceStorage());
    }
  }

  private static class TestConfDataSourceSession0
    implements ConferenceDataSource {

    @Override
    public ConferenceData getRemoteConferenceData() {
      return conferenceData;
    }

    @Override
    public ConferenceData getOfflineConferenceData() {
      return conferenceData;
    }

    private final ConferenceData conferenceData = new ConferenceData(
      TestData.sessions,
      TestData.tags,
      TestData.speakers,
      TestData.rooms,
      42
    );
  }

  private static class BootstrapDataSourceSession3
    implements ConferenceDataSource {

    @Override
    public ConferenceData getRemoteConferenceData() {
      throw new NotImplementedError();
    }

    @Override
    public ConferenceData getOfflineConferenceData() {
      return new ConferenceData(
        TestData.sessions3,
        TestData.tags,
        TestData.speakers,
        TestData.rooms,
        42
      );
    }
  }
}
