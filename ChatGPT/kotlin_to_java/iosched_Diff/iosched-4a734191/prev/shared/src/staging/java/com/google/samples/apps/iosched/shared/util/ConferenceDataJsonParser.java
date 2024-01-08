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

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SessionTemp.class, new SessionDeserializer());
        gsonBuilder.registerTypeAdapter(Tag.class, new TagDeserializer());
        com.google.gson.Gson gson = gsonBuilder.create();

        TestDataTemp tempData = gson.fromJson(jsonReader, TestDataTemp.class);
        return normalize(tempData);
    }

    private static TestData normalize(TestDataTemp data) {
        List<Session> sessions = new ArrayList<>();

        for (SessionTemp session : data.getSessions()) {
            Session newSession = new Session(
                    session.getId(),
                    session.getStartTime(),
                    session.getEndTime(),
                    session.getTitle(),
                    session.getAbstract(),
                    session.getSessionUrl(),
                    session.getLiveStreamUrl(),
                    session.getYouTubeUrl(),
                    data.getTags().stream().filter(tag -> session.getTags().contains(tag.getId())).toList(),
                    data.getSpeakers().stream().filter(speaker -> session.getSpeakers().contains(speaker.getId())).collect(Collectors.toSet()),
                    session.getPhotoUrl(),
                    session.getRelatedSessions(),
                    data.getRooms().stream().filter(room -> room.getId().equals(session.getRoom())).findFirst().orElse(null)
            );
            sessions.add(newSession);
        }

        return new TestData(sessions,
                data.getTags(),
                data.getSpeakers(),
                data.getBlocks(),
                data.getRooms());
    }

    public static List<Session> getSessions() {
        if (testData == null) {
            testData = parseConferenceData();
        }
        return testData.getSessions();
    }

    public static List<Tag> getTags() {
        if (testData == null) {
            testData = parseConferenceData();
        }
        return testData.getTags();
    }
}