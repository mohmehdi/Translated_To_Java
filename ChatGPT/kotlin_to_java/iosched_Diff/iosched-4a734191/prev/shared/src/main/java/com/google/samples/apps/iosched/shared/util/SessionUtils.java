package com.google.samples.apps.iosched.shared.util;

import com.google.samples.apps.iosched.shared.model.Session;
import org.threeten.bp.Duration;

public class SessionUtils {

    public static Duration getDuration(Session session) {
        return Duration.between(session.getStartTime(), session.getEndTime());
    }
}