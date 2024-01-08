package com.google.samples.apps.iosched.shared.domain.sessions;

import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.MediatorUseCase;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;

import java.util.List;
import java.util.Map;

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
        List<UserSession> userSessions = userEventRepository.getObservableUserEvents(userId);

        result.removeSource(userSessions);
        result.addSource(userSessions, it -> {
            DefaultScheduler.execute(() -> {
                if (it instanceof Result.Success) {
                    Result.Success<List<UserSession>> successResult = (Result.Success<List<UserSession>>) it;
                    Map<ConferenceDay, List<UserSession>> userSessionsPerDay = successResult.getData().getUserSessionsPerDay().mapValues((key, sessions) -> {
                        return sessions.filter(sessionMatcher::matches);
                    });
                    LoadUserSessionsByDayUseCaseResult usecaseResult = new LoadUserSessionsByDayUseCaseResult(
                            userSessionsPerDay,
                            successResult.getData().getUserMessage()
                    );
                    result.postValue(Result.Success(usecaseResult));
                } else if (it instanceof Result.Error) {
                    result.postValue((Result.Error) it);
                }
            });
        });
    }
}

class LoadUserSessionsByDayUseCaseResult {
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

enum UserEventsMessage {
    CHANGES_IN_RESERVATIONS,
    CHANGES_IN_WAITLIST,
    DATA_NOT_SYNCED
}