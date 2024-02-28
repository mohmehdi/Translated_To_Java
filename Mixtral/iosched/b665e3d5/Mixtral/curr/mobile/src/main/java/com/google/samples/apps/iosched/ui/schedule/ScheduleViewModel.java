package com.google.samples.apps.iosched.ui.schedule;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.model.SessionId;
import com.google.samples.apps.iosched.model.userdata.UserSession;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsActions;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.domain.RefreshConferenceDataUseCase;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.invoke;
import com.google.samples.apps.iosched.shared.domain.prefs.LoadSelectedFiltersUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.SaveSelectedFiltersUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.ScheduleUiHintsShownUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.EventLocation;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseParameters;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult;
import com.google.samples.apps.iosched.shared.domain.sessions.ObserveConferenceDataUseCase;
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase;
import com.google.samples.apps.iosched.shared.domain.users.StarEventParameter;
import com.google.samples.apps.iosched.shared.domain.users.StarEventUseCase;
import com.google.samples.apps.iosched.shared.domain.users.StarUpdatedStatus;
import com.google.samples.apps.iosched.shared.fcm.TopicSubscriber;
import com.google.samples.apps.iosched.shared.result.Event;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.result.Result.Success;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDays;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.MyEventsFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.TagFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActions;
import com.google.samples.apps.iosched.ui.sessioncommon.stringRes;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener, EventActions {

    private final LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase;
    private final LoadAgendaUseCase loadAgendaUseCase;
    private final LoadEventFiltersUseCase loadEventFiltersUseCase;
    private final SignInViewModelDelegate signInViewModelDelegate;
    private final StarEventUseCase starEventUseCase;
    private final ScheduleUiHintsShownUseCase scheduleUiHintsShownUseCase;
    private final TopicSubscriber topicSubscriber;
    private final SnackbarMessageManager snackbarMessageManager;
    private final GetTimeZoneUseCase getTimeZoneUseCase;
    private final RefreshConferenceDataUseCase refreshConferenceDataUseCase;
    private final ObserveConferenceDataUseCase observeConferenceDataUseCase;
    private final LoadSelectedFiltersUseCase loadSelectedFiltersUseCase;
    private final SaveSelectedFiltersUseCase saveSelectedFiltersUseCase;
    private final AnalyticsHelper analyticsHelper;
    private final UserSessionMatcher userSessionMatcher = new UserSessionMatcher();
    private final MutableLiveData<Result<Unit>> loadSelectedFiltersResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> preferConferenceTimeZoneResult = new MutableLiveData<>();
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay1 = new MediatorLiveData<>();
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay2 = new MediatorLiveData<>();
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay3 = new MediatorLiveData<>();
    private List<EventFilter> cachedEventFilters = new ArrayList<>();
    private final MutableLiveData<List<EventFilter>> _eventFilters = new MutableLiveData<>();
    private final MutableLiveData<List<EventFilter>> _selectedFilters = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _hasAnyFilters = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isAgendaPage = new MutableLiveData<>();
    private final MutableLiveData<TransientUiState> _transientUiState = new MutableLiveData<>();
    private final MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> loadSessionsResult = new MediatorLiveData<>();
    private final MutableLiveData<Result<List<Block>>> loadAgendaResult = new MutableLiveData<>();
    private final MediatorLiveData<Result<List<EventFilter>>> loadEventFiltersResult = new MediatorLiveData<>();
    private final MutableLiveData<Result<Boolean>> swipeRefreshResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>> _eventCount = new MutableLiveData<>();
    private final MutableLiveData<Event<List<Block>>> _agenda = new MutableLiveData<>();
    private final MediatorLiveData<Event<String>> _errorMessage = new MediatorLiveData<>();
    private final MutableLiveData<Event<String>> _navigateToSessionAction = new MutableLiveData<>();
    private final MediatorLiveData<Event<SnackbarMessage>> _snackBarMessage = new MediatorLiveData<>();
    private final MutableLiveData<Event<Boolean>> _showReservations = new MutableLiveData<>();
    private final MutableLiveData<Event<Unit>> _navigateToSignInDialogAction = new MutableLiveData<>();
    private final MutableLiveData<Event<Unit>> _navigateToSignOutDialogAction = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> scheduleUiHintsShownResult = new MutableLiveData<>();
    private final MediatorLiveData<Event<Boolean>> scheduleUiHintsShown = new MediatorLiveData<>();
    private boolean userHasInteracted;
    private final MutableLiveData<EventLocation> currentEvent = new MutableLiveData<>();

    public ScheduleViewModel(
            LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase,
            LoadAgendaUseCase loadAgendaUseCase,
            LoadEventFiltersUseCase loadEventFiltersUseCase,
            SignInViewModelDelegate signInViewModelDelegate,
            StarEventUseCase starEventUseCase,
            ScheduleUiHintsShownUseCase scheduleUiHintsShownUseCase,
            TopicSubscriber topicSubscriber,
            SnackbarMessageManager snackbarMessageManager,
            GetTimeZoneUseCase getTimeZoneUseCase,
            RefreshConferenceDataUseCase refreshConferenceDataUseCase,
            ObserveConferenceDataUseCase observeConferenceDataUseCase,
            LoadSelectedFiltersUseCase loadSelectedFiltersUseCase,
            SaveSelectedFiltersUseCase saveSelectedFiltersUseCase,
            AnalyticsHelper analyticsHelper
    ) {
        this.loadUserSessionsByDayUseCase = loadUserSessionsByDayUseCase;
        this.loadAgendaUseCase = loadAgendaUseCase;
        this.loadEventFiltersUseCase = loadEventFiltersUseCase;
        this.signInViewModelDelegate = signInViewModelDelegate;
        this.starEventUseCase = starEventUseCase;
        this.scheduleUiHintsShownUseCase = scheduleUiHintsShownUseCase;
        this.topicSubscriber = topicSubscriber;
        this.snackbarMessageManager = snackbarMessageManager;
        this.getTimeZoneUseCase = getTimeZoneUseCase;
        this.refreshConferenceDataUseCase = refreshConferenceDataUseCase;
        this.observeConferenceDataUseCase = observeConferenceDataUseCase;
        this.loadSelectedFiltersUseCase = loadSelectedFiltersUseCase;
        this.saveSelectedFiltersUseCase = saveSelectedFiltersUseCase;
        this.analyticsHelper = analyticsHelper;

        initializeViewModel();
    }
public LiveData getSessionTimeDataForDay(int day) {
  switch (day) {
  case 0:
    return sessionTimeDataDay1;
  case 1:
    return sessionTimeDataDay2;
  case 2:
    return sessionTimeDataDay3;
  default:
    Exception exception = new Exception("Invalid day: " + day);
    Timber.e(exception);
    throw exception;
  }
}

public void openEventDetail(SessionId id) {
  _navigateToSessionAction.setValue(new Event < > (id));
}

public void toggleFilter(EventFilter filter, boolean enabled) {
  boolean changed = false;
  if (filter instanceof MyEventsFilter) {
    userSessionMatcher.setShowPinnedEventsOnly(enabled);
    changed = true;
  } else if (filter instanceof TagFilter) {
    if (enabled) {
      userSessionMatcher.add(((TagFilter) filter).getTag());
    } else {
      userSessionMatcher.remove(((TagFilter) filter).getTag());
    }
    changed = true;
  }
  if (changed) {
    filter.isChecked.set(enabled);
    saveSelectedFiltersUseCase(userSessionMatcher);
    updateFilterStateObservables();
    refreshUserSessions();
    String filterName = "";
    if (filter instanceof MyEventsFilter) {
      filterName = "Starred & Reserved";
    } else {
      filterName = filter.getText();
    }
    String action = enabled ? AnalyticsActions.ENABLE : AnalyticsActions.DISABLE;
    analyticsHelper.logUiEvent("Filter changed: " + filterName, action);
  }
}

public void clearFilters() {
  if (userSessionMatcher.clearAll()) {
    for (EventFilter eventFilter: eventFilters.getValue()) {
      eventFilter.isChecked.set(false);
    }
    saveSelectedFiltersUseCase(userSessionMatcher);
    updateFilterStateObservables();
    refreshUserSessions();
    analyticsHelper.logUiEvent("Clear filters", AnalyticsActions.CLICK);
  }
}

private void updateFilterStateObservables() {
  boolean hasAnyFilters = userSessionMatcher.hasAnyFilters();
  _hasAnyFilters.setValue(hasAnyFilters);
  _selectedFilters.setValue(cachedEventFilters.stream()
    .filter(eventFilter -> eventFilter.isChecked.get())
    .collect(Collectors.toList()));
  TransientUiState state = _transientUiStateVar.copy(hasAnyFilters = hasAnyFilters);
  setTransientUiState(state);
  _transientUiState.setValue(state);
}

public void onSwipeRefresh() {
  refreshConferenceDataUseCase(any(), swipeRefreshResult);
}

public void onSignInRequired() {
  _navigateToSignInDialogAction.setValue(new Event < > (Unit));
}

private void refreshUserSessions() {
  Timber.d("ViewModel refreshing user sessions");
  loadUserSessionsByDayUseCase.execute(
    new LoadUserSessionsByDayUseCaseParameters(userSessionMatcher, getUserId())
  );
}

public void onStarClicked(UserSession userSession) {
  if (!isSignedIn()) {
    Timber.d("Showing Sign-in dialog after star click");
    _navigateToSignInDialogAction.setValue(new Event < > (Unit));
    return;
  }
  boolean newIsStarredState = !userSession.getUserEvent().isStarred();
  int stringResId = (newIsStarredState) ? R.string.event_starred : R.string.event_unstarred;
  if (newIsStarredState) {
    analyticsHelper.logUiEvent(userSession.getSession().getTitle(), AnalyticsActions.STARRED);
  }
  snackbarMessageManager.addMessage(
    new SnackbarMessage(
      R.string.event_starred,
      R.string.dont_show,
      UUID.randomUUID().toString()
    )
  );
  String userId = getUserId();
  if (userId != null) {
    starEventUseCase.execute(
      new StarEventParameter(
        userId,
        userSession.getUserEvent().copy(isStarred = newIsStarredState)
      )
    );
  }
}

public void setIsAgendaPage(boolean isAgendaPage) {
  if (_isAgendaPage.getValue() != isAgendaPage) {
    _isAgendaPage.setValue(isAgendaPage);
    TransientUiState state = _transientUiStateVar.copy(isAgendaPage = isAgendaPage);
    setTransientUiState(state);
    _transientUiState.setValue(state);
  }
}

private void setTransientUiState(TransientUiState state) {
  _transientUiStateVar = state;
  _transientUiState.setValue(state);
}

public void initializeTimeZone() {
  getTimeZoneUseCase(Unit.INSTANCE, preferConferenceTimeZoneResult);
}
}

data class SessionTimeData {
  List list;
  ZoneId timeZoneId;

  public SessionTimeData(List list, ZoneId timeZoneId) {
    this.list = list;
    this.timeZoneId = timeZoneId;
  }

  public SessionTimeData(List list) {
    this(list, null);
  }

  public SessionTimeData() {
    this(null, null);
  }
}

interface ScheduleEventListener extends EventActions {
  void toggleFilter(EventFilter filter, boolean enabled);

  void clearFilters();
}

public class SessionTimeData {
    private List<UserSession> list;
    private ZoneId timeZoneId;

    public SessionTimeData(List<UserSession> list, ZoneId timeZoneId) {
        this.list = list;
        this.timeZoneId = timeZoneId;
    }

    public SessionTimeData(List<UserSession> list) {
        this.list = list;
    }

    public SessionTimeData() {
    }

}

public class TransientUiState {
    private boolean isAgendaPage;
    private boolean hasAnyFilters;

    public TransientUiState(boolean isAgendaPage, boolean hasAnyFilters) {
        this.isAgendaPage = isAgendaPage;
        this.hasAnyFilters = hasAnyFilters;
    }

   
}