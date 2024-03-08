package com.google.samples.apps.iosched.shared.data.userevent;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DefaultSessionAndUserEventRepository implements SessionAndUserEventRepository {

    private final MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> result = new MediatorLiveData<>();
    private final UserEventDataSource userEventDataSource;
    private final SessionRepository sessionRepository;

    @Inject
    public DefaultSessionAndUserEventRepository(UserEventDataSource userEventDataSource, SessionRepository sessionRepository) {
        this.userEventDataSource = userEventDataSource;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId) {

        if (userId == null) {
            Map<ConferenceDay, List<UserSession>> userSessionsPerDay = mapUserDataAndSessions(null, sessionRepository.getSessions());
            result.postValue(new Result.Success<>(new LoadUserSessionsByDayUseCaseResult(userSessionsPerDay, null)));
            return result;
        }

        LiveData<UserEventsResult> observableUserEvents = userEventDataSource.getObservableUserEvents(userId);

        result.removeSource(observableUserEvents);
        result.addSource(observableUserEvents, userEvents -> {
            if (userEvents == null) return;

            DefaultScheduler.execute(() -> {
                try {

                    List<Session> allSessions = sessionRepository.getSessions();

                    result.postValue(new Result.Success<>(new LoadUserSessionsByDayUseCaseResult(
                            mapUserDataAndSessions(userEvents, allSessions),
                            userEvents.getUserEventsMessage())
                    ));

                } catch (Exception e) {
                    result.postValue(new Result.Error(e));
                }
            });
        });
        return result;
    }

    @Override
    public LiveData<Result<StarUpdatedStatus>> updateIsStarred(String userId, Session session, boolean isStarred) {
        return userEventDataSource.updateStarred(userId, session, isStarred);
    }

    @Override
    public LiveData<Result<LastReservationRequested>> changeReservation(String userId, Session session, ReservationRequestAction action) {
        return userEventDataSource.requestReservation(userId, session, action);
    }

    @WorkerThread
    private Map<ConferenceDay, List<UserSession>> mapUserDataAndSessions(UserEventsResult userData, List<Session> allSessions) {

        if (userData == null) {
            return Arrays.stream(ConferenceDay.values())
                    .collect(Collectors.toMap(day -> day, day -> allSessions.stream()
                            .filter(day::contains)
                            .map(session -> new UserSession(session, null))
                            .collect(Collectors.toList())));
        }

        UserEventsResult.Companion.Triple<Boolean, List<UserEvent>, ?> triple = userData;

        Map<String, UserEvent> eventIdToUserEvent = triple.getSecond().stream()
                .collect(Collectors.toMap(UserEvent::getId, userEvent -> userEvent));
        List<UserSession> allUserSessions = allSessions.stream()
                .map(session -> new UserSession(session, eventIdToUserEvent.get(session.getId())))
                .collect(Collectors.toList());

        Set<String> alreadyPendingWriteIds = result.getValue() instanceof Result.Success ?
                ((Result.Success<LoadUserSessionsByDayUseCaseResult>) result.getValue()).getData().getUserSessionsPerDay().values().stream()
                        .flatMap(Collection::stream)
                        .filter(userSession -> userSession.getUserEvent() != null && userSession.getUserEvent().isHasPendingWrite())
                        .map(userSession -> userSession.getUserEvent().getId())
                        .collect(Collectors.toSet()) : new HashSet<>();

        return Arrays.stream(ConferenceDay.values())
                .collect(Collectors.toMap(day -> day, day -> allUserSessions.stream()
                        .filter(userSession -> day.contains(userSession.getSession()))
                        .map(userSession -> {
                            UserEvent userEvent = userSession.getUserEvent();
                            if (userEvent != null && alreadyPendingWriteIds.contains(userEvent.getId())) {
                                userEvent.setHasPendingWrite(true);
                            }
                            return new UserSession(userSession.getSession(), userEvent);
                        })
                        .sorted(Comparator.comparing(userSession -> userSession.getSession().getStartTime()))
                        .collect(Collectors.toList())));
    }
}

interface SessionAndUserEventRepository {

    LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId);

    LiveData<Result<StarUpdatedStatus>> updateIsStarred(String userId, Session session, boolean isStarred);

    LiveData<Result<LastReservationRequested>> changeReservation(String userId, Session session, ReservationRequestAction action);
}