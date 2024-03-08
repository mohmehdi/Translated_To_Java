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

    private final DefaultSessionAndUserEventRepository userEventRepository;

    @Inject
    public LoadUserSessionsByDayUseCase(DefaultSessionAndUserEventRepository userEventRepository) {
        this.userEventRepository = userEventRepository;
    }

    @Override
    public void execute(Pair<UserSessionMatcher, String> parameters) {
        UserSessionMatcher sessionMatcher = parameters.getFirst();
        String userId = parameters.getSecond();
        LiveData<Result<List<UserSession>>> userSessions = userEventRepository.getObservableUserEvents(userId);

        result.removeSource(userSessions);
        result.addSource(userSessions, it -> DefaultScheduler.execute(() -> {
            if (it instanceof Result.Success) {
                Map<ConferenceDay, List<UserSession>> userSessionsPerDay = ((Result.Success<List<UserSession>>) it).getData().getUserSessionsPerDay().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                                .filter(sessionMatcher::matches)
                                .collect(Collectors.toList())));
                LoadUserSessionsByDayUseCaseResult usecaseResult = new LoadUserSessionsByDayUseCaseResult(
                        userSessionsPerDay,
                        ((Result.Success<List<UserSession>>) it).getData().getUserMessage()
                );
                result.postValue(Result.Success(usecaseResult));
            } else if (it instanceof Result.Error) {
                result.postValue(it);
            }
        }));
    }
}

class LoadUserSessionsByDayUseCaseResult {
    private final Map<ConferenceDay, List<UserSession>> userSessionsPerDay;
    private final UserEventsMessage userMessage;

    public LoadUserSessionsByDayUseCaseResult(Map<ConferenceDay, List<UserSession>> userSessionsPerDay, UserEventsMessage userMessage) {
        this.userSessionsPerDay = userSessionsPerDay;
        this.userMessage = userMessage;
    }
}

enum UserEventsMessage {
    CHANGES_IN_RESERVATIONS,
    CHANGES_IN_WAITLIST,
    DATA_NOT_SYNCED
}