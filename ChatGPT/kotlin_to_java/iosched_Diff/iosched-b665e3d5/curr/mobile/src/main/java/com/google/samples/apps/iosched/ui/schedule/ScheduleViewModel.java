package com.google.samples.apps.iosched.ui.schedule;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.SessionId;
import com.google.samples.apps.iosched.model.userdata.UserSession;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsActions;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.domain.RefreshConferenceDataUseCase;
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
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import org.threeten.bp.ZoneId;
import timber.log.Timber;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener, SignInViewModelDelegate {

    private LiveData<Boolean> isLoading;
    private LiveData<Boolean> swipeRefreshing;

    private UserSessionMatcher userSessionMatcher = new UserSessionMatcher();
    private MutableLiveData<Result<Unit>> loadSelectedFiltersResult = new MutableLiveData<>();

    private MutableLiveData<Result<Boolean>> preferConferenceTimeZoneResult = new MutableLiveData<>();

    private LiveData<List<Integer>> labelsForDays;
    private LiveData<ZoneId> timeZoneId;

    private MediatorLiveData<SessionTimeData> _sessionTimeDataDay1 = new MediatorLiveData<>();
    private LiveData<SessionTimeData> sessionTimeDataDay1;
    private MediatorLiveData<SessionTimeData> _sessionTimeDataDay2 = new MediatorLiveData<>();
    private LiveData<SessionTimeData> sessionTimeDataDay2;
    private MediatorLiveData<SessionTimeData> _sessionTimeDataDay3 = new MediatorLiveData<>();
    private LiveData<SessionTimeData> sessionTimeDataDay3;

    private List<EventFilter> cachedEventFilters;

    private LiveData<List<EventFilter>> eventFilters;
    private MutableLiveData<List<EventFilter>> _selectedFilters = new MutableLiveData<>();
    private LiveData<Boolean> hasAnyFilters;

    private MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> loadSessionsResult;
    private MediatorLiveData<Result<List<EventFilter>>> loadEventFiltersResult = new MediatorLiveData<>();
    private MutableLiveData<Result<Boolean>> swipeRefreshResult = new MutableLiveData<>();

    private LiveData<Integer> eventCount;

    private MediatorLiveData<Event<String>> _errorMessage = new MediatorLiveData<>();
    private LiveData<Event<String>> errorMessage;

    private MutableLiveData<Event<String>> _navigateToSessionAction = new MutableLiveData<>();
    private LiveData<Event<String>> navigateToSessionAction;

    private MediatorLiveData<Event<SnackbarMessage>> _snackBarMessage = new MediatorLiveData<>();
    private LiveData<Event<SnackbarMessage>> snackBarMessage;

    private LiveData<Boolean> showReservations;

    private MutableLiveData<Event<Unit>> _navigateToSignInDialogAction = new MutableLiveData<>();
    private LiveData<Event<Unit>> navigateToSignInDialogAction;

    private MutableLiveData<Event<Unit>> _navigateToSignOutDialogAction = new MutableLiveData<>();
    private LiveData<Event<Unit> navigateToSignOutDialogAction;

    private MutableLiveData<Result<Boolean>> scheduleUiHintsShownResult;

    private LiveData<Event<Boolean>> scheduleUiHintsShown;

    private boolean userHasInteracted = false;

    private LiveData<EventLocation> currentEvent;

    @Inject
    public ScheduleViewModel(
            LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase,
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
        this.starEventUseCase = starEventUseCase;
        this.snackbarMessageManager = snackbarMessageManager;
        this.getTimeZoneUseCase = getTimeZoneUseCase;
        this.refreshConferenceDataUseCase = refreshConferenceDataUseCase;
        this.loadSelectedFiltersUseCase = loadSelectedFiltersUseCase;
        this.saveSelectedFiltersUseCase = saveSelectedFiltersUseCase;
        this.analyticsHelper = analyticsHelper;

        this.isLoading = new MutableLiveData<>();
        this.swipeRefreshing = new MutableLiveData<>();
        this.labelsForDays = new MutableLiveData<>();
        this.timeZoneId = new MutableLiveData<>();
        this.eventCount = new MutableLiveData<>();
        this.errorMessage = new MutableLiveData<>();
        this.navigateToSessionAction = new MutableLiveData<>();
        this.snackBarMessage = new MutableLiveData<>();
        this.showReservations = new MutableLiveData<>();
        this.navigateToSignInDialogAction = new MutableLiveData<>();
        this.navigateToSignOutDialogAction = new MutableLiveData<>();
        this.scheduleUiHintsShown = new MutableLiveData<>();
        this.currentEvent = new MutableLiveData<>();

        initializeTimeZone();
    }

    public LiveData<SessionTimeData> getSessionTimeDataForDay(int day) {
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

    @Override
    public void openEventDetail(SessionId id) {
        _navigateToSessionAction.setValue(new Event<>(id));
    }

    @Override
    public void toggleFilter(EventFilter filter, boolean enabled) {
        boolean changed;
        if (filter instanceof MyEventsFilter) {
            changed = userSessionMatcher.setShowPinnedEventsOnly(enabled);
        } else if (filter instanceof TagFilter) {
            if (enabled) {
                changed = userSessionMatcher.add(((TagFilter) filter).getTag());
            } else {
                changed = userSessionMatcher.remove(((TagFilter) filter).getTag());
            }
        } else {
            changed = false;
        }
        if (changed) {
            filter.isChecked().set(enabled);
            saveSelectedFiltersUseCase.execute(userSessionMatcher);
            updateFilterStateObservables();
            refreshUserSessions();
            String filterName = (filter instanceof MyEventsFilter) ? "Starred & Reserved" : filter.getText();
            AnalyticsActions action = enabled ? AnalyticsActions.ENABLE : AnalyticsActions.DISABLE;
            analyticsHelper.logUiEvent("Filter changed: " + filterName, action);
        }
    }

    @Override
    public void clearFilters() {
        if (userSessionMatcher.clearAll()) {
            eventFilters.getValue().forEach(filter -> filter.isChecked().set(false));
            saveSelectedFiltersUseCase.execute(userSessionMatcher);
            updateFilterStateObservables();
            refreshUserSessions();
            analyticsHelper.logUiEvent("Clear filters", AnalyticsActions.CLICK);
        }
    }

    private void updateFilterStateObservables() {
        boolean hasAnyFilters = userSessionMatcher.hasAnyFilters();
        _hasAnyFilters.setValue(hasAnyFilters);
        _selectedFilters.setValue(cachedEventFilters.stream().filter(EventFilter::isChecked).collect(Collectors.toList()));
    }

    public void onSwipeRefresh() {
        refreshConferenceDataUseCase.execute(new Object(), swipeRefreshResult);
    }

    public void onSignInRequired() {
        _navigateToSignInDialogAction.setValue(new Event<>(new Unit()));
    }

    private void refreshUserSessions() {
        Timber.d("ViewModel refreshing user sessions");
        loadUserSessionsByDayUseCase.execute(new LoadUserSessionsByDayUseCaseParameters(userSessionMatcher, getUserId()));
    }

    @Override
    public void onStarClicked(UserSession userSession) {
        if (!isSignedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            _navigateToSignInDialogAction.setValue(new Event<>(new Unit()));
            return;
        }
        boolean newIsStarredState = !userSession.getUserEvent().isStarred();

        int stringResId = newIsStarredState ? R.string.event_starred : R.string.event_unstarred;

        if (newIsStarredState) {
            analyticsHelper.logUiEvent(userSession.getSession().getTitle(), AnalyticsActions.STARRED);
        }

        snackbarMessageManager.addMessage(new SnackbarMessage(stringResId, R.string.dont_show, UUID.randomUUID().toString()));

        getUserId().ifPresent(userId -> starEventUseCase.execute(new StarEventParameter(userId, userSession.getUserEvent().copy(isStarred: newIsStarredState))));
    }

    public void initializeTimeZone() {
        getTimeZoneUseCase.execute(new Unit(), preferConferenceTimeZoneResult);
    }
}

class SessionTimeData {
    private List<UserSession> list;
    private ZoneId timeZoneId;

    public SessionTimeData(List<UserSession> list, ZoneId timeZoneId) {
        this.list = list;
        this.timeZoneId = timeZoneId;
    }
}

interface ScheduleEventListener extends EventActions {
    void toggleFilter(EventFilter filter, boolean enabled);

    void clearFilters();
}