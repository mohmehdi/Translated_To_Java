

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultSessionAndUserEventRepository implements SessionAndUserEventRepository {

    private final UserEventDataSource userEventDataSource;
    private final SessionRepository sessionRepository;
    private final MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> result;

    @Inject
    public DefaultSessionAndUserEventRepository(
            @NonNull UserEventDataSource userEventDataSource,
            @NonNull SessionRepository sessionRepository) {
        this.userEventDataSource = userEventDataSource;
        this.sessionRepository = sessionRepository;
        this.result = new MediatorLiveData<>();
    }

    @NonNull
    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @Nullable String userId) {

        if (userId == null) {
            LoadUserSessionsByDayUseCaseResult loadUserSessionsByDayUseCaseResult =
                    mapUserDataAndSessions(null, sessionRepository.getSessions());
            result.postValue(Result.success(loadUserSessionsByDayUseCaseResult));
            return result;
        }

        LiveData<Result<UserEventsResult>> observableUserEvents =
                userEventDataSource.getObservableUserEvents(userId);

        result.removeSource(observableUserEvents);
        result.addSource(observableUserEvents, userEvents -> {
            if (userEvents == null) {
                return;
            }

            DefaultScheduler.execute(() -> {
                try {
                    List<Session> allSessions = sessionRepository.getSessions();

                    LoadUserSessionsByDayUseCaseResult loadUserSessionsByDayUseCaseResult =
                            mapUserDataAndSessions(userEvents, allSessions);

                    result.postValue(Result.success(loadUserSessionsByDayUseCaseResult));

                } catch (Exception e) {
                    result.postValue(Result.error(e));
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

    @NonNull
    @WorkerThread
    private LoadUserSessionsByDayUseCaseResult mapUserDataAndSessions(
            @Nullable UserEventsResult userData, @NonNull List<Session> allSessions) {

        if (userData == null) {

            Map<ConferenceDay, List<UserSession>> userSessionsPerDay =
                    Collections.singletonMap(ConferenceDay.values()[0], allSessions.stream()
                            .filter(day -> day.getConferenceDay() == ConferenceDay.values()[0])
                            .map(session -> new UserSession(session, null))
                            .collect(Collectors.toList()));

            return new LoadUserSessionsByDayUseCaseResult(userSessionsPerDay, null);
        }

        boolean allDataSynced = userData.isAllDataSynced();
        UserEventsResult.Data data = userData.getData();
        List<UserEvent> userEvents = data.getUserEvents();

        Map<String, UserEvent> eventIdToUserEvent = new HashMap<>();
        for (UserEvent userEvent : userEvents) {
            eventIdToUserEvent.put(userEvent.getId(), userEvent);
        }

        List<UserSession> allUserSessions = allSessions.stream()
                .map(session -> new UserSession(session, eventIdToUserEvent.get(session.getId())))
                .collect(Collectors.toList());

        Set<String> alreadyPendingWriteIds = new HashSet<>();
        if (result.getValue() instanceof Result.Success) {
            if (allDataSynced) {
                alreadyPendingWriteIds.clear();
            } else {
                LoadUserSessionsByDayUseCaseResult loadUserSessionsByDayUseCaseResult =
                        (LoadUserSessionsByDayUseCaseResult) ((Result.Success) result.getValue()).getData();
                alreadyPendingWriteIds = loadUserSessionsByDayUseCaseResult.getUserSessionsPerDay().values()
                        .stream()
                        .flatMap(Collection::stream)
                        .filter(userSession -> userSession.getUserEvent() != null &&
                                userSession.getUserEvent().isHasPendingWrite())
                        .map(userSession -> userSession.getUserEvent().getId())
                        .collect(Collectors.toSet());
            }
        }

        Map<ConferenceDay, List<UserSession>> userSessionsPerDay = new LinkedHashMap<>();
        for (ConferenceDay day : ConferenceDay.values()) {
            List<UserSession> userSessions = allUserSessions.stream()
                    .filter(userSession -> day.contains(userSession.getSession()))
                    .map(userSession -> {
                        UserEvent userEvent = userSession.getUserEvent();
                        if (alreadyPendingWriteIds.contains(userEvent.getId())) {
                            userEvent.setHasPendingWrite(true);
                        }
                        return userSession;
                    })
                    .sorted((o1, o2) -> o1.getSession().getStartTime().compareTo(o2.getSession().getStartTime()))
                    .collect(Collectors.toList());
            userSessionsPerDay.put(day, userSessions);
        }

        return new LoadUserSessionsByDayUseCaseResult(userSessionsPerDay, data.getUserEventsMessage());
    }
}

interface SessionAndUserEventRepository {

    LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(
            @Nullable String userId);

    LiveData<Result<StarUpdatedStatus>> updateIsStarred(
            @NonNull String userId, @NonNull Session session, boolean isStarred);

    LiveData<Result<LastReservationRequested>> changeReservation(
            @NonNull String userId, @NonNull Session session,
            @NonNull ReservationRequestAction action);
}