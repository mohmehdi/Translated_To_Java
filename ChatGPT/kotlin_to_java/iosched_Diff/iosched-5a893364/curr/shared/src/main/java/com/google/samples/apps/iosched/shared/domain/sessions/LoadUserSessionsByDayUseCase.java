package com.google.samples.apps.iosched.shared.domain.sessions;

import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.MediatorUseCase;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;

import javax.inject.Inject;

public class LoadUserSessionsByDayUseCase extends MediatorUseCase<Pair<UserSessionMatcher, String>, LoadUserSessionsByDayUseCaseResult> {

    private DefaultSessionAndUserEventRepository userEventRepository;

    @Inject
    public LoadUserSessionsByDayUseCase(DefaultSessionAndUserEventRepository userEventRepository) {
        this.userEventRepository = userEventRepository;
    }

    @Override
    public void execute(Pair<UserSessionMatcher, String> parameters) {
        UserSessionMatcher sessionMatcher = parameters.first;
        String userId = parameters.second;

        Observable<List<UserSession>> userSessionsObservable = userEventRepository.getObservableUserEvents(userId);

        result.removeSource(userSessionsObservable);
        result.addSource(userSessionsObservable, new Observer<List<UserSession>>() {
            @Override
            public void onChanged(Result<List<UserSession>> it) {
                DefaultScheduler.execute(() -> {
                    if (it instanceof Result.Success) {
                        Result.Success<List<UserSession>> successResult = (Result.Success<List<UserSession>>) it;
                        List<UserSession> userSessions = successResult.getData().getUserSessionsPerDay().mapValues((ConferenceDay day, List<UserSession> sessions) -> {
                            return sessions.filter(sessionMatcher::matches);
                        });
                        LoadUserSessionsByDayUseCaseResult usecaseResult = new LoadUserSessionsByDayUseCaseResult(
                                userSessions,
                                successResult.getData().getUserMessage()
                        );
                        result.postValue(Result.Success(usecaseResult));
                    } else if (it instanceof Result.Error) {
                        result.postValue(it);
                    }
                });
            }
        });
    }
}

public class LoadUserSessionsByDayUseCaseResult {
    private Map<ConferenceDay, List<UserSession>> userSessionsPerDay;
    private UserEventsMessage userMessage;

    public LoadUserSessionsByDayUseCaseResult(Map<ConferenceDay, List<UserSession>> userSessionsPerDay, UserEventsMessage userMessage) {
        this.userSessionsPerDay = userSessionsPerDay;
        this.userMessage = userMessage;
    }

    public Map<ConferenceDay, List<UserSession>> getUserSessionsPerDay() {
        return userSessionsPerDay;
    }

    public UserEventsMessage getUserMessage() {
        return userMessage;
    }
}

public enum UserEventsMessage {
    CHANGES_IN_RESERVATIONS,
    CHANGES_IN_WAITLIST,
    DATA_NOT_SYNCED
}