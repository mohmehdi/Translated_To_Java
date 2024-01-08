package com.google.samples.apps.iosched.ui.schedule.agenda;

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
            boolean isDistinct = true;
            for (Pair<Integer, ZonedDateTime> distinctHeader : distinctHeaders) {
                if (header.getSecond().getDayOfMonth() == distinctHeader.getSecond().getDayOfMonth()) {
                    isDistinct = false;
                    break;
                }
            }
            if (isDistinct) {
                distinctHeaders.add(header);
            }
        }

        return distinctHeaders;
    }
}