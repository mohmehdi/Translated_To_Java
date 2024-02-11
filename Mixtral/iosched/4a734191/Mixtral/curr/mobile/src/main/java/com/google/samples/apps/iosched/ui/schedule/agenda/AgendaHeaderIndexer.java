

package com.google.samples.apps.iosched.ui.schedule.agenda;

import com.google.samples.apps.iosched.shared.model.Block;
import one.util.streamex.StreamEx;
import org.threeten.bp.ZonedDateTime;

import java.util.List;
import java.util.stream.Collectors;

public class AgendaHeaderIndexer {

    public static List<Pair<Integer, ZonedDateTime>> indexAgendaHeaders(List<Block> agendaItems) {
        return StreamEx.of(agendaItems)
                .mapIndexed((index, block) -> new Pair<>(index, block.getStartTime()))
                .distinctBy(agendaHeaderIndexed -> agendaHeaderIndexed.second.getDayOfMonth())
                .toList();
    }
}