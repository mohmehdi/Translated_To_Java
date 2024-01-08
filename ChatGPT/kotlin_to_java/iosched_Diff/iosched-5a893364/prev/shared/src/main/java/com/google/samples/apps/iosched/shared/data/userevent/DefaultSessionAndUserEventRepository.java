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

    private UserEventDataSource userEventDataSource;
    private SessionRepository sessionRepository;
    private MediatorLiveData<Result<LoadUserSessionsByDayUseCaseResult>> result = new MediatorLiveData<>();

    @Inject
    public DefaultSessionAndUserEventRepository(UserEventDataSource userEventDataSource, SessionRepository sessionRepository) {
        this.userEventDataSource = userEventDataSource;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId) {
        LiveData<Result<LoadUserSessionsByDayUseCaseResult>> observableUserEvents = userEventDataSource.getObservableUserEvents(userId);
        result.removeSource(observableUserEvents);
        result.addSource(observableUserEvents, userEvents -> {
            if (userEvents == null) {
                return;
            }

            DefaultScheduler.execute(() -> {
                try {
                    List<Session> allSessions = sessionRepository.getSessions();

                    result.postValue(new Result.Success<>(
                            new LoadUserSessionsByDayUseCaseResult(
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
        UserEventsResult.Companion.Triple<Boolean, List<UserEvent>, String> triple = userData.getTriple();
        boolean allDataSynced = triple.getFirst();
        List<UserEvent> userEvents = triple.getSecond();
        String userEventsMessage = triple.getThird();

        Map<String, UserEvent> eventIdToUserEvent = new HashMap<>();
        for (UserEvent userEvent : userEvents) {
            eventIdToUserEvent.put(userEvent.getId(), userEvent);
        }

        List<UserSession> allUserSessions = new ArrayList<>();
        for (Session session : allSessions) {
            allUserSessions.add(new UserSession(session, eventIdToUserEvent.get(session.getId())));
        }

        Set<String> alreadyPendingWriteIds = new HashSet<>();
        if (result.getValue() instanceof Result.Success) {
            if (allDataSynced) {
                alreadyPendingWriteIds = Collections.emptySet();
            } else {
                List<UserSession> userSessions = ((Result.Success<LoadUserSessionsByDayUseCaseResult>) result.getValue()).getData().getUserSessionsPerDay().values().stream()
                        .flatMap(List::stream)
                        .filter(userSession -> userSession.getUserEvent() != null && userSession.getUserEvent().isHasPendingWrite())
                        .map(userSession -> userSession.getUserEvent().getId())
                        .collect(Collectors.toSet());
            }
        }

        Map<ConferenceDay, List<UserSession>> mappedData = new HashMap<>();
        for (ConferenceDay day : ConferenceDay.values()) {
            List<UserSession> filteredUserSessions = allUserSessions.stream()
                    .filter(userSession -> day.contains(userSession.getSession()))
                    .map(userSession -> {
                        UserEvent userEvent = userSession.getUserEvent();
                        if (alreadyPendingWriteIds.contains(userEvent.getId())) {
                            userEvent.setHasPendingWrite(true);
                        }
                        return new UserSession(userSession.getSession(), userEvent);
                    })
                    .sorted(Comparator.comparing(userSession -> userSession.getSession().getStartTime()))
                    .collect(Collectors.toList());
            mappedData.put(day, filteredUserSessions);
        }

        return mappedData;
    }
}

interface SessionAndUserEventRepository {

    LiveData<Result<LoadUserSessionsByDayUseCaseResult>> getObservableUserEvents(String userId);

    LiveData<Result<StarUpdatedStatus>> updateIsStarred(String userId, Session session, boolean isStarred);

    LiveData<Result<LastReservationRequested>> changeReservation(String userId, Session session, ReservationRequestAction action);
}