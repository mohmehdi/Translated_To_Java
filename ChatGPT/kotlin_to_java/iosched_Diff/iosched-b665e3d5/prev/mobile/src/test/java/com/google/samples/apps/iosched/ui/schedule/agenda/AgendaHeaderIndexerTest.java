package com.google.samples.apps.iosched.ui.schedule.agenda;

import com.google.samples.apps.iosched.test.data.TestData;
import org.junit.Test;
import org.threeten.bp.ZonedDateTime;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AgendaHeaderIndexerTest {

    @Test
    public void indexAgenda_groupsCorrectly() {

        TestData.Block block = TestData.block1;
        ZonedDateTime start = ZonedDateTime.parse("2018-05-08T07:00:00-07:00");
        List<TestData.Block> sessions = List.of(
                block.copy(start.with(start)),
                block.copy(start.with(start)),
                block.copy(start.with(start.plusDays(1))),
                block.copy(start.with(start.plusDays(1))),
                block.copy(start.with(start.plusDays(2))),
                block.copy(start.with(start.plusDays(2)))
        );

        Map<Integer, ZonedDateTime> grouped = indexAgendaHeaders(sessions).toMap();

        assertEquals(3, grouped.size());
        assertEquals(Set.of(0, 2, 4), grouped.keySet());
        assertEquals(start, grouped.get(0));
        assertEquals(start.with(start.plusDays(1)), grouped.get(2));
        assertEquals(start.with(start.plusDays(2)), grouped.get(4));
    }

    @Test
    public void indexAgenda_roundsDayDown() {

        TestData.Block block = TestData.block1;
        ZonedDateTime dayOneSevenAM = ZonedDateTime.parse("2018-05-08T07:00:00-07:00");
        ZonedDateTime dayOneEightAM = ZonedDateTime.parse("2018-05-08T08:00:00-07:00");
        ZonedDateTime dayOneElevenPM = ZonedDateTime.parse("2018-05-08T23:00:00-07:00");
        ZonedDateTime dayTwoOneAM = ZonedDateTime.parse("2018-05-09T01:00:00-07:00");
        ZonedDateTime dayTwoSevenAM = ZonedDateTime.parse("2018-05-09T07:00:00-07:00");
        ZonedDateTime dayTwoEightAM = ZonedDateTime.parse("2018-05-09T08:00:00-07:00");

        List<TestData.Block> sessions = List.of(
                block.copy(start.with(dayOneSevenAM), end.with(dayOneEightAM)),
                block.copy(start.with(dayOneElevenPM), end.with(dayTwoOneAM)),
                block.copy(start.with(dayTwoSevenAM), end.with(dayTwoEightAM))
        );

        Map<Integer, ZonedDateTime> grouped = indexAgendaHeaders(sessions).toMap();

        assertEquals(2, grouped.size());
        assertEquals(Set.of(0, 2), grouped.keySet());
        assertEquals(dayOneSevenAM, grouped.get(0));
        assertEquals(dayTwoSevenAM, grouped.get(2));
    }
}