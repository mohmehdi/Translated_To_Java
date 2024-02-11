

package com.google.samples.apps.iosched.ui.schedule;

import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.model.Room;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Speaker;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.model.Track;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestSessionDataSource implements SessionDataSource {

    private static final String ANDROID_TAG_ID = "1";
    private static final String ANDROID_TAG_TRACK = "TRACK";
    private static final int ANDROID_TAG_COLOR = 0xFFAED581;
    private static final String ANDROID_TAG_NAME = "Android";

    private static final String WEB_TAG_ID = "2";
    private static final String WEB_TAG_TRACK = "TRACK";
    private static final int WEB_TAG_COLOR = 0xFFFFF176;
    private static final String WEB_TAG_NAME = "Web";

    private static final ZonedDateTime TIME_1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private static final ZonedDateTime TIME_2 = ZonedDateTime.of(2017, 3, 12, 13, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private static final Room ROOM_1 = new Room("1", "Tent 1", 40);
    private static final Speaker SPEAKER_1 = new Speaker("1", "Troy McClure", "", "", "", "", "");

    private static final Session SESSION_1 = new Session(
            "1", TIME_1, TIME_2,
            "Jet Packs", "", ROOM_1, "",
            "", "", Arrays.asList(new Tag(ANDROID_TAG_ID, ANDROID_TAG_TRACK, ANDROID_TAG_COLOR, ANDROID_TAG_NAME, 0)),
            new HashSet<>(Arrays.asList(SPEAKER_1)), "", Arrays.asList()
    );

    private static final Session SESSION_2 = new Session(
            "2", TIME_1, TIME_2,
            "Flying Cars", "", ROOM_1, "Title 1",
            "", "", Arrays.asList(new Tag(ANDROID_TAG_ID, ANDROID_TAG_TRACK, ANDROID_TAG_COLOR, ANDROID_TAG_NAME, 0)),
            new HashSet<>(Arrays.asList(SPEAKER_1)), "", Arrays.asList()
    );

    private static final Session SESSION_3 = new Session(
            "3", TIME_1, TIME_2,
            "Teleportation", "", ROOM_1, "Title 1",
            "", "", Arrays.asList(new Tag(WEB_TAG_ID, WEB_TAG_TRACK, WEB_TAG_COLOR, WEB_TAG_NAME, 0)),
            new HashSet<>(Arrays.asList(SPEAKER_1)), "", Arrays.asList()
    );

    @Override
    public List<Session> getSessions() {
        return Arrays.asList(SESSION_1, SESSION_2, SESSION_3);
    }

    @Override
    public Session getSession(String sessionId) {
        return getSessions().get(0);
    }
}