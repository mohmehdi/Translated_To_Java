package com.google.samples.apps.iosched.ui.agenda;

import com.google.samples.apps.iosched.test.data.TestData;
import org.junit.Assert;
import org.junit.Test;
import org.threeten.bp.ZonedDateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgendaHeaderIndexerTest {


@Test
public void indexAgenda_groupsCorrectly() {
    AgendaBlock block = TestData.block1;
    ZonedDateTime start = ZonedDateTime.parse("2018-05-08T07:00:00-07:00");
    List<AgendaBlock> sessions = List.of(
            block.copy(startTime = start),
            block.copy(startTime = start),
            block.copy(startTime = start.plusDays(1)),
            block.copy(startTime = start.plusDays(1)),
            block.copy(startTime = start.plusDays(2)),
            block.copy(startTime = start.plusDays(2))
    );

    Map<Integer, ZonedDateTime> grouped = indexAgendaHeaders(sessions);

    Assert.assertEquals(3, grouped.size());
    Assert.assertEquals(Set.of(0, 2, 4), grouped.keySet());
    Assert.assertEquals(start, grouped.get(0));
    Assert.assertEquals(start.plusDays(1), grouped.get(2));
    Assert.assertEquals(start.plusDays(2), grouped.get(4));
}

@Test
public void indexAgenda_roundsDayDown() {
    AgendaBlock block = TestData.block1;
    ZonedDateTime dayOneSevenAM = ZonedDateTime.parse("2018-05-08T07:00:00-07:00");
    ZonedDateTime dayOneEightAM = ZonedDateTime.parse("2018-05-08T08:00:00-07:00");
    ZonedDateTime dayOneElevenPM = ZonedDateTime.parse("2018-05-08T23:00:00-07:00");
    ZonedDateTime dayTwoOneAM = ZonedDateTime.parse("2018-05-09T01:00:00-07:00");
    ZonedDateTime dayTwoSevenAM = ZonedDateTime.parse("2018-05-09T07:00:00-07:00");
    ZonedDateTime dayTwoEightAM = ZonedDateTime.parse("2018-05-09T08:00:00-07:00");

    List<AgendaBlock> sessions = List.of(
            block.copy(startTime = dayOneSevenAM, endTime = dayOneEightAM),
            block.copy(startTime = dayOneElevenPM, endTime = dayTwoOneAM),
            block.copy(startTime = dayTwoSevenAM, endTime = dayTwoEightAM)
    );

    Map<Integer, ZonedDateTime> grouped = indexAgendaHeaders(sessions);

    Assert.assertEquals(2, grouped.size());
    Assert.assertEquals(Set.of(0, 2), grouped.keySet());
    Assert.assertEquals(dayOneSevenAM, grouped.get(0));
    Assert.assertEquals(dayTwoSevenAM, grouped.get(2));
}

}