package com.google.samples.apps.iosched.shared.usecases.repository;

import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaDataSource;
import com.google.samples.apps.iosched.shared.model.Block;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import java.util.List;
import java.util.Collections;

public class TestAgendaDataSource implements AgendaDataSource {

    private static final ZonedDateTime time1 = ZonedDateTime.of(2017, 3, 12, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
    private static final ZonedDateTime time2 = ZonedDateTime.of(2017, 3, 12, 13, 0, 0, 0, ZoneId.of("Asia/Tokyo"));

    private static final Block block1 = new Block(
            "Keynote",
            "keynote",
            0xffff00ff,
            time1,
            time2
    );

    private static final ZonedDateTime tomorrowTime1 = time1.plusDays(1L);
    private static final ZonedDateTime tomorrowTime2 = time2.plusDays(1L);
    private static final Block block2 = new Block(
            "Breakfast",
            "meal",
            0xffff00ff,
            tomorrowTime1,
            tomorrowTime2
    );

    @Override
    public List<Block> getAgenda() {
        return Collections.unmodifiableList(List.of(block1, block2));
    }
}