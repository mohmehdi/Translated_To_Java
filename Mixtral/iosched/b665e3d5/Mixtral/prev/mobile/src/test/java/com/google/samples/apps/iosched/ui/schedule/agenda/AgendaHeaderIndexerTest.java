

package com.google.samples.apps.iosched.ui.schedule.agenda;

import com.google.samples.apps.iosched.test.data.TestData;
import org.junit.Assert;
import org.threeten.bp.ZonedDateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgendaHeaderIndexerTest {

    @org.junit.Test
    public void indexAgenda_groupsCorrectly() {
        AgendaBlock block = TestData.block1;
        ZonedDateTime start = ZonedDateTime.parse("2018-05-08T07:00:00-07:00");
        List<AgendaBlock> sessions = new ArrayList<>();
        sessions.add(new AgendaBlock(block, start));
        sessions.add(new AgendaBlock(block, start));
        sessions.add(new AgendaBlock(block, start.plusDays(1)));
        sessions.add(new AgendaBlock(block, start.plusDays(1)));
        sessions.add(new AgendaBlock(block, start.plusDays(2)));
        sessions.add(new AgendaBlock(block, start.plusDays(2)));

        Map<Integer, ZonedDateTime> grouped = indexAgendaHeaders(sessions);

        Assert.assertEquals(3, grouped.size());
        Assert.assertEquals(setOf(0, 2, 4), grouped.keySet());
        Assert.assertEquals(start, grouped.get(0));
        Assert.assertEquals(start.plusDays(1), grouped.get(2));
        Assert.assertEquals(start.plusDays(2), grouped.get(4));
    }

    @org.junit.Test
    public void indexAgenda_roundsDayDown() {
        AgendaBlock block = TestData.block1;
        ZonedDateTime dayOneSevenAM = ZonedDateTime.parse("2018-05-08T07:00:00-07:00");
        ZonedDateTime dayOneEightAM = ZonedDateTime.parse("2018-05-08T08:00:00-07:00");
        ZonedDateTime dayOneElevenPM = ZonedDateTime.parse("2018-05-08T23:00:00-07:00");
        ZonedDateTime dayTwoOneAM = ZonedDateTime.parse("2018-05-09T01:00:00-07:00");
        ZonedDateTime dayTwoSevenAM = ZonedDateTime.parse("2018-05-09T07:00:00-07:00");
        ZonedDateTime dayTwoEightAM = ZonedDateTime.parse("2018-05-09T08:00:00-07:00");

        List<AgendaBlock> sessions = new ArrayList<>();
        sessions.add(new AgendaBlock(block, dayOneSevenAM, dayOneEightAM));
        sessions.add(new AgendaBlock(block, dayOneElevenPM, dayTwoOneAM)); 
        sessions.add(new AgendaBlock(block, dayTwoSevenAM, dayTwoEightAM));

        Map<Integer, ZonedDateTime> grouped = indexAgendaHeaders(sessions);

        Assert.assertEquals(2, grouped.size());
        Assert.assertEquals(setOf(0, 2), grouped.keySet());
        Assert.assertEquals(dayOneSevenAM, grouped.get(0));
        Assert.assertEquals(dayTwoSevenAM, grouped.get(2));
    }

    private static Map<Integer, ZonedDateTime> indexAgendaHeaders(List<AgendaBlock> sessions) {
        Map<Integer, ZonedDateTime> index = new HashMap<>();
        int i = 0;
        for (AgendaBlock session : sessions) {
            ZonedDateTime start = session.getStartTime();
            int key = start.getDayOfYear() - 1;
            if (!index.containsKey(key)) {
                index.put(key, start);
            }
            session.setIndex(key);
            i++;
        }
        return index;
    }
}