

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
                .getResourceAsStream(FILENAME);

        JsonReader jsonReader = new JsonReader(new BufferedReader(new InputStreamReader(inputStream)));

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SessionTemp.class, new SessionDeserializer());
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
                Optional<Speaker> speakerOptional = data.speakers.stream()
                        .filter(speaker -> speaker.id.equals(speakerId))
                        .findFirst();
                if (speakerOptional.isPresent()) {
                    speakersSet.add(speakerOptional.get());
                }
            }

            Set<Tag> tagsSet = new HashSet<>();
            for (Integer tagId : session.tags) {
                Optional<Tag> tagOptional = data.tags.stream()
                        .filter(tag -> tag.id.equals(tagId))
                        .findFirst();
                if (tagOptional.isPresent()) {
                    tagsSet.add(tagOptional.get());
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
                            .filter(room -> room.id.equals(session.room))
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

    public static List<Tag> getTags() {
        return testData.tags;
    }
}