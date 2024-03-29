package com.google.samples.apps.iosched.ui.schedule.agenda;

import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaDataSource;
import com.google.samples.apps.iosched.shared.model.Block;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import java.util.List;

public class TestAgendaDataSource implements AgendaDataSource {

    private static final ZonedDateTime time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

    private static final Block block = new Block(
            "Keynote",
            "keynote",
            0xffff00ff,
            time1,
            time1.plusHours(1L)
    );

    @Override
    public List<Block> getAgenda() {
        return List.of(block);
    }
}