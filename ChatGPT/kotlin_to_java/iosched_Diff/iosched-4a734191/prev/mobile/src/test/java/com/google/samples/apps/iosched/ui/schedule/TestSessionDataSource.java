package com.google.samples.apps.iosched.ui.schedule;

import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.data.tag.TagDataSource;
import com.google.samples.apps.iosched.shared.model.Room;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Speaker;
import com.google.samples.apps.iosched.shared.model.Tag;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

public class TestSessionDataSource implements SessionDataSource, TagDataSource {

    private Tag androidTag = new Tag("1", "TRACK", 0, "Android", 0xFFAED581);
    private Tag webTag = new Tag("2", "TRACK", 1, "Web", 0xFFFFF176);
    private Tag sessionsTag = new Tag("101", "TYPE", 0, "Sessions", 0);
    private Tag codelabsTag = new Tag("102", "TYPE", 1, "Codelabs", 0);
    private Tag beginnerTag = new Tag("201", "LEVEL", 0, "Beginner", 0);
    private Tag intermediateTag = new Tag("202", "LEVEL", 1, "Intermediate", 0);

    private ZonedDateTime time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private ZonedDateTime time2 = ZonedDateTime.of(2017, 3, 12, 13, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private Room room1 = new Room("1", "Tent 1", 40);
    private Speaker speaker1 = new Speaker("1", "Troy McClure", "", "", "", "", "");

    private Session session1 = new Session("1", time1, time2,
            "Jet Packs", "", room1, "",
            "", "", "", new ArrayList<>(Arrays.asList(androidTag, webTag)),
            new HashSet<>(Collections.singletonList(speaker1)), "", new HashSet<>());

    private Session session2 = new Session("2", time1, time2,
            "Flying Cars", "", room1, "Title 1",
            "", "", "", new ArrayList<>(Collections.singletonList(androidTag)),
            new HashSet<>(Collections.singletonList(speaker1)), "", new HashSet<>());

    private Session session3 = new Session("3", time1, time2,
            "Teleportation", "", room1, "Title 1",
            "", "", "", new ArrayList<>(Collections.singletonList(webTag)),
            new HashSet<>(Collections.singletonList(speaker1)), "", new HashSet<>());

    @Override
    public List<Session> getSessions() {
        return new ArrayList<>(Arrays.asList(session1, session2, session3));
    }

    @Override
    public List<Tag> getTags() {
        return new ArrayList<>(Arrays.asList(androidTag, webTag, sessionsTag, codelabsTag, beginnerTag, intermediateTag));
    }

    @Override
    public Session getSession(String sessionId) {
        return getSessions().get(0);
    }
}