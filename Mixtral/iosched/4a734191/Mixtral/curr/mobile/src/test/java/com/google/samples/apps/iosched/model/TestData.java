

package com.google.samples.apps.iosched.model;

import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.model.Room;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Speaker;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;

import java.time.LocalTime;
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
            TimeUtils.getDayStart(ConferenceDay.DAY_1), TimeUtils.getDayEnd(ConferenceDay.DAY_1),
            room, "", "", "", "",
            Arrays.asList(androidTag, webTag), Collections.singleton(speaker),
            Collections.emptySet());

    public static final Session session1 = new Session("1", "Session 1", "",
            TimeUtils.getDayStart(ConferenceDay.DAY_1), TimeUtils.getDayEnd(ConferenceDay.DAY_1),
            room, "", "", "", "",
            Arrays.asList(androidTag, webTag), Collections.singleton(speaker),
            Collections.emptySet());

    public static final Session session2 = new Session("2", "Session 2", "",
            TimeUtils.getDayStart(ConferenceDay.DAY_2), TimeUtils.getDayEnd(ConferenceDay.DAY_2),
            room, "", "", "", "",
            Arrays.asList(androidTag), Collections.singleton(speaker),
            Collections.emptySet());

    public static final Session session3 = new Session("3", "Session 3", "",
            TimeUtils.getDayStart(ConferenceDay.DAY_3), TimeUtils.getDayEnd(ConferenceDay.DAY_3),
            room, "", "", "", "",
            Arrays.asList(webTag), Collections.singleton(speaker),
            Collections.emptySet());

    public static final Map<ConferenceDay, List<Session>> sessionsMap = Map.of(
            ConferenceDay.DAY_1, Arrays.asList(session0, session1),
            ConferenceDay.DAY_2, Arrays.asList(session2),
            ConferenceDay.DAY_3, Arrays.asList(session3)
    );

    public static final List<Tag> tagsList = Arrays.asList(
            androidTag, webTag, sessionsTag, codelabsTag, beginnerTag,
            intermediateTag, advancedTag
    );

    public static final Block block1 = new Block(
            "Keynote", "keynote", 0xffff00ff,
            TimeUtils.getDayStart(ConferenceDay.DAY_1),
            TimeUtils.getDayStart(ConferenceDay.DAY_1).plusHours(1)
    );

    public static final Block block2 = new Block(
            "Breakfast", "meal", 0xffff00ff,
            TimeUtils.getDayStart(ConferenceDay.DAY_1).plusHours(1),
            TimeUtils.getDayStart(ConferenceDay.DAY_1).plusHours(2)
    );

    public static final List<Block> agenda = Arrays.asList(block1, block2);
}