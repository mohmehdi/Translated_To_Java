package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.databinding.ObservableBoolean;
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

    private LiveData<Boolean> isLoading;

    private UserSessionMatcher userSessionMatcher;

    private TagFilterMatcher tagFilterMatcher = new TagFilterMatcher();

    private List<TagFilter> cachedTagFilters = new ArrayList<>();

    private LiveData<List<TagFilter>> tagFilters;
    private ObservableBoolean hasAnyFilters = new ObservableBoolean(false);
    private ObservableBoolean showPinnedEvents = new ObservableBoolean(false);

    private MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> loadSessionsResult;
    private MutableLiveData<Result<List<Block>>> loadAgendaResult = new MutableLiveData<>();
    private MutableLiveData<Result<List<Tag>>> loadTagsResult = new MutableLiveData<>();

    private LiveData<List<UserSession>> day1Sessions;
    private LiveData<List<UserSession>> day2Sessions;
    private LiveData<List<UserSession>> day3Sessions;

    private LiveData<List<Block>> agenda;

    private MediatorLiveData<Event<String>> _errorMessage = new MediatorLiveData<>();
    private LiveData<Event<String>> errorMessage;
    
    private MutableLiveData<Event<String>> _navigateToSessionAction = new MutableLiveData<>();
    private LiveData<Event<String>> navigateToSessionAction;

    private MediatorLiveData<Event<SnackbarMessage>> _snackBarMessage = new MediatorLiveData<>();
    private LiveData<Event<SnackbarMessage>> snackBarMessage;

    private MediatorLiveData<Integer> _profileContentDesc = new MediatorLiveData<>();
    private LiveData<Integer> profileContentDesc;

    private MutableLiveData<Event<Boolean>> _navigateToSignInDialogAction = new MutableLiveData<>();
    private LiveData<Event<Boolean>> navigateToSignInDialogAction;

    @Inject
    public ScheduleViewModel(LoadUserSessionsByDayUseCase loadUserSessionsByDayUseCase, LoadAgendaUseCase loadAgendaUseCase,
                             LoadTagsByCategoryUseCase loadTagsByCategoryUseCase, LoginViewModelPlugin loginViewModelPlugin,
                             StarEventUseCase starEventUseCase, ReservationActionUseCase reservationActionUseCase) {
        this.loadUserSessionsByDayUseCase = loadUserSessionsByDayUseCase;
        this.starEventUseCase = starEventUseCase;
        this.reservationActionUseCase = reservationActionUseCase;

        userSessionMatcher = tagFilterMatcher;

        loadSessionsResult = loadUserSessionsByDayUseCase.observe();

        loadAgendaUseCase(loadAgendaResult);
        loadTagsByCategoryUseCase(loadTagsResult);

        day1Sessions = loadSessionsResult.map(result -> ((result instanceof Result.Success) ? ((Result.Success<LoadUserSessionsByDayUseCaseResult>) result).getData().getUserSessionsPerDay().get(DAY_1) : Collections.emptyList()));
        day2Sessions = loadSessionsResult.map(result -> ((result instanceof Result.Success) ? ((Result.Success<LoadUserSessionsByDayUseCaseResult>) result).getData().getUserSessionsPerDay().get(DAY_2) : Collections.emptyList()));
        day3Sessions = loadSessionsResult.map(result -> ((result instanceof Result.Success) ? ((Result.Success<LoadUserSessionsByDayUseCaseResult>) result).getData().getUserSessionsPerDay().get(DAY_3) : Collections.emptyList()));

        isLoading = loadSessionsResult.map(result -> result == Result.Loading);

        _errorMessage.addSource(loadSessionsResult, result -> {
            if (result instanceof Result.Error) {
                _errorMessage.setValue(new Event<>(result.getException().getMessage() != null ? result.getException().getMessage() : "Error"));
            }
        });
        _errorMessage.addSource(loadTagsResult, result -> {
            if (result instanceof Result.Error) {
                _errorMessage.setValue(new Event<>(result.getException().getMessage() != null ? result.getException().getMessage() : "Error"));
            }
        });

        agenda = loadAgendaResult.map(result -> (result instanceof Result.Success) ? ((Result.Success<List<Block>>) result).getData() : Collections.emptyList());

        tagFilters = loadTagsResult.map(result -> {
            if (result instanceof Success) {
                cachedTagFilters = processTags(((Success<List<Tag>>) result).getData());
            }
            return cachedTagFilters;
        });

        _profileContentDesc.addSource(currentFirebaseUser, user -> {
            _profileContentDesc.setValue(getProfileContentDescription(user));
        });

        _snackBarMessage.addSource(reservationActionUseCase.observe(), result -> {
            if (result instanceof Result.Error) {
                _snackBarMessage.postValue(new Event<>(new SnackbarMessage(R.string.reservation_error)));
            }
        });

        _snackBarMessage.addSource(starEventUseCase.observe(), result -> {
            if (result instanceof Result.Error) {
                _snackBarMessage.postValue(new Event<>(new SnackbarMessage(R.string.event_star_error)));
            }
        });

        _snackBarMessage.addSource(loadUserSessionsByDayUseCase.observe(), result -> {
            String message = switch (result) {
                case Result.Success success -> switch (success.getData().getUserMessage()) {
                    case CHANGES_IN_WAITLIST -> R.string.waitlist_new;
                    case CHANGES_IN_RESERVATIONS -> R.string.reservation_new;
                    default -> null;
                };
                case default -> null;
            };

            if (message != null) {
                _snackBarMessage.postValue(new Event<>(new SnackbarMessage(message, R.string.got_it, true)));
            }
        });

        loadSessionsResult.addSource(currentFirebaseUser, user -> {
            Timber.d("Loading user session with user " + (((Result.Success) user).getData().getUid()));
            refreshUserSessions();
        });
    }

    private List<TagFilter> processTags(List<Tag> tags) {
        tagFilterMatcher.removeOrphanedTags(tags);
        return tags.stream().map(tag -> new TagFilter(tag, tagFilterMatcher.contains(tag))).collect(Collectors.toList());
    }

    public LiveData<List<UserSession>> getSessionsForDay(ConferenceDay day) {
        return switch (day) {
            case DAY_1 -> day1Sessions;
            case DAY_2 -> day2Sessions;
            case DAY_3 -> day3Sessions;
        };
    }

    @Override
    public void openSessionDetail(String id) {
        _navigateToSessionAction.setValue(new Event<>(id));
    }

    @Override
    public void toggleFilter(TagFilter filter, boolean enabled) {
        if (enabled && tagFilterMatcher.add(filter.getTag())) {
            filter.getIsChecked().set(true);
            hasAnyFilters.set(true);
            refreshUserSessions();
        } else if (!enabled && tagFilterMatcher.remove(filter.getTag())) {
            filter.getIsChecked().set(false);
            hasAnyFilters.set(!tagFilterMatcher.isEmpty());
            refreshUserSessions();
        }
    }

    @Override
    public void clearFilters() {
        if (tagFilterMatcher.clearAll()) {
            tagFilters.getValue().forEach(tagFilter -> tagFilter.getIsChecked().set(false));
            hasAnyFilters.set(false);
            refreshUserSessions();
        }
    }

    @Override
    public void togglePinnedEvents(boolean pinned) {
        if (showPinnedEvents.get() != pinned) {
            showPinnedEvents.set(pinned);
            userSessionMatcher = pinned ? PinnedEventMatcher : tagFilterMatcher;
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
        return (userResult instanceof Success && ((Success<AuthenticatedUserInfo>) userResult).getData().isLoggedIn()) ? R.string.a11y_logout : R.string.a11y_login;
    }

    private void refreshUserSessions() {
        Timber.d("ViewModel refreshing user sessions");
        loadUserSessionsByDayUseCase.execute(userSessionMatcher, getUserId() != null ? getUserId() : "tempUser");
    }

    @Override
    public void onStarClicked(Session session, UserEvent userEvent) {
        if (!isLoggedIn()) {
            Timber.d("Showing Sign-in dialog after star click");
            _navigateToSignInDialogAction.setValue(new Event<>(true));
            return;
        }
        boolean newIsStarredState = userEvent != null && userEvent.isStarred();

        SnackbarMessage snackbarMessage = new SnackbarMessage(newIsStarredState ? R.string.event_starred : R.string.event_unstarred);
        _snackBarMessage.postValue(new Event<>(snackbarMessage));

        getUserId().ifPresent(userId -> starEventUseCase.execute(new StarEventParameter(userId, session, newIsStarredState)));
    }

    @Override
    public void onReservationClicked(Session session, UserEvent userEvent) {
        if (!isLoggedIn()) {
            Timber.d("You need to sign in to reserve an event");
            _errorMessage.setValue(new Event<>("Sign in to reserve events"));
            return;
        }
        if (!isRegistered()) {
            Timber.d("You need to be an attendee to reserve an event");
            _errorMessage.setValue(new Event<>("You're not an attendee"));
            return;
        }

        ReservationRequestAction action = (userEvent != null && (userEvent.isReserved() || userEvent.isWaitlisted() || userEvent.getReservationRequested() == LastReservationRequested.RESERVATION)) ? ReservationRequestAction.CANCEL : ReservationRequestAction.REQUEST;

        SnackbarMessage snackbarMessage = switch (action) {
            case REQUEST -> new SnackbarMessage(R.string.reservation_request_succeeded, R.string.got_it);
            case CANCEL -> new SnackbarMessage(R.string.reservation_cancel_succeeded, R.string.got_it);
        };
        _snackBarMessage.postValue(new Event<>(snackbarMessage));

        getUserId().ifPresent(userId -> reservationActionUseCase.execute(new ReservationRequestParameters(userId, session, action)));
    }

    private Optional<String> getUserId() {
        Result<AuthenticatedUserInfo> user = currentFirebaseUser.getValue();
        return (user instanceof Success) ? Optional.of(((Success<AuthenticatedUserInfo>) user).getData().getUid()) : Optional.empty();
    }
}

class TagFilter {
    private Tag tag;
    public ObservableBoolean isChecked;

    public TagFilter(Tag tag, boolean isChecked) {
        this.tag = tag;
        this.isChecked = new ObservableBoolean(isChecked);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TagFilter && ((TagFilter) o).tag.equals(tag));
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    public boolean isUiContentEqual(TagFilter other) {
        return tag.isUiContentEqual(other.tag) && isChecked.hasSameValue(other.isChecked);
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
    private T content;
    private boolean hasBeenHandled;

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