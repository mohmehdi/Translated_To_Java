

package com.google.samples.apps.iosched.model;

import com.google.samples.apps.iosched.shared.model.Room;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Speaker;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_1;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_2;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestData {

    public static final Tag androidTag = new Tag("1", "TRACK", 0, "Android", 0xFFAED581);
    public static final Tag webTag = new Tag("2", "TRACK", 1, "Web", 0xFFFFF176);
    public static final Tag sessionsTag = new Tag("101", "TYPE", 0, "Sessions", 0);
    public static final Tag codelabsTag = new Tag("102", "TYPE", 1, "Codelabs", 0);
    public static final Tag beginnerTag = new Tag("201", "LEVEL", 0, "Beginner", 0);
    public static final Tag intermediateTag = new Tag("202", "LEVEL", 1, "Intermediate", 0);
    public static final Tag advancedTag = new Tag("203", "LEVEL", 2, "Advanced", 0);

    public static final Speaker speaker = new Speaker("1", "Troy McClure", "", "", "", "", "");

    public static final Room room = new Room("1", "Tent 1", 40);

    public static final Session session0 = new Session("0", "Session 0", "",
            DAY_1.start, DAY_1.end, room, "", "", "", Collections.singletonList(androidTag),
            Collections.singleton(speaker), Collections.emptySet());

    public static final Session session1 = new Session("1", "Session 1", "",
            DAY_1.start, DAY_1.end, room, "", "", "", Collections.singletonList(androidTag),
            Collections.singleton(speaker), Collections.emptySet());

    public static final Session session2 = new Session("2", "Session 2", "",
            DAY_2.start, DAY_2.end, room, "", "", "", Collections.singletonList(androidTag),
            Collections.singleton(speaker), Collections.emptySet());

    public static final Session session3 = new Session("3", "Session 3", "",
            DAY_3.start, DAY_3.end, room, "", "", "", Collections.singletonList(webTag),
            Collections.singleton(speaker), Collections.emptySet());

    public static final Map<ConferenceDay, List<Session>> sessionsMap = Map.of(
            DAY_1, Arrays.asList(session0, session1),
            DAY_2, Arrays.asList(session2),
            DAY_3, Arrays.asList(session3)
    );

    public static final List<Tag> tagsList = Arrays.asList(androidTag, webTag, sessionsTag, codelabsTag, beginnerTag,
            intermediateTag, advancedTag);
}