

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModel;
import android.databinding.ObservableBoolean;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUserInfo;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.invoke;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCase;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult;
import com.google.samples.apps.iosched.shared.domain.sessions.UserEventsMessage;
import com.google.samples.apps.iosched.shared.domain.tags.LoadTagsByCategoryUseCase;
import com.google.samples.apps.iosched.shared.domain.users.ReservationActionUseCase;
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestAction;
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestParameters;
import com.google.samples.apps.iosched.shared.domain.users.StarEventParameter;
import com.google.samples.apps.iosched.shared.domain.users.StarEventUseCase;
import com.google.samples.apps.iosched.shared.domain.users.StarUpdatedStatus;
import com.google.samples.apps.iosched.shared.firestore.entity.LastReservationRequested;
import com.google.samples.apps.iosched.shared.firestore.entity.UserEvent;
import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.result.Result.Success;
import com.google.samples.apps.iosched.shared.schedule.PinnedEventMatcher;
import com.google.samples.apps.iosched.shared.schedule.TagFilterMatcher;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_1;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_2;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_3;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.login.LoginViewModelPlugin;
import com.google.samples.apps.iosched.util.hasSameValue;
import timber.log.Timber;
import javax.inject.Inject;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener, LoginViewModelPlugin {

    private final LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase;
    private final LoadAgendaUseCase loadAgendaUseCase;
    private final LoadTagsByCategoryUseCase loadTagsByCategoryUseCase;
    private final LoginViewModelPlugin loginViewModelPlugin;
    private final StarEventUseCase starEventUseCase;
    private final ReservationActionUseCase reservationActionUseCase;

    public ObservableBoolean isLoading = new ObservableBoolean(false);
    private UserSessionMatcher userSessionMatcher;
    private final TagFilterMatcher tagFilterMatcher = new TagFilterMatcher();
    private List<TagFilter> cachedTagFilters = new ArrayList<>();
    public LiveData<List<TagFilter>> tagFilters;
    public ObservableBoolean hasAnyFilters = new ObservableBoolean(false);
    public ObservableBoolean showPinnedEvents = new ObservableBoolean(false);
    public LiveData<List<UserSession>> day1Sessions;
    public LiveData<List<UserSession>> day2Sessions;
    public LiveData<List<UserSession>> day3Sessions;
    public LiveData<List<Block>> agenda;
    private final MediatorLiveData<Event<String>> _errorMessage = new MediatorLiveData<>();
    public LiveData<Event<String>> errorMessage;
    private final MediatorLiveData<Event<SnackbarMessage>> _snackBarMessage = new MediatorLiveData<>();
    public LiveData<Event<SnackbarMessage>> snackBarMessage;
    public LiveData<Integer> profileContentDesc;
    private final MediatorLiveData<Event<Boolean>> _navigateToSignInDialogAction = new MediatorLiveData<>();
    public LiveData<Event<Boolean>> navigateToSignInDialogAction;

    @Inject
    public ScheduleViewModel(
            LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase,
            LoadAgendaUseCase loadAgendaUseCase,
            LoadTagsByCategoryUseCase loadTagsByCategoryUseCase,
            LoginViewModelPlugin loginViewModelPlugin,
            StarEventUseCase starEventUseCase,
            ReservationActionUseCase reservationActionUseCase) {
        this.loadUserSessionsByDayUseCase = loadUserSessionsByDayUseCase;
        this.loadAgendaUseCase = loadAgendaUseCase;
        this.loadTagsByCategoryUseCase = loadTagsByCategoryUseCase;
        this.loginViewModelPlugin = loginViewModelPlugin;
        this.starEventUseCase = starEventUseCase;
        this.reservationActionUseCase = reservationActionUseCase;

        userSessionMatcher = tagFilterMatcher;

        MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> loadSessionsResult = loadUserSessionsByDayUseCase.observe();
        loadSessionsResult.addSource(currentFirebaseUser, new Observer<Result<AuthenticatedUserInfo>>() {

        });

        loadAgendaUseCase.execute(loadAgendaResult);
        loadTagsByCategoryUseCase.execute(loadTagsResult);

        day1Sessions = Transformations.map(loadSessionsResult, input -> {
            if (input instanceof Success) {
                return ((Success<LoadUserSessionsByDayUseCaseResult>) input).getData().getUserSessionsPerDay().get(DAY_1);
            } else {
                return new ArrayList<>();
            }
        });
        day2Sessions = Transformations.map(loadSessionsResult, input -> {
            if (input instanceof Success) {
                return ((Success<LoadUserSessionsByDayUseCaseResult>) input).getData().getUserSessionsPerDay().get(DAY_2);
            } else {
                return new ArrayList<>();
            }
        });
        day3Sessions = Transformations.map(loadSessionsResult, input -> {
            if (input instanceof Success) {
                return ((Success<LoadUserSessionsByDayUseCaseResult>) input).getData().getUserSessionsPerDay().get(DAY_3);
            } else {
                return new ArrayList<>();
            }
        });

        isLoading = Transformations.map(loadSessionsResult, input -> input == Result.Loading);

        _errorMessage.addSource(loadSessionsResult, new Observer<Result<LoadUserSessionsByDayUseCaseResult>>() {

        });
        _errorMessage.addSource(loadTagsResult, new Observer<Result<List<Tag>>>() {

        });

        agenda = loadAgendaResult.map(input -> {
            if (input instanceof Success) {
                return ((Success<List<Block>>) input).getData();
            } else {
                return new ArrayList<>();
            }
        });

        tagFilters = loadTagsResult.map(input -> {
            if (input instanceof Success) {
                cachedTagFilters = processTags(input.getData());
            }
            return cachedTagFilters;
        });

        profileContentDesc = Transformations.map(currentFirebaseUser, this::getProfileContentDescription);

        _snackBarMessage.addSource(reservationActionUseCase.observe(), new Observer<Result<StarUpdatedStatus>>() {

        });

        _snackBarMessage.addSource(starEventUseCase.observe(), new Observer<Result<StarUpdatedStatus>>() {

        });

        _snackBarMessage.addSource(loadUserSessionsByDayUseCase.observe(), new Observer<Result<LoadUserSessionsByDayUseCaseResult>>() {

        });
    }

    private List<TagFilter> processTags(List<Tag> tags) {
        tagFilterMatcher.removeOrphanedTags(tags);

        return tags.stream().map(tag -> new TagFilter(tag, tagFilterMatcher.contains(tag))).collect(Collectors.toList());
    }

    public LiveData<List<UserSession>> getSessionsForDay(ConferenceDay day) {
        switch (day) {
            case DAY_1:
                return day1Sessions;
            case DAY_2:
                return day2Sessions;
            case DAY_3:
                return day3Sessions;
            default:
                return null;
        }
    }

    @Override
    public void openSessionDetail(String id) {
        _navigateToSessionAction.postValue(new Event<>(id));
    }

    @Override
    public void toggleFilter(TagFilter filter, boolean enabled) {
        if (enabled && tagFilterMatcher.add(filter.tag)) {
            filter.isChecked.set(true);
            hasAnyFilters.set(true);
            refreshUserSessions();
        } else if (!enabled && tagFilterMatcher.remove(filter.tag)) {
            filter.isChecked.set(false);
            hasAnyFilters.set(!tagFilterMatcher.isEmpty());
            refreshUserSessions();
        }
    }

    @Override
    public void clearFilters() {
        if (tagFilterMatcher.clearAll()) {
            for (TagFilter filter : cachedTagFilters) {
                filter.isChecked.set(false);
            }
            hasAnyFilters.set(false);
            refreshUserSessions();
        }
    }

    @Override
    public void togglePinnedEvents(boolean pinned) {
        if (showPinnedEvents.get() != pinned) {
            showPinnedEvents.set(pinned);
            userSessionMatcher = pinned ? new PinnedEventMatcher() : tagFilterMatcher;
            refreshUserSessions();
        }
    }

    public void onProfileClicked() {
        if (isLoggedIn()) {
            emitLogoutRequest();
        } else {
            emitLoginRequest();
        }
    }

    @StringRes
    private int getProfileContentDescription(Result<AuthenticatedUserInfo> userResult) {
        if (userResult instanceof Success && userResult.getData().isLoggedIn()) {
            return R.string.a11y_logout;
        } else {
            return R.string.a11y_login;
        }
    }

    private void refreshUserSessions() {
        String uid = ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().getUid();
        loadUserSessionsByDayUseCase.execute(userSessionMatcher, uid);
    }

    @Override
    public void onStarClicked(Session session, UserEvent userEvent) {
        if (!isLoggedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            _navigateToSignInDialogAction.postValue(new Event<>(true));
            return;
        }
        boolean newIsStarredState = !userEvent.isStarred();

        SnackbarMessage snackbarMessage = newSnackbarMessage(newIsStarredState, newIsStarredState ? R.string.event_starred : R.string.event_unstarred);
        _snackBarMessage.postValue(new Event<>(snackbarMessage));

        String uid = ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().getUid();
        starEventUseCase.execute(new StarEventParameter(uid, session, newIsStarredState));
    }

    @Override
    public void onReservationClicked(Session session, UserEvent userEvent) {
        if (!isLoggedIn()) {
            Timber.d("You need to sign in to star an event");
            _errorMessage.postValue(new Event<>("Sign in to star events"));
            return;
        }

        ReservationRequestAction action;
        if (userEvent.isReserved() || userEvent.isWaitlisted() || userEvent.reservationRequested == LastReservationRequested.RESERVATION) {
            action = ReservationRequestAction.CANCEL;
        } else {
            action = ReservationRequestAction.REQUEST;
        }

        SnackbarMessage snackbarMessage = newSnackbarMessage(action == ReservationRequestAction.REQUEST, action == ReservationRequestAction.REQUEST ? R.string.reservation_request_succeeded : R.string.reservation_cancel_succeeded);
        _snackBarMessage.postValue(new Event<>(snackbarMessage));

        String uid = ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().getUid();
        reservationActionUseCase.execute(new ReservationRequestParameters(uid, session, action));
    }


    public static class TagFilter {
        private final Tag tag;
        private final ObservableBoolean isChecked;

        public TagFilter(Tag tag, boolean isChecked) {
            this.tag = tag;
            this.isChecked = new ObservableBoolean(isChecked);
        }

        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TagFilter)) return false;
            TagFilter otherFilter = (TagFilter) other;
            return tag.equals(otherFilter.tag);
        }

        public int hashCode() {
            return tag.hashCode();
        }

        public boolean isUiContentEqual(TagFilter other) {
            return tag.isUiContentEqual(other.tag) && isChecked.hasSameValue(other.isChecked);
        }
    }

    @Override
    public void openSessionDetail(String id) {
        _navigateToSessionAction.postValue(new Event<>(id));
    }

    @Override
    public void toggleFilter(TagFilter filter, boolean enabled) {
        if (enabled && tagFilterMatcher.add(filter.tag)) {
            filter.isChecked.set(true);
            hasAnyFilters.set(true);
            refreshUserSessions();
        } else if (!enabled && tagFilterMatcher.remove(filter.tag)) {
            filter.isChecked.set(false);
            hasAnyFilters.set(!tagFilterMatcher.isEmpty());
            refreshUserSessions();
        }
    }

    @Override
    public void clearFilters() {
        if (tagFilterMatcher.clearAll()) {
            for (TagFilter filter : cachedTagFilters) {
                filter.isChecked.set(false);
            }
            hasAnyFilters.set(false);
            refreshUserSessions();
        }
    }

    @Override
    public void togglePinnedEvents(boolean pinned) {
        if (showPinnedEvents.get() != pinned) {
            showPinnedEvents.set(pinned);
            userSessionMatcher = pinned ? new PinnedEventMatcher() : tagFilterMatcher;
            refreshUserSessions();
        }
    }

    @Override
    public void onStarClicked(Session session, UserEvent userEvent) {
        if (!isLoggedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            _navigateToSignInDialogAction.postValue(new Event<>(true));
            return;
        }
        boolean newIsStarredState = !userEvent.isStarred();

        SnackbarMessage snackbarMessage = newSnackbarMessage(newIsStarredState, newIsStarredState ? R.string.event_starred : R.string.event_unstarred);
        _snackBarMessage.postValue(new Event<>(snackbarMessage));

        String uid = ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().getUid();
        starEventUseCase.execute(new StarEventParameter(uid, session, newIsStarredState));
    }

    @Override
    public void onReservationClicked(Session session, UserEvent userEvent) {
        if (!isLoggedIn()) {
            Timber.d("You need to sign in to star an event");
            _errorMessage.postValue(new Event<>("Sign in to star events"));
            return;
        }

        ReservationRequestAction action;
        if (userEvent.isReserved() || userEvent.isWaitlisted() || userEvent.reservationRequested == LastReservationRequested.RESERVATION) {
            action = ReservationRequestAction.CANCEL;
        } else {
            action = ReservationRequestAction.REQUEST;
        }

        SnackbarMessage snackbarMessage = newSnackbarMessage(action == ReservationRequestAction.REQUEST, action == ReservationRequestAction.REQUEST ? R.string.reservation_request_succeeded : R.string.reservation_cancel_succeeded);
        _snackBarMessage.postValue(new Event<>(snackbarMessage));

        String uid = ((Success<AuthenticatedUserInfo>) currentFirebaseUser.getValue()).getData().getUid();
        reservationActionUseCase.execute(new ReservationRequestParameters(uid, session, action));
    }


}

interface ScheduleEventListener {
    void openSessionDetail(String id);

    void toggleFilter(TagFilter filter, boolean enabled);

    void clearFilters();

    void togglePinnedEvents(boolean pinned);

    void onStarClicked(Session session, UserEvent userEvent);

    void onReservationClicked(Session session, UserEvent userEvent);
}

class Event<T> {
    private final T content;
    private boolean hasBeenHandled;

    public Event(T content) {
        this.content = content;
    }

    public Event(T content, boolean hasBeenHandled) {
        this.content = content;
        this.hasBeenHandled = hasBeenHandled;
    }

    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    public T peekContent() {
        return content;
    }
}