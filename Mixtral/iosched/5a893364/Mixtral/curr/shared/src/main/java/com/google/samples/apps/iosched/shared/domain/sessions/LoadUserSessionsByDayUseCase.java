

package com.google.samples.apps.iosched.shared.domain.sessions;

import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.domain.MediatorUseCase;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.model.UserSession;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.schedule.UserSessionMatcher;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LoadUserSessionsByDayUseCase extends MediatorUseCase<Pair<UserSessionMatcher, String>, LoadUserSessionsByDayUseCaseResult> {

    private final DefaultSessionAndUserEventRepository userEventRepository;

    @Inject
    public LoadUserSessionsByDayUseCase(DefaultSessionAndUserEventRepository userEventRepository) {
        this.userEventRepository = userEventRepository;
    }

    @Override
    public void execute(Pair<UserSessionMatcher, String> parameters) {
        UserSessionMatcher sessionMatcher = parameters.first;
        String userId = parameters.second;

        Result<UserEventsData> userSessionsObservable = userEventRepository.getObservableUserEvents(userId);

        result.removeSource(userSessionsObservable);
        result.addSource(userSessionsObservable, this::postValue);
    }
}

@Singleton
public class LoadUserSessionsByDayUseCaseResult {

    private final Map<ConferenceDay, List<UserSession>> userSessionsPerDay;
    private final UserEventsMessage userMessage;

    @Inject
    public LoadUserSessionsByDayUseCaseResult(DefaultScheduler defaultScheduler) {
        this.userSessionsPerDay = new LinkedHashMap<>();
        this.userMessage = UserEventsMessage.DATA_NOT_SYNCED;
    }

    public LoadUserSessionsByDayUseCaseResult(Map<ConferenceDay, List<UserSession>> userSessionsPerDay, UserEventsMessage userMessage) {
        this.userSessionsPerDay = userSessionsPerDay;
        this.userMessage = userMessage;
    }

    // Getters and setters
}

public enum UserEventsMessage {
    CHANGES_IN_RESERVATIONS,
    CHANGES_IN_WAITLIST,
    DATA_NOT_SYNCED;
}