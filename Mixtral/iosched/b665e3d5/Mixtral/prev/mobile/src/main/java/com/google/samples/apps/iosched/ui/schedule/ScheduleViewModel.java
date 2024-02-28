package com.google.samples.apps.iosched.ui.schedule;

import android.os.ZoneId;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.model.SessionId;
import com.google.samples.apps.iosched.model.userdata.UserSession;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsActions;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.domain.RefreshConferenceDataUseCase;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase.LoadAgendaUseCaseCallback;
import com.google.samples.apps.iosched.shared.domain.invoke;
import com.google.samples.apps.iosched.shared.domain.prefs.LoadSelectedFiltersUseCase;
import com.google.samples.apps.iosched.shared.domain.prefs.SaveSelectedFiltersUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.EventLocation;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCase.LoadUserSessionsByDayUseCaseCallback;
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
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.MyEventsFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.EventFilter.TagFilter;
import com.google.samples.apps.iosched.ui.schedule.filters.LoadEventFiltersUseCase;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActions;
import com.google.samples.apps.iosched.ui.sessioncommon.stringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener, SignInViewModelDelegate {
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
private final UserSessionMatcher userSessionMatcher;

private final MutableLiveData<Result> loadSessionsResult =
new MutableLiveData<>();
private final MutableLiveData<Result<List>> loadAgendaResult = new MutableLiveData<>();
private final MutableLiveData<Result<List>> loadEventFiltersResult =
new MutableLiveData<>();
private final MediatorLiveData<Result> preferConferenceTimeZoneResult =
new MediatorLiveData<>();
private final MediatorLiveData<List> labelsForDays = new MediatorLiveData<>();
private final MutableLiveData timeZoneId = new MutableLiveData<>();
private final MediatorLiveData sessionTimeDataDay1 = new MediatorLiveData<>();
private final MediatorLiveData sessionTimeDataDay2 = new MediatorLiveData<>();
private final MediatorLiveData sessionTimeDataDay3 = new MediatorLiveData<>();
private final MutableLiveData<List> cachedEventFilters = new MutableLiveData<>();
private final MutableLiveData<List> selectedFilters = new MutableLiveData<>();
private final MutableLiveData hasAnyFilters = new MutableLiveData<>();
private final MutableLiveData isAgendaPage = new MutableLiveData<>();
private final MutableLiveData transientUiState = new MutableLiveData<>();
private final MediatorLiveData<Event> errorMessage = new MediatorLiveData<>();
private final MediatorLiveData<Event> snackBarMessage = new MediatorLiveData<>();
private final MutableLiveData<Event> navigateToSessionAction = new MutableLiveData<>();
private final MutableLiveData userHasInteracted = new MutableLiveData<>();
private final MutableLiveData<Event> scheduleUiHintsShown = new MutableLiveData<>();
private final MutableLiveData<EventLocation?> currentEvent = new MutableLiveData<>();
private LiveData<List<Block>> agenda;
@Inject
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
}



    public LiveData<LoadUserSessionsByDayResult> getSessionTimeDataForDay(int day) {
        LiveData<LoadUserSessionsByDayResult> sessionTimeData;
        switch (day) {
            case 0:
                sessionTimeData = sessionTimeDataDay1;
                break;
            case 1:
                sessionTimeData = sessionTimeDataDay2;
                break;
            case 2:
                sessionTimeData = sessionTimeDataDay3;
                break;
            default:
                Exception exception = new Exception("Invalid day: " + day);
                Timber.e(exception);
                throw exception;
        }
        return sessionTimeData;
    }

    public void openEventDetail(SessionId id) {
        navigateToSessionAction.setValue(id);
    }

    public void toggleFilter(EventFilter filter, boolean enabled) {
        boolean changed = false;
        if (filter instanceof MyEventsFilter) {
            changed = userSessionMatcher.getValue().setShowPinnedEventsOnly(enabled);
        } else if (filter instanceof TagFilter) {
            if (enabled) {
                userSessionMatcher.getValue().add(filter.getTag());
            } else {
                userSessionMatcher.getValue().remove(filter.getTag());
            }
            changed = true;
        }

        if (changed) {
            filter.isChecked.set(enabled);
            saveSelectedFiltersUseCase(userSessionMatcher.getValue());
            updateFilterStateObservables();
            refreshUserSessions();

            String filterName = (filter instanceof MyEventsFilter) ? "Starred & Reserved" : filter.getText();
            String action = enabled ? AnalyticsActions.ENABLE : AnalyticsActions.DISABLE;
            analyticsHelper.logUiEvent("Filter changed: " + filterName, action);
        }
    }

    public void clearFilters() {
        if (userSessionMatcher.getValue().clearAll()) {
            for (EventFilter eventFilter : eventFilters.getValue()) {
                eventFilter.isChecked.set(false);
            }
            saveSelectedFiltersUseCase(userSessionMatcher.getValue());
            updateFilterStateObservables();
            refreshUserSessions();

            analyticsHelper.logUiEvent("Clear filters", AnalyticsActions.CLICK);
        }
    }

    private void updateFilterStateObservables() {
        boolean hasAnyFilters = userSessionMatcher.getValue().hasAnyFilters();
        this.hasAnyFilters.setValue(hasAnyFilters);
        List<EventFilter> selectedFilters = cachedEventFilters.stream()
                .filter(eventFilter -> eventFilter.isChecked.get())
                .collect(Collectors.toList());
        this.selectedFilters.setValue(selectedFilters);
        setTransientUiState(transientUiStateVar.getValue().copy(hasAnyFilters = hasAnyFilters));
    }

    public void onSwipeRefresh() {
        refreshConferenceDataUseCase(null, swipeRefreshResult);
    }

    public void onSignInRequired() {
        navigateToSignInDialogAction.setValue(null);
    }

    private void refreshUserSessions() {
        Timber.d("ViewModel refreshing user sessions");
        loadUserSessionsByDayUseCase.execute(
                new LoadUserSessionsByDayUseCaseParameters(userSessionMatcher.getValue(), getUserId())
        );
    }

    public void onStarClicked(UserSession userSession) {
        if (!isSignedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            navigateToSignInDialogAction.setValue(null);
            return;
        }
        boolean newIsStarredState = !userSession.getUserEvent().isStarred();

        int stringResId;
        if (newIsStarredState) {
            stringResId = R.string.event_starred;
            analyticsHelper.logUiEvent(userSession.getSession().getTitle(), AnalyticsActions.STARRED);
        } else {
            stringResId = R.string.event_unstarred;
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
        if (this.isAgendaPage.getValue() != isAgendaPage) {
            this.isAgendaPage.setValue(isAgendaPage);
            setTransientUiState(transientUiStateVar.getValue().copy(isAgendaPage = isAgendaPage));
        }
    }

    private void setTransientUiState(@NonNull TransientUiState state) {
        transientUiStateVar.setValue(state);
    }

    public void initializeTimeZone() {
        getTimeZoneUseCase(null, preferConferenceTimeZoneResult);
    }
}

public interface ScheduleEventListener extends EventActions {

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

}

public class TransientUiState {
    private boolean isAgendaPage;
    private boolean hasAnyFilters;

    public TransientUiState(boolean isAgendaPage, boolean hasAnyFilters) {
        this.isAgendaPage = isAgendaPage;
        this.hasAnyFilters = hasAnyFilters;
    }


}