

package com.google.samples.apps.iosched.shared.usecases.repository;

import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.model.Room;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Speaker;
import com.google.samples.apps.iosched.shared.model.Tag;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class TestSessionDataSource implements SessionDataSource {

    private static final String SESSION_1_ID = "1";
    private static final String SESSION_2_ID = "2";
    private static final String SESSION_3_ID = "3";

    private static final Tag androidTag = new Tag("1", "TRACK", 0, "Android", 0xFFAED581);
    private static final Tag webTag = new Tag("2", "TRACK", 1, "Web", 0xFFFFF176);

    private static final ZonedDateTime time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private static final ZonedDateTime time2 = ZonedDateTime.of(2017, 3, 12, 13, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private static final Room room1 = new Room("1", "Tent 1", 40);
    private static final Speaker speaker1 = new Speaker("1", "Troy McClure", "", "", "", "", "");

    private static final Session session1 = new Session(
            SESSION_1_ID, time1, time2,
            "Jet Packs", "", room1, "",
            "", "", Arrays.asList(androidTag, webTag),
            Sets.newHashSet(speaker1), "", Sets.newHashSet());

    private static final Session session2 = new Session(
            SESSION_2_ID, time1, time2,
            "Flying Cars", "", room1, "Title 1",
            "", "", Sets.newHashSet(androidTag),
            Sets.newHashSet(speaker1), "", Sets.newHashSet());

    private static final Session session3 = new Session(
            SESSION_3_ID, time1, time2,
            "Teleportation", "", room1, "Title 1",
            "", "", Sets.newHashSet(webTag),
            Sets.newHashSet(speaker1), "", Sets.newHashSet());

    @Override
    public List<Session> getSessions() {
        return Arrays.asList(session1, session2, session3);
    }

    @Override
    public Session getSession(String sessionId) {
        return getSessions().get(0);
    }
}