package com.google.samples.apps.iosched.shared.usecases.repository;

import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.model.Room;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Speaker;
import com.google.samples.apps.iosched.shared.model.Tag;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

public class TestSessionDataSource implements SessionDataSource {

    private Tag androidTag = new Tag("1", "TRACK", 0, "Android", 0xFFAED581);
    private Tag webTag = new Tag("2", "TRACK", 1, "Web", 0xFFFFF176);

    private ZonedDateTime time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private ZonedDateTime time2 = ZonedDateTime.of(2017, 3, 12, 13, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private Room room1 = new Room("1", "Tent 1", 40);
    private Speaker speaker1 = new Speaker("1", "Troy McClure", "", "", "", "", "");

    private Session session1 = new Session(
            "1", time1, time2,
            "Jet Packs", "", room1, "",
            "", "", "", Arrays.asList(androidTag, webTag),
            new HashSet<>(Arrays.asList(speaker1)), "", Collections.emptySet()
    );

    private Session session2 = new Session(
            "2", time1, time2,
            "Flying Cars", "", room1, "Title 1",
            "", "", "", Arrays.asList(androidTag),
            new HashSet<>(Arrays.asList(speaker1)), "", Collections.emptySet()
    );

    private Session session3 = new Session(
            "3", time1, time2,
            "Teleportation", "", room1, "Title 1",
            "", "", "", Arrays.asList(webTag),
            new HashSet<>(Arrays.asList(speaker1)), "", Collections.emptySet()
    );

    @Override
    public List<Session> getSessions() {
        return Arrays.asList(session1, session2, session3);
    }

    @Override
    public Session getSession(String sessionId) {
        return getSessions().get(0);
    }
}