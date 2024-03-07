package com.google.samples.apps.iosched.ui.schedule;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
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
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.MyEventsFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.TagFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActions;
import com.google.samples.apps.iosched.ui.sessioncommon.stringRes;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import org.threeten.bp.ZoneId;
import timber.log.Timber;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

class ScheduleViewModel extends ViewModel implements ScheduleEventListener,
        SignInViewModelDelegate {

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

    private final MutableLiveData<Boolean> isLoading;
    private final MutableLiveData<Boolean> swipeRefreshing;

    private final UserSessionMatcher userSessionMatcher;
    private final MutableLiveData<Result<Unit>> loadSelectedFiltersResult;
    private final MutableLiveData<Result<Boolean>> preferConferenceTimeZoneResult;

    private final LiveData<List<Integer>> labelsForDays;
    private final LiveData<ZoneId> timeZoneId;

    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay1;
    private final LiveData<SessionTimeData> sessionTimeDataDay1;
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay2;
    private final LiveData<SessionTimeData> sessionTimeDataDay2;
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay3;
    private final LiveData<SessionTimeData> sessionTimeDataDay3;

    private List<EventFilter> cachedEventFilters;
    private final LiveData<List<EventFilter>> eventFilters;
    private final MutableLiveData<List<EventFilter>> _selectedFilters;
    private final LiveData<List<EventFilter>> selectedFilters;
    private final MutableLiveData<Boolean> _hasAnyFilters;
    private final LiveData<Boolean> hasAnyFilters;
    private final MutableLiveData<Boolean> _isAgendaPage;
    private final LiveData<Boolean> isAgendaPage;

    private TransientUiState _transientUiStateVar;
    private final MutableLiveData<TransientUiState> _transientUiState;

    private final MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> loadSessionsResult;
    private final MutableLiveData<Result<List<Block>>> loadAgendaResult;
    private final MediatorLiveData<Result<List<EventFilter>>> loadEventFiltersResult;
    private final MutableLiveData<Result<Boolean>> swipeRefreshResult;

    private final LiveData<Integer> eventCount;
    private final LiveData<List<Block>> agenda;

    private final MediatorLiveData<Event<String>> _errorMessage;
    private final LiveData<Event<String>> errorMessage;
    private final MediatorLiveData<Event<String>> _navigateToSessionAction;
    private final LiveData<Event<String>> navigateToSessionAction;
    private final MediatorLiveData<Event<SnackbarMessage>> _snackBarMessage;
    private final LiveData<Event<SnackbarMessage>> snackBarMessage;
    private final LiveData<Boolean> showReservations;
    private final MediatorLiveData<Event<Unit>> _navigateToSignInDialogAction;
    private final LiveData<Event<Unit>> navigateToSignInDialogAction;
    private final MediatorLiveData<Event<Unit>> _navigateToSignOutDialogAction;
    private final LiveData<Event<Unit>> navigateToSignOutDialogAction;
    private final MutableLiveData<Result<Boolean>> scheduleUiHintsShownResult;
    private final LiveData<Event<Boolean>> scheduleUiHintsShown;
    private boolean userHasInteracted;

    private final LiveData<EventLocation> currentEvent;

    ScheduleViewModel(
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
            AnalyticsHelper analyticsHelper) {
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

        isLoading = new MutableLiveData<>();
        swipeRefreshing = new MutableLiveData<>();
        userSessionMatcher = new UserSessionMatcher();
        loadSelectedFiltersResult = new MutableLiveData<>();
        preferConferenceTimeZoneResult = new MutableLiveData<>();
        labelsForDays = new MutableLiveData<>();
        timeZoneId = new MutableLiveData<>();
        _sessionTimeDataDay1 = new MediatorLiveData<>();
        sessionTimeDataDay1 = _sessionTimeDataDay1;
        _sessionTimeDataDay2 = new MediatorLiveData<>();
        sessionTimeDataDay2 = _sessionTimeDataDay2;
        _sessionTimeDataDay3 = new MediatorLiveData<>();
        sessionTimeDataDay3 = _sessionTimeDataDay3;
        cachedEventFilters = null;
        eventFilters = new MutableLiveData<>();
        _selectedFilters = new MutableLiveData<>();
        selectedFilters = _selectedFilters;
        _hasAnyFilters = new MutableLiveData<>();
        hasAnyFilters = _hasAnyFilters;
        _isAgendaPage = new MutableLiveData<>();
        isAgendaPage = _isAgendaPage;
        _transientUiStateVar = new TransientUiState(false, false);
        _transientUiState = new MutableLiveData<>();
        loadSessionsResult = new MediatorLiveData<>();
        loadAgendaResult = new MutableLiveData<>();
        loadEventFiltersResult = new MediatorLiveData<>();
        swipeRefreshResult = new MutableLiveData<>();
        eventCount = new MutableLiveData<>();
        agenda = new MutableLiveData<>();
        _errorMessage = new MediatorLiveData<>();
        errorMessage = _errorMessage;
        _navigateToSessionAction = new MediatorLiveData<>();
        navigateToSessionAction = _navigateToSessionAction;
        _snackBarMessage = new MediatorLiveData<>();
        snackBarMessage = _snackBarMessage;
        showReservations = new MutableLiveData<>();
        _navigateToSignInDialogAction = new MediatorLiveData<>();
        navigateToSignInDialogAction = _navigateToSignInDialogAction;
        _navigateToSignOutDialogAction = new MediatorLiveData<>();
        navigateToSignOutDialogAction = _navigateToSignOutDialogAction;
        scheduleUiHintsShownResult = new MutableLiveData<>();
        scheduleUiHintsShown = new MutableLiveData<>();
        userHasInteracted = false;
        currentEvent = new MutableLiveData<>();

        loadSessionsResult.addSource(loadUserSessionsByDayUseCase.observe(), result -> {
            isLoading.setValue(result == Result.Loading.INSTANCE);
            if (result instanceof Result.Success) {
                refreshUserSessions();
            }
            handleLoadSessionsResult(result);
        });

        loadSessionsResult.addSource(observeConferenceDataUseCase.observe(), this::handleConferenceData);

        loadEventFiltersResult.addSource(loadSelectedFiltersResult,
                result -> loadEventFiltersUseCase.invoke(userSessionMatcher, loadEventFiltersResult));

        loadEventFiltersResult.addSource(observeConferenceDataUseCase.observe(),
                result -> loadEventFiltersUseCase.invoke(userSessionMatcher, loadEventFiltersResult));

        loadSelectedFiltersUseCase.invoke(userSessionMatcher, loadSelectedFiltersResult);

        loadSessionsResult.addSource(observeConferenceDataUseCase.observe(), this::handleConferenceData);
        loadSessionsResult.addSource(loadEventFiltersResult, result -> refreshUserSessions());

        loadAgendaUseCase.invoke(loadAgendaResult);

        eventCount = loadSessionsResult.map(
                result -> (result instanceof Result.Success)
                        ? ((Result.Success<?>) result).getData().getUserSessionCount()
                        : 0);

        _errorMessage.addSource(loadSessionsResult, result -> handleError(result));
        _errorMessage.addSource(loadEventFiltersResult, result -> handleError(result));

        agenda = loadAgendaResult.map(
                result -> (result instanceof Result.Success) ? ((Result.Success<?>) result).getData() : emptyList());

        eventFilters = loadEventFiltersResult.map(
                result -> {
                    if (result instanceof Result.Success) {
                        cachedEventFilters = ((Result.Success<List<EventFilter>>) result).getData();
                        updateFilterStateObservables();
                    }
                    return cachedEventFilters;
                });

        _snackBarMessage.addSource(starEventUseCase.observe(), result -> {
            if (result instanceof Result.Error) {
                _snackBarMessage.postValue(new Event<>(
                        new SnackbarMessage(R.string.event_star_error, true)));
            }
        });

        _snackBarMessage.addSource(loadUserSessionsByDayUseCase.observe(), result -> {
            if (result instanceof Result.Success) {
                String type = result.getData().getUserMessage().getType().stringRes();
                if (type != null) {
                    snackbarMessageManager.addMessage(
                            new SnackbarMessage(type, true,
                                    result.getData().getUserMessageSession(),
                                    result.getData().getUserMessage().getChangeRequestId()));
                }
            }
        });

        showReservations = signInViewModelDelegate.getCurrentUserInfo().map(
                userInfo -> isRegistered() || !isSignedIn());

        scheduleUiHintsShownUseCase.invoke(Unit, scheduleUiHintsShownResult);
        scheduleUiHintsShown.postValue(new Event<>(
                (scheduleUiHintsShownResult.getValue() instanceof Result.Success) &&
                        ((Result.Success<Boolean>) scheduleUiHintsShownResult.getValue()).getData()));

        LiveData<Boolean> showInConferenceTimeZone = preferConferenceTimeZoneResult.map(
                result -> (result instanceof Result.Success) ? ((Result.Success<Boolean>) result).getData() : true);

        timeZoneId = showInConferenceTimeZone
                .map(inConferenceTimeZone -> (inConferenceTimeZone) ? TimeUtils.CONFERENCE_TIMEZONE
                        : ZoneId.systemDefault());

        labelsForDays = showInConferenceTimeZone
                .map(inConferenceTimeZone -> (TimeUtils.physicallyInConferenceTimeZone() || inConferenceTimeZone)
                        ? List.of(R.string.day1_date, R.string.day2_date, R.string.day3_date)
                        : List.of(R.string.day1, R.string.day2, R.string.day3));

        _sessionTimeDataDay1.addSource(timeZoneId, it -> {
            if (_sessionTimeDataDay1.getValue() != null) {
                _sessionTimeDataDay1.getValue().setTimeZoneId(it);
            } else {
                _sessionTimeDataDay1.setValue(new SessionTimeData(it));
            }
        });
        _sessionTimeDataDay1.addSource(loadSessionsResult, it -> {
            if (it instanceof Result.Success) {
                List<UserSession> userSessions = ((Result.Success<?>) it).getData().getUserSessionsPerDay()
                        .get(ConferenceDays.INSTANCE.get(0));
                if (_sessionTimeDataDay1.getValue() != null) {
                    _sessionTimeDataDay1.getValue().setList(userSessions);
                } else {
                    _sessionTimeDataDay1.setValue(new SessionTimeData(userSessions));
                }
            }
        });

        _sessionTimeDataDay2.addSource(timeZoneId, it -> {
            if (_sessionTimeDataDay2.getValue() != null) {

                _sessionTimeDataDay2.getValue().setTimeZoneId(it);
            } else {
                _sessionTimeDataDay2.setValue(new SessionTimeData(it));
            }
        });
        _sessionTimeDataDay2.addSource(loadSessionsResult, it -> {
            if (it instanceof Result.Success) {
                List<UserSession> userSessions = ((Result.Success<?>) it).getData().getUserSessionsPerDay()
                        .get(ConferenceDays.INSTANCE.get(1));
                if (_sessionTimeDataDay2.getValue() != null) {
                    _sessionTimeDataDay2.getValue().setList(userSessions);
                } else {
                    _sessionTimeDataDay2.setValue(new SessionTimeData(userSessions));
                }
            }
        });

        _sessionTimeDataDay3.addSource(timeZoneId, it -> {
            if (_sessionTimeDataDay3.getValue() != null) {
                _sessionTimeDataDay3.getValue().setTimeZoneId(it);
            } else {
                _sessionTimeDataDay3.setValue(new SessionTimeData(it));
            }
        });
        _sessionTimeDataDay3.addSource(loadSessionsResult, it -> {
            if (it instanceof Result.Success) {
                List<UserSession> userSessions = ((Result.Success<?>) it).getData().getUserSessionsPerDay()
                        .get(ConferenceDays.INSTANCE.get(2));
                if (_sessionTimeDataDay3.getValue() != null) {
                    _sessionTimeDataDay3.getValue().setList(userSessions);
                } else {
                    _sessionTimeDataDay3.setValue(new SessionTimeData(userSessions));
                }
            }
        });

        swipeRefreshing = swipeRefreshResult.map(result -> false);

        topicSubscriber.subscribeToScheduleUpdates();

        observeConferenceDataUseCase.execute(new Object());

        currentEvent = loadSessionsResult.map(result -> (result instanceof Result.Success)
                ? ((Result.Success<LoadUserSessionsByDayUseCaseResult>) result).getData().getFirstUnfinishedSession()
                : null);
    }

    LiveData<SessionTimeData> getSessionTimeDataForDay(int day) {
        switch (day) {
            case 0:
                return sessionTimeDataDay1;
            case 1:
                return sessionTimeDataDay2;
            case 2:
                return sessionTimeDataDay3;
            default:
                throw new IllegalArgumentException("Invalid day: " + day);
        }
    }

    private void updateFilterStateObservables() {
        boolean hasAnyFilters = userSessionMatcher.hasAnyFilters();
        _hasAnyFilters.setValue(hasAnyFilters);
        _selectedFilters.setValue(cachedEventFilters.stream()
                .filter(EventFilter::isChecked)
                .collect(Collectors.toList()));
        setTransientUiState(_transientUiStateVar.copy(hasAnyFilters = hasAnyFilters));
    }

    @Override
    public void openEventDetail(SessionId id) {
        _navigateToSessionAction.setValue(new Event<>(id));
    }

    @Override
    public void toggleFilter(EventFilter filter, boolean enabled) {
        boolean changed;
        switch (filter) {
            case MyEventsFilter:
                changed = userSessionMatcher.setShowPinnedEventsOnly(enabled);
                break;
            case TagFilter:
                if (enabled) {
                    changed = userSessionMatcher.add(((TagFilter) filter).getTag());
                } else {
                    changed = userSessionMatcher.remove(((TagFilter) filter).getTag());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown filter type: " + filter);
        }

        if (changed) {
            filter.isChecked().set(enabled);
            saveSelectedFiltersUseCase.invoke(userSessionMatcher);
            updateFilterStateObservables();
            refreshUserSessions();

            String filterName = (filter instanceof MyEventsFilter) ? "Starred & Reserved" : filter.getText();
            String action = (enabled) ? AnalyticsActions.ENABLE : AnalyticsActions.DISABLE;
            analyticsHelper.logUiEvent("Filter changed: " + filterName, action);
        }
    }

    @Override
    public void clearFilters() {
        if (userSessionMatcher.clearAll()) {
            eventFilters.getValue().forEach(f -> f.isChecked().set(false));
            saveSelectedFiltersUseCase.invoke(userSessionMatcher);
            updateFilterStateObservables();
            refreshUserSessions();

            analyticsHelper.logUiEvent("Clear filters", AnalyticsActions.CLICK);
        }
    }

    void onSwipeRefresh() {
        refreshConferenceDataUseCase.invoke(new Object(), swipeRefreshResult);
    }

    void onSignInRequired() {
        _navigateToSignInDialogAction.setValue(new Event<>(new Unit()));
    }

    private void refreshUserSessions() {
        Timber.d("ViewModel refreshing user sessions");
        loadUserSessionsByDayUseCase.execute(
                new LoadUserSessionsByDayUseCaseParameters(userSessionMatcher, getUserId()));
    }

    @Override
    public void onStarClicked(UserSession userSession) {
        if (!isSignedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            _navigateToSignInDialogAction.setValue(new Event<>(new Unit()));
            return;
        }
        boolean newIsStarredState = !userSession.getUserEvent().isStarred();

        int stringResId = (newIsStarredState) ? R.string.event_starred : R.string.event_unstarred;

        if (newIsStarredState) {
            analyticsHelper.logUiEvent(userSession.getSession().getTitle(), AnalyticsActions.STARRED);
        }

        snackbarMessageManager.addMessage(new SnackbarMessage(
                stringResId, R.string.dont_show, UUID.randomUUID().toString()));

        getUserId().ifPresent(userId -> starEventUseCase.execute(
                new StarEventParameter(
                        userId,
                        userSession.getUserEvent().copy(isStarred = newIsStarredState))));
    }

    void setIsAgendaPage(boolean isAgendaPage) {
        if (_isAgendaPage.getValue() != isAgendaPage) {
            _isAgendaPage.setValue(isAgendaPage);
            setTransientUiState(_transientUiStateVar.copy(isAgendaPage = isAgendaPage));
        }
    }

    private void setTransientUiState(TransientUiState state) {
        _transientUiStateVar = state;
        _transientUiState.setValue(state);
    }

    void initializeTimeZone() {
        getTimeZoneUseCase.invoke(new Unit(), preferConferenceTimeZoneResult);
    }
}

class SessionTimeData {
    private List<UserSession> list;
    private ZoneId timeZoneId;

    SessionTimeData(List<UserSession> list, ZoneId timeZoneId) {
        this.list = list;
        this.timeZoneId = timeZoneId;
    }

    List<UserSession> getList() {
        return list;
    }

    void setList(List<UserSession> list) {
        this.list = list;
    }

    ZoneId getTimeZoneId() {
        return timeZoneId;
    }

    void setTimeZoneId(ZoneId timeZoneId) {
        this.timeZoneId = timeZoneId;
    }
}

interface ScheduleEventListener extends EventActions {
    void toggleFilter(EventFilter filter, boolean enabled);

    void clearFilters();
}

class TransientUiState {
    private final boolean isAgendaPage;
    private final boolean hasAnyFilters;

    TransientUiState(boolean isAgendaPage, boolean hasAnyFilters) {
        this.isAgendaPage = isAgendaPage;
        this.hasAnyFilters = hasAnyFilters;
    }

    boolean isAgendaPage() {
        return isAgendaPage;
    }

    boolean hasAnyFilters() {
        return hasAnyFilters;
    }
}
