

package com.google.samples.apps.iosched.shared.util;

import com.google.gson.*;
import com.google.samples.apps.iosched.shared.model.*;
import com.google.samples.apps.iosched.shared.util.testdata.*;
import java.io.*;
import java.util.*;

public class ConferenceDataJsonParser {

    private static final String FILENAME = "conference_data.json";

    private static TestData testData;

    static {
        testData = parseConferenceData();
    }

    private static TestData parseConferenceData() {
        InputStream inputStream = ConferenceDataJsonParser.class
                .getClassLoader()
                .getResource(FILENAME)
                .openStream();

        JsonReader jsonReader = new JsonReader(new BufferedReader(new InputStreamReader(inputStream)));

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SessionTemp.class, new SessionDeserializer());
        gsonBuilder.registerTypeAdapter(Block.class, new BlockDeserializer());
        gsonBuilder.registerTypeAdapter(Tag.class, new TagDeserializer());
        Gson gson = gsonBuilder.create();

        TestDataTemp tempData = gson.fromJson(jsonReader, TestDataTemp.class);
        return normalize(tempData);
    }

    private static TestData normalize(TestDataTemp data) {
        List<Session> sessions = new ArrayList<>();

        for (SessionTemp session : data.sessions) {
            Set<Speaker> speakersSet = new HashSet<>();
            for (Integer speakerId : session.speakers) {
                Speaker speaker = data.speakers.stream()
                        .filter(s -> s.id.equals(speakerId))
                        .findFirst()
                        .orElse(null);
                if (speaker != null) {
                    speakersSet.add(speaker);
                }
            }

            Set<Tag> tagsSet = new HashSet<>();
            for (Integer tagId : session.tags) {
                Tag tag = data.tags.stream()
                        .filter(t -> t.id.equals(tagId))
                        .findFirst()
                        .orElse(null);
                if (tag != null) {
                    tagsSet.add(tag);
                }
            }

            Session newSession = new Session(
                    session.id,
                    session.startTime,
                    session.endTime,
                    session.title,
                    session.abstract_,
                    session.sessionUrl,
                    session.liveStreamUrl,
                    session.youTubeUrl,
                    tagsSet,
                    speakersSet,
                    session.photoUrl,
                    session.relatedSessions,
                    data.rooms.stream()
                            .filter(r -> r.id.equals(session.room))
                            .findFirst()
                            .orElse(null)
            );
            sessions.add(newSession);
        }

        return new TestData(sessions, data.tags, data.speakers, data.blocks, data.rooms);
    }

    public static List<Session> getSessions() {
        return testData.sessions;
    }

    public static List<Block> getAgenda() {
        return testData.blocks;
    }

    public static List<Tag> getTags() {
        return testData.tags;
    }
}