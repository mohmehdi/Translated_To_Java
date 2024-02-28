

package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.model.Block;
import org.threeten.bp.ZonedDateTime;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Arrays;

@Singleton
public class AgendaRepository {
    private List<Block> blocks;

    @Inject
    public AgendaRepository() {
        blocks = Arrays.asList(
                new Block(
                        "Breakfast",
                        "meal",
                        0xff31e7b6,
                        ZonedDateTime.parse("2018-05-08T07:00-07:00"),
                        ZonedDateTime.parse("2018-05-08T09:30-07:00")
                ),
                // ... rest of the Block instances
        );
    }

    public List<Block> getAgenda() {
        return blocks;
    }
}