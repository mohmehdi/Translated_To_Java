

package com.google.samples.apps.iosched.shared.data.userevent;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.google.samples.apps.iosched.shared.data.session.SessionRepository;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.domain.sessions.LoadUserSessionsByDayUseCaseResult;
import com.google.samples.apps.iosched.shared.domain.users.ReservationRequestAction;
import com.google.samples.apps.iosched.shared.domain.users.StarUpdatedStatus;
import com.google.samples.apps.iosched.shared.firestore.entity.LastReservationRequested;
import com.google.samples.apps.iosched.shared.firestore.entity.UserEvent;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultSessionAndUserEventRepository implements SessionAndUserEventRepository {

    private final UserEventDataSource userEventDataSource;
    private final SessionRepository sessionRepository;
    private final MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> result;

    @Inject
    public DefaultSessionAndUserEventRepository(
            UserEventDataSource userEventDataSource,
            SessionRepository sessionRepository) {
        this.userEventDataSource = userEventDataSource;
        this.sessionRepository = sessionRepository;
        this.result = new MediatorLiveData<>();
    }

    @NonNull
    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @NonNull String userId) {

        LiveData<Result<UserEventsResult>> observableUserEvents =
                userEventDataSource.getObservableUserEvents(userId);
        result.removeSource(observableUserEvents);
        result.addSource(observableUserEvents, userEvents -> {
            if (userEvents == null) return;

            DefaultScheduler.execute(() -> {
                try {
                    List<Session> allSessions = sessionRepository.getSessions();

                    result.postValue(Result.Success(
                            new LoadUserSessionsByDayUseCaseResult(
                                    mapUserDataAndSessions(
                                            userEvents.getDataSynced(),
                                            userEvents.getUserEvents(),
                                            allSessions),
                                    userEvents.getUserEventsMessage())));

                } catch (Exception e) {
                    result.postValue(Result.Error(e));
                }
            });
        });
        return result;
    }

    @NonNull
    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            @NonNull String userId, @NonNull Session session, boolean isStarred) {
        return userEventDataSource.updateStarred(userId, session, isStarred);
    }

    @NonNull
    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(
            @NonNull String userId, @NonNull Session session,
            @NonNull ReservationRequestAction action) {
        return userEventDataSource.requestReservation(userId, session, action);
    }
@WorkerThread
private Map<ConferenceDay, List<UserSession>> mapUserDataAndSessions(UserEventsResult userData, List<Session> allSessions) {
    Pair<Boolean, List<UserEvent>, String> userDataPair = userData;
    Map<String, UserEvent> eventIdToUserEvent = new HashMap<>();
    for (UserEvent userEvent : userDataPair.getSecond()) {
        eventIdToUserEvent.put(userEvent.id, userEvent);
    }

    List<UserSession> allUserSessions = new ArrayList<>();
    for (Session session : allSessions) {
        UserEvent userEvent = eventIdToUserEvent.get(session.id);
        allUserSessions.add(new UserSession(session, userEvent));
    }

    Set<String> alreadyPendingWriteIds = new HashSet<>();
    if (result.getValue() instanceof Result.Success) {
        boolean allDataSynced = userDataPair.getFirst();
        if (!allDataSynced) {
            List<Map<ConferenceDay, List<UserSessionPerDay>>> data = ((Result.Success<Map<ConferenceDay, List<UserSessionPerDay>>>) result.getValue()).getData().getUserSessionsPerDay();
            for (Map<ConferenceDay, List<UserSessionPerDay>> map : data) {
                for (List<UserSessionPerDay> list : map.values()) {
                    for (UserSessionPerDay userSessionPerDay : list) {
                        UserEvent userEvent = userSessionPerDay.getUserEvent();
                        if (userEvent != null && userEvent.hasPendingWrite()) {
                            alreadyPendingWriteIds.add(userEvent.getId());
                        }
                    }
                }
            }
        }
    }

    Map<ConferenceDay, List<UserSession>> map = new EnumMap<>(ConferenceDay.class);
    for (ConferenceDay day : ConferenceDay.values()) {
        List<UserSession> userSessions = new ArrayList<>();
        for (UserSession userSession : allUserSessions) {
            if (day.contains(userSession.session)) {
                UserEvent userEvent = userSession.userEvent;
                if (userEvent != null) {
                    if (alreadyPendingWriteIds.contains(userEvent.getId())) {
                        userEvent.setHasPendingWrite(true);
                    }
                    userSessions.add(new UserSession(userSession.session, userEvent));
                }
            }
        }
        userSessions.sort(Comparator.comparingLong(session -> session.session.startTime));
        map.put(day, userSessions);
    }
    return map;
}

}

interface SessionAndUserEventRepository {

    LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            String userId);

    LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            String userId, Session session, boolean isStarred);

    LiveData<Result<LastReservationRequested>> changeReservation(
            String userId, Session session, ReservationRequestAction action);
}