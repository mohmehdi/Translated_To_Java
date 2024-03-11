package com.google.samples.apps.iosched.ui.schedule.agenda;

import com.google.samples.apps.iosched.shared.model.Block;
import org.threeten.bp.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AgendaHeaderIndexer {

    public static List<Pair<Integer, ZonedDateTime>> indexAgendaHeaders(List<Block> agendaItems) {
        List<Pair<Integer, ZonedDateTime>> result = new ArrayList<>();
        Set<Integer> distinctDays = new HashSet<>();

        for (int i = 0; i < agendaItems.size(); i++) {
            Block block = agendaItems.get(i);
            int index = i;
            ZonedDateTime startTime = block.getStartTime();
            int dayOfMonth = startTime.getDayOfMonth();

            if (!distinctDays.contains(dayOfMonth)) {
                result.add(new Pair<>(index, startTime));
                distinctDays.add(dayOfMonth);
            }
        }

        return result;
    }
}