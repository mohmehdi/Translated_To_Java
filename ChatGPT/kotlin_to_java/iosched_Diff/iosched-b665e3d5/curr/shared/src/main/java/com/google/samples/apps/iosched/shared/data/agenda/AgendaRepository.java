package com.google.samples.apps.iosched.shared.data.agenda;

import com.google.samples.apps.iosched.model.Block;
import org.threeten.bp.ZonedDateTime;

import java.util.List;

public interface AgendaRepository {
    List<Block> getAgenda();
}

public class DefaultAgendaRepository implements AgendaRepository {
    private final List<Block> blocks = new ArrayList<Block>() {{
        add(new Block("Breakfast", "meal", 0xff31e7b6, ZonedDateTime.parse("2018-05-08T07:00-07:00"), ZonedDateTime.parse("2018-05-08T09:30-07:00")));
        add(new Block("Badge pick-up", "badge", 0xffe6e6e6, ZonedDateTime.parse("2018-05-08T07:00-07:00"), ZonedDateTime.parse("2018-05-08T19:00-07:00")));
        add(new Block("Keynote", "keynote", 0xfffcd230, ZonedDateTime.parse("2018-05-08T10:00-07:00"), ZonedDateTime.parse("2018-05-08T11:30-07:00")));
        add(new Block("Lunch", "meal", 0xff31e7b6, ZonedDateTime.parse("2018-05-08T11:30-07:00"), ZonedDateTime.parse("2018-05-08T14:30-07:00")));
        add(new Block("Codelabs", "codelab", 0xff4768fd, true, ZonedDateTime.parse("2018-05-08T11:30-07:00"), ZonedDateTime.parse("2018-05-08T12:30-07:00")));
        add(new Block("Office Hours & App Review", "office_hours", 0xff4768fd, true, ZonedDateTime.parse("2018-05-08T11:30-07:00"), ZonedDateTime.parse("2018-05-08T12:30-07:00")));
        add(new Block("I/O Store", "store", 0xffffffff, 0xffff6c00, ZonedDateTime.parse("2018-05-08T11:30-07:00"), ZonedDateTime.parse("2018-05-08T19:30-07:00")));
        add(new Block("Keynote", "keynote", 0xfffcd230, ZonedDateTime.parse("2018-05-08T12:45-07:00"), ZonedDateTime.parse("2018-05-08T13:45-07:00")));
        add(new Block("Sessions", "session", 0xff27e5fd, ZonedDateTime.parse("2018-05-08T14:00-07:00"), ZonedDateTime.parse("2018-05-08T19:00-07:00")));
        add(new Block("Codelabs", "codelab", 0xff4768fd, true, ZonedDateTime.parse("2018-05-08T14:00-07:00"), ZonedDateTime.parse("2018-05-08T19:30-07:00")));
        add(new Block("Sandbox", "sandbox", 0xff4768fd, true, ZonedDateTime.parse("2018-05-08T14:00-07:00"), ZonedDateTime.parse("2018-05-08T19:30-07:00")));
        add(new Block("Office Hours & App Review", "office_hours", 0xff4768fd, true, ZonedDateTime.parse("2018-05-08T14:00-07:00"), ZonedDateTime.parse("2018-05-08T19:30-07:00")));
        add(new Block("After hours party", "after_hours", 0xff202124, true, ZonedDateTime.parse("2018-05-08T19:00-07:00"), ZonedDateTime.parse("2018-05-08T22:00-07:00")));
        add(new Block("Breakfast", "meal", 0xff31e7b6, ZonedDateTime.parse("2018-05-09T08:00-07:00"), ZonedDateTime.parse("2018-05-09T10:00-07:00")));
        add(new Block("Badge & device pick-up", "badge", 0xffe6e6e6, ZonedDateTime.parse("2018-05-09T08:00-07:00"), ZonedDateTime.parse("2018-05-09T19:00-07:00")));
        add(new Block("I/O Store", "store", 0xffffffff, 0xffff6c00, ZonedDateTime.parse("2018-05-09T08:00-07:00"), ZonedDateTime.parse("2018-05-09T20:00-07:00")));
        add(new Block("Sessions", "session", 0xff27e5fd, ZonedDateTime.parse("2018-05-09T08:30-07:00"), ZonedDateTime.parse("2018-05-09T19:30-07:00")));
        add(new Block("Codelabs", "codelab", 0xff4768fd, true, ZonedDateTime.parse("2018-05-09T08:30-07:00"), ZonedDateTime.parse("2018-05-09T20:00-07:00")));
        add(new Block("Sandbox", "sandbox", 0xff4768fd, true, ZonedDateTime.parse("2018-05-09T08:30-07:00"), ZonedDateTime.parse("2018-05-09T20:00-07:00")));
        add(new Block("Office Hours & App Review", "office_hours", 0xff4768fd, true, ZonedDateTime.parse("2018-05-09T08:30-07:00"), ZonedDateTime.parse("2018-05-09T20:00-07:00")));
        add(new Block("Lunch", "meal", 0xff31e7b6, ZonedDateTime.parse("2018-05-09T11:30-07:00"), ZonedDateTime.parse("2018-05-09T14:30-07:00")));
        add(new Block("Concert", "concert", 0xff202124, true, ZonedDateTime.parse("2018-05-09T19:30-07:00"), ZonedDateTime.parse("2018-05-09T22:00-07:00")));
        add(new Block("Breakfast", "meal", 0xff31e7b6, ZonedDateTime.parse("2018-05-10T08:00-07:00"), ZonedDateTime.parse("2018-05-10T10:00-07:00")));
        add(new Block("Badge & device pick-up", "badge", 0xffe6e6e6, ZonedDateTime.parse("2018-05-10T08:00-07:00"), ZonedDateTime.parse("2018-05-10T16:00-07:00")));
        add(new Block("I/O Store", "store", 0xffffffff, 0xffff6c00, ZonedDateTime.parse("2018-05-10T08:00-07:00"), ZonedDateTime.parse("2018-05-10T17:00-07:00")));
        add(new Block("Sessions", "session", 0xff27e5fd, ZonedDateTime.parse("2018-05-10T08:30-07:00"), ZonedDateTime.parse("2018-05-10T16:30-07:00")));
        add(new Block("Codelabs", "codelab", 0xff4768fd, true, ZonedDateTime.parse("2018-05-10T08:30-07:00"), ZonedDateTime.parse("2018-05-10T16:00-07:00")));
        add(new Block("Sandbox", "sandbox", 0xff4768fd, true, ZonedDateTime.parse("2018-05-10T08:30-07:00"), ZonedDateTime.parse("2018-05-10T16:00-07:00")));
        add(new Block("Office Hours & App Review", "office_hours", 0xff4768fd, true, ZonedDateTime.parse("2018-05-10T08:30-07:00"), ZonedDateTime.parse("2018-05-10T16:00-07:00")));
        add(new Block("Lunch", "meal", 0xff31e7b6, ZonedDateTime.parse("2018-05-10T11:30-07:00"), ZonedDateTime.parse("2018-05-10T14:30-07:00")));
    }};

    @Override
    public List<Block> getAgenda() {
        return blocks;
    }
}