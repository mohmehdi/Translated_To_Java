package com.google.samples.apps.iosched.shared.util;

import com.google.gson.GsonBuilder;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.util.testdata.SessionDeserializer;
import com.google.samples.apps.iosched.shared.util.testdata.SessionTemp;
import com.google.samples.apps.iosched.shared.util.testdata.TagDeserializer;
import com.google.samples.apps.iosched.shared.util.testdata.TestData;
import com.google.samples.apps.iosched.shared.util.testdata.TestDataTemp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ConferenceDataJsonParser {

    private static final String FILENAME = "conference_data.json";
    private static TestData testData;

    private static TestData parseConferenceData() {
        InputStream inputStream = ConferenceDataJsonParser.class.getClassLoader().getResourceAsStream(FILENAME);

        com.google.gson.stream.JsonReader jsonReader = new com.google.gson.stream.JsonReader(new InputStreamReader(inputStream));

        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(SessionTemp.class, new SessionDeserializer())
                .registerTypeAdapter(Tag.class, new TagDeserializer());

        com.google.gson.Gson gson = gsonBuilder.create();

        TestDataTemp tempData = gson.fromJson(jsonReader, TestDataTemp.class);
        return normalize(tempData);
    }

    private static TestData normalize(TestDataTemp data) {
        List<Session> sessions = new ArrayList<>();

        for (SessionTemp session : data.sessions) {
            Session newSession = new Session(
                    session.id,
                    session.startTime,
                    session.endTime,
                    session.title,
                    session.abstract,
                    session.sessionUrl,
                    session.liveStreamUrl,
                    session.youTubeUrl,
                    data.tags.stream().filter(tag -> session.tags.contains(tag.id)).toList(),
                    data.speakers.stream().filter(speaker -> session.speakers.contains(speaker.id)).collect(Collectors.toSet()),
                    session.photoUrl,
                    session.relatedSessions,
                    data.rooms.stream().filter(room -> room.id.equals(session.room)).findFirst().orElse(null)
            );
            sessions.add(newSession);
        }

        return new TestData(sessions,
                data.tags,
                data.speakers,
                data.blocks,
                data.rooms);
    }

    public static List<Session> getSessions() {
        if (testData == null) {
            testData = parseConferenceData();
        }
        return testData.sessions;
    }

    public static List<Tag> getTags() {
        if (testData == null) {
            testData = parseConferenceData();
        }
        return testData.tags;
    }
}