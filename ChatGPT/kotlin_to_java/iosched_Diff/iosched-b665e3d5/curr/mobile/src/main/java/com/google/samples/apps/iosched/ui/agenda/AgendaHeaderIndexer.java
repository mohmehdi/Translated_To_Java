package com.google.samples.apps.iosched.ui.agenda;

import com.google.samples.apps.iosched.model.Block;
import org.threeten.bp.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgendaHeaderIndexer {

    public static List<Pair<Integer, ZonedDateTime>> indexAgendaHeaders(List<Block> agendaItems) {
        List<Pair<Integer, ZonedDateTime>> indexedHeaders = new ArrayList<>();

        for (int i = 0; i < agendaItems.size(); i++) {
            Block block = agendaItems.get(i);
            indexedHeaders.add(new Pair<>(i, block.getStartTime()));
        }

        List<Pair<Integer, ZonedDateTime>> distinctHeaders = new ArrayList<>();
        for (Pair<Integer, ZonedDateTime> header : indexedHeaders) {
            if (!distinctHeaders.contains(header)) {
                distinctHeaders.add(header);
            }
        }

        return distinctHeaders;
    }
}