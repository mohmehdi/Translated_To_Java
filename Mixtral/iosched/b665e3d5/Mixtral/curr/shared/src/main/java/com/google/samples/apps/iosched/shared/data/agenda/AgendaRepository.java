

package com.google.samples.apps.iosched.shared.data.agenda;

import com.google.samples.apps.iosched.model.Block;
import org.threeten.bp.ZonedDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;

public interface AgendaRepository {
List getAgenda();
}
public class DefaultAgendaRepository implements AgendaRepository {
    private List<Block> blocks;

    public DefaultAgendaRepository() {
        blocks = new ArrayList<>();
        blocks.add(new Block(
                "Breakfast",
                "meal",
                0xff31e7b6,
                ZonedDateTime.parse("2018-05-08T07:00-07:00"),
                ZonedDateTime.parse("2018-05-08T09:30-07:00")
        ));
        // Add more Block objects here, same as the Kotlin version

        // In case you need an immutable list, use the following line instead of the above block-adding code
        // blocks = Collections.unmodifiableList(blocks);
    }

    @Override
    public List<Block> getAgenda() {
        return blocks;
    }
}