
package com.google.samples.apps.iosched.ui.schedule;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import android.util.Log;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.model.SessionId;
import com.google.samples.apps.iosched.model.userdata.UserSession;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsActions;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.data.signin.AuthenticatedUserInfo;
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
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDays;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.ThemedActivityDelegate;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.MyEventsFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.TagFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActions;
import com.google.samples.apps.iosched.ui.sessioncommon.stringRes;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

class ScheduleViewModel extends ViewModel implements ScheduleEventListener, SignInViewModelDelegate {
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
    private final ThemedActivityDelegate themedActivityDelegate;

    private final UserSessionMatcher userSessionMatcher = new UserSessionMatcher();
    private final MutableLiveData<Result<Unit>> loadSelectedFiltersResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> preferConferenceTimeZoneResult = new MutableLiveData<>();

    private final LiveData<List<Int>> labelsForDays;
    private final LiveData<ZoneId> timeZoneId;

    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay1 = new MediatorLiveData<>();
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay2 = new MediatorLiveData<>();
    private final MediatorLiveData<SessionTimeData> _sessionTimeDataDay3 = new MediatorLiveData<>();

    private List<EventFilter> cachedEventFilters = new ArrayList<>();

    private final MutableLiveData<List<EventFilter>> eventFilters = new MutableLiveData<>();
    private final MutableLiveData<List<EventFilter>> _selectedFilters = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _hasAnyFilters = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isAgendaPage = new MutableLiveData<>();

    private final TransientUiState _transientUiStateVar = new TransientUiState(false, false);
    private final MutableLiveData<TransientUiState> _transientUiState = new MutableLiveData<>();

    private final MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> loadSessionsResult = new MediatorLiveData<>();
    private final MutableLiveData<Result<List<Block>>> loadAgendaResult = new MutableLiveData<>();
    private final MediatorLiveData<Result<List<EventFilter>>> loadEventFiltersResult = new MediatorLiveData<>();
    private final MutableLiveData<Result<Boolean>> swipeRefreshResult = new MutableLiveData<>();

    private final MediatorLiveData<Event<String>> _errorMessage = new MediatorLiveData<>();
    private final MutableLiveData<Event<String>> _navigateToSessionAction = new MutableLiveData<>();
    private final MediatorLiveData<Event<SnackbarMessage>> _snackBarMessage = new MediatorLiveData<>();

    private final MutableLiveData<Integer> _profileContentDesc = new MediatorLiveData<>();

    private final MutableLiveData<Boolean> showReservations;

    private final MutableLiveData<Event<Unit>> _navigateToSignInDialogAction = new MutableLiveData<>();
    private final MutableLiveData<Event<Unit>> _navigateToSignOutDialogAction = new MutableLiveData<>();

    private final MutableLiveData<Result<Boolean>> scheduleUiHintsShownResult = new MutableLiveData<>();

    private boolean userHasInteracted;

    private final LiveData<EventLocation> currentEvent;

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
            AnalyticsHelper analyticsHelper,
            ThemedActivityDelegate themedActivityDelegate
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
        this.themedActivityDelegate = themedActivityDelegate;

        labelsForDays = Transformations.map(preferConferenceTimeZoneResult, new Function<Result<Boolean>, List<Integer>>() {
            @Override
            public List<Integer> apply(Result<Boolean> result) {
                if (result.isSuccess()) {
                    boolean inConferenceTimeZone = result.getData();
                    if (TimeUtils.physicallyInConferenceTimeZone() || inConferenceTimeZone) {
                        return Arrays.asList(R.string.day1_date, R.string.day2_date, R.string.day3_date);
                    } else {
                        return Arrays.asList(R.string.day1, R.string.day2, R.string.day3);
                    }
                }
                return new ArrayList<>();
            }
        });

        timeZoneId = Transformations.switchMap(preferConferenceTimeZoneResult, new Function<Result<Boolean>, LiveData<ZoneId>>() {
            @Override
            public LiveData<ZoneId> apply(Result<Boolean> result) {
                if (result.isSuccess()) {
                    boolean inConferenceTimeZone = result.getData();
                    if (inConferenceTimeZone) {
                        return getTimeZoneUseCase.getTimeZone(TimeUtils.CONFERENCE_TIMEZONE);
                    } else {
                        return new MutableLiveData<>(ZoneId.systemDefault());
                    }
                }
                return new MutableLiveData<>();
            }
        });
        labelsForDays = showInConferenceTimeZone.stream().map(inConferenceTimeZone - > {
            if (TimeUtils.physicallyInConferenceTimeZone() || inConferenceTimeZone) {
                return Arrays.asList(R.string.day1_date, R.string.day2_date, R.string.day3_date);
            } else {
                return Arrays.asList(R.string.day1, R.string.day2, R.string.day3);
            }
        }).collect(Collectors.toList());

        _sessionTimeDataDay1.addSource(timeZoneId, (timeZone, _) - > {
            _sessionTimeDataDay1.setValue(_sessionTimeDataDay1.getValue().apply {
                timeZoneId = timeZone;
            } != null ? _sessionTimeDataDay1.getValue().apply {
                timeZoneId = timeZone;
            } : new SessionTimeData(timeZoneId));
        });
        loadSessionsResult.subscribe(loadSessions - > {
            if (loadSessions instanceof Result.Success) {
                userSessions = ((Result.Success) loadSessions).data.userSessionsPerDay.get(ConferenceDays[0]);
                _sessionTimeDataDay1.setValue(_sessionTimeDataDay1.getValue().apply {
                    list = userSessions;
                } != null ? _sessionTimeDataDay1.getValue().apply {
                    list = userSessions;
                } : new SessionTimeData(list));
            }
        });

        _sessionTimeDataDay2.addSource(timeZoneId, (timeZone, _) - > {
            _sessionTimeDataDay2.setValue(_sessionTimeDataDay2.getValue().apply {
                timeZoneId = timeZone;
            } != null ? _sessionTimeDataDay2.getValue().apply {
                timeZoneId = timeZone;
            } : new SessionTimeData(timeZoneId));
        });
        loadSessionsResult.subscribe(loadSessions - > {
            if (loadSessions instanceof Result.Success) {
                userSessions = ((Result.Success) loadSessions).data.userSessionsPerDay.get(ConferenceDays[1]);
                _sessionTimeDataDay2.setValue(_sessionTimeDataDay2.getValue().apply {
                    list = userSessions;
                } != null ? _sessionTimeDataDay2.getValue().apply {
                    list = userSessions;
                } : new SessionTimeData(list));
            }
        });

        _sessionTimeDataDay3.addSource(timeZoneId, (timeZone, _) - > {
            _sessionTimeDataDay3.setValue(_sessionTimeDataDay3.getValue().apply {
                timeZoneId = timeZone;
            } != null ? _sessionTimeDataDay3.getValue().apply {
                timeZoneId = timeZone;
            } : new SessionTimeData(timeZoneId));
        });
        loadSessionsResult.subscribe(loadSessions - > {
            if (loadSessions instanceof Result.Success) {
                userSessions = ((Result.Success) loadSessions).data.userSessionsPerDay.get(ConferenceDays[2]);
                _sessionTimeDataDay3.setValue(_sessionTimeDataDay3.getValue().apply {
                    list = userSessions;
                } != null ? _sessionTimeDataDay3.getValue().apply {
                    list = userSessions;
                } : new SessionTimeData(list));
            }
        });

        swipeRefreshing = swipeRefreshResult.map(loadSessions - > {
            return false;
        });

        topicSubscriber.subscribeToScheduleUpdates();

        observeConferenceDataUseCase.execute(new Any());

        currentEvent = loadSessionsResult.map(loadSessions - > {
            return (loadSessions instanceof Success) ? ((Success) loadSessions).data.firstUnfinishedSession : null;
        }); 
        // ... initialize other LiveData objects and set up observers

        // ... initialize other fields and set up observers

        // ... initialize currentEvent
    }
    public LiveData<SessionTimeData> getSessionTimeDataForDay(int day) {
        if (day == 0) {
            return sessionTimeDataDay1;
        } else if (day == 1) {
            return sessionTimeDataDay2;
        } else if (day == 2) {
            return sessionTimeDataDay3;
        } else {
            Exception exception = new Exception("Invalid day: " + day);
            Timber.e(exception);
            throw exception;
        }
    }

    @Override
    public void openEventDetail(SessionId id) {
        _navigateToSessionAction.setValue(new Event<>(id));
    }

    @Override
    public void toggleFilter(EventFilter filter, boolean enabled) {
        boolean changed = false;
        if (filter instanceof MyEventsFilter) {
            changed = userSessionMatcher.setShowPinnedEventsOnly(enabled);
        } else if (filter instanceof TagFilter) {
            if (enabled) {
                userSessionMatcher.add(((TagFilter) filter).tag);
            } else {
                userSessionMatcher.remove(((TagFilter) filter).tag);
            }
            changed = true;
        }
        if (changed) {
            filter.isChecked.set(enabled);
            saveSelectedFiltersUseCase(userSessionMatcher);
            updateFilterStateObservables();
            refreshUserSessions();
            String filterName = (filter instanceof MyEventsFilter) ? "Starred & Reserved" : filter.getText();
            String action = enabled ? AnalyticsActions.ENABLE : AnalyticsActions.DISABLE;
            analyticsHelper.logUiEvent("Filter changed: " + filterName, action);
        }
    }

    @Override
    public void clearFilters() {
        if (userSessionMatcher.clearAll()) {
            if (eventFilters.getValue() != null) {
                eventFilters.getValue().forEach(it -> it.isChecked.set(false));
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
        _selectedFilters.setValue(cachedEventFilters.stream().filter(it -> it.isChecked.get()).collect(Collectors.toList()));
        setTransientUiState(_transientUiStateVar.copy(hasAnyFilters = hasAnyFilters));
    }

    public void onSwipeRefresh() {
        refreshConferenceDataUseCase(Any(), swipeRefreshResult);
    }

    public void onProfileClicked() {
        if (isSignedIn()) {
            _navigateToSignOutDialogAction.setValue(new Event<>(Unit));
        } else {
            _navigateToSignInDialogAction.setValue(new Event<>(Unit));
        }
    }

    public void onSignInRequired() {
        _navigateToSignInDialogAction.setValue(new Event<>(Unit));
    }

    @StringRes
    private int getProfileContentDescription(Result<AuthenticatedUserInfo> userResult) {
        if (userResult instanceof Success && userResult.getData().isSignedIn()) {
            return R.string.sign_out;
        } else {
            return R.string.sign_in;
        }
    }

    private void refreshUserSessions() {
        Timber.d("ViewModel refreshing user sessions");
        loadUserSessionsByDayUseCase.execute(
                new LoadUserSessionsByDayUseCaseParameters(userSessionMatcher, getUserId())
        );
    }

    @Override
    public void onStarClicked(UserSession userSession) {
        if (!isSignedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            _navigateToSignInDialogAction.setValue(new Event<>(Unit));
            return;
        }
        boolean newIsStarredState = !userSession.getUserEvent().isStarred();
        int stringResId = newIsStarredState ? R.string.event_starred : R.string.event_unstarred;
        if (newIsStarredState) {
            analyticsHelper.logUiEvent(userSession.getSession().getTitle(), AnalyticsActions.STARRED);
        }
        snackbarMessageManager.addMessage(
                new SnackbarMessage(
                        messageId = stringResId,
                        actionId = R.string.dont_show,
                        requestChangeId = UUID.randomUUID().toString()
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
            setTransientUiState(_transientUiStateVar.copy(isAgendaPage = isAgendaPage));
        }
    }

    private void setTransientUiState(TransientUiState state) {
        _transientUiStateVar = state;
        _transientUiState.setValue(state);
    }

    public void initializeTimeZone() {
        getTimeZoneUseCase(Unit, preferConferenceTimeZoneResult);
    }
    // ... implement required methods from ScheduleEventListener, SignInViewModelDelegate, and ThemedActivityDelegate
}


public class SessionTimeData {

    private final List<UserSession> list;
    private final ZoneId timeZoneId;

    public SessionTimeData(List<UserSession> list, ZoneId timeZoneId) {
        this.list = list;
        this.timeZoneId = timeZoneId;
    }
}
interface ScheduleEventListener extends EventActions {

    void toggleFilter(EventFilter filter, boolean enabled);

    void clearFilters();
}
public class TransientUiState {

    private final boolean isAgendaPage;
    private final boolean hasAnyFilters;

    public TransientUiState(boolean isAgendaPage, boolean hasAnyFilters) {
        this.isAgendaPage = isAgendaPage;
        this.hasAnyFilters = hasAnyFilters;
    }
}