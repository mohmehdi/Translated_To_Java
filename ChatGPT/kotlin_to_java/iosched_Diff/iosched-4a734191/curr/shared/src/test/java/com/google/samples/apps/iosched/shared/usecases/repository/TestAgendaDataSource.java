package com.google.samples.apps.iosched.shared.usecases.repository;

import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaDataSource;
import com.google.samples.apps.iosched.shared.model.Block;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

public class TestAgendaDataSource implements AgendaDataSource {

    private ZonedDateTime time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private ZonedDateTime time2 = ZonedDateTime.of(2017, 3, 12, 13, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

    private Block block1 = new Block(
        "Keynote",
        "keynote",
        0xffff00ff,
        time1,
        time2
    );

    private Block block2 = new Block(
        "Breakfast",
        "meal",
        0xffff00ff,
        time1.plusDays(1L),
        time2.plusDays(1L)
    );

    @Override
    public List<Block> getAgenda() {
        return Arrays.asList(block1, block2);
    }
}