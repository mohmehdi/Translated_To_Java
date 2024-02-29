

package com.google.samples.apps.iosched.shared.domain.sessions;

import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.MediatorUseCase;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.inject.Inject;

public class LoadUserSessionsByDayUseCase extends MediatorUseCase<Pair<UserSessionMatcher, String>, LoadUserSessionsByDayUseCaseResult> {

    private final DefaultSessionAndUserEventRepository userEventRepository;
    private final Executor defaultScheduler;

    @Inject
    public LoadUserSessionsByDayUseCase(
            DefaultSessionAndUserEventRepository userEventRepository,
            DefaultScheduler defaultScheduler
    ) {
        this.userEventRepository = userEventRepository;
        this.defaultScheduler = defaultScheduler;
    }

    @Override
    public void execute(Pair<UserSessionMatcher, String> parameters) {
        UserSessionMatcher sessionMatcher = parameters.first;
        String userId = parameters.second;

        Result<UserSessionsPerDay> userSessions = userEventRepository.getObservableUserEvents(userId);

        removeSource(userSessions);
        addSource(userSessions, sessionsResult -> {
            defaultScheduler.execute(() -> {
                if (sessionsResult instanceof Result.Success) {
                    Result.Success<UserSessionsPerDay> success = (Result.Success<UserSessionsPerDay>) sessionsResult;
                    UserSessionsPerDay data = success.getData();
                    Map<ConferenceDay, List<UserSession>> userSessionsPerDay = data.getUserSessionsPerDay().entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            entry -> entry.getValue().stream()
                                                    .filter(sessionMatcher::matches)
                                                    .collect(Collectors.toList())
                                    )
                            );
                    LoadUserSessionsByDayUseCaseResult usecaseResult = new LoadUserSessionsByDayUseCaseResult(
                            userSessionsPerDay,
                            data.getUserMessage()
                    );
                    postValue(new Result.Success<>(usecaseResult));
                } else if (sessionsResult instanceof Result.Error) {
                    postValue(sessionsResult);
                }
            });
        });
    }
}

record LoadUserSessionsByDayUseCaseResult(
        Map<ConferenceDay, List<UserSession>> userSessionsPerDay,
        UserEventsMessage userMessage
) {
}

enum UserEventsMessage {
    CHANGES_IN_RESERVATIONS,
    CHANGES_IN_WAITLIST,
    DATA_NOT_SYNCED
}