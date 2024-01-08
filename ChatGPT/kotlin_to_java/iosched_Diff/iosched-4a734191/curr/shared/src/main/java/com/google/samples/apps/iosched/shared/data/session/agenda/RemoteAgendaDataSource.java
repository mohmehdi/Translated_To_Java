package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;

import java.util.ArrayList;
import java.util.List;

public class RemoteAgendaDataSource implements AgendaDataSource {

    @Override
    public List<Block> getAgenda() {
        return AGENDA;
    }

    private static final List<Block> AGENDA = new ArrayList<Block>() {{
        add(new Block(
                "Breakfast",
                "meal",
                0xff31e7b6,
                false,
                ConferenceDay.DAY_1.start,
                ConferenceDay.DAY_1.start.plusMinutes(150L)));
        add(new Block(
                "Badge pick-up",
                "badge",
                0xffe6e6e6,
                false,
                ConferenceDay.DAY_1.start,
                ConferenceDay.DAY_1.start.plusHours(12L)));
        add(new Block(
                "Keynote",
                "keynote",
                0xfffcd230,
                false,
                ConferenceDay.DAY_1.start.plusHours(3L),
                ConferenceDay.DAY_1.start.plusMinutes(270L)));
        add(new Block(
                "Lunch",
                "meal",
                0xff31e7b6,
                false,
                ConferenceDay.DAY_1.start.plusMinutes(270L),
                ConferenceDay.DAY_1.start.plusMinutes(450L)));
        add(new Block(
                "Codelabs",
                "codelab",
                0xff4768fd,
                true,
                ConferenceDay.DAY_1.start.plusMinutes(270L),
                ConferenceDay.DAY_1.start.plusMinutes(750L)));
        add(new Block(
                "Sandbox",
                "sandbox",
                0xff4768fd,
                true,
                ConferenceDay.DAY_1.start.plusMinutes(270L),
                ConferenceDay.DAY_1.start.plusMinutes(750L)));
        add(new Block(
                "Office Hours & App Review",
                "office_hours",
                0xff4768fd,
                true,
                ConferenceDay.DAY_1.start.plusMinutes(270L),
                ConferenceDay.DAY_1.start.plusMinutes(750L)));
        add(new Block(
                "I/O Store",
                "store",
                0xffffffff,
                0xffff6c00,
                false,
                ConferenceDay.DAY_1.start.plusMinutes(270L),
                ConferenceDay.DAY_1.start.plusMinutes(750L)));
        add(new Block(
                "Keynote",
                "keynote",
                0xfffcd230,
                false,
                ConferenceDay.DAY_1.start.plusHours(5L),
                ConferenceDay.DAY_1.start.plusHours(6L)));
        add(new Block(
                "Sessions",
                "session",
                0xff27e5fd,
                false,
                ConferenceDay.DAY_1.start.plusHours(7L),
                ConferenceDay.DAY_1.start.plusHours(12L)));
        add(new Block(
                "After hours party",
                "after_hours",
                0xff202124,
                true,
                ConferenceDay.DAY_1.end.minusHours(3L),
                ConferenceDay.DAY_1.end)));

        add(new Block(
                "Breakfast",
                "meal",
                0xff31e7b6,
                false,
                ConferenceDay.DAY_2.start,
                ConferenceDay.DAY_2.start.plusHours(2L)));
        add(new Block(
                "Badge & device pick-up",
                "badge",
                0xffe6e6e6,
                false,
                ConferenceDay.DAY_2.start,
                ConferenceDay.DAY_2.start.plusHours(11L)));
        add(new Block(
                "I/O Store",
                "store",
                0xffffffff,
                0xffff6c00,
                false,
                ConferenceDay.DAY_2.start,
                ConferenceDay.DAY_2.start.plusHours(12L)));
        add(new Block(
                "Sessions",
                "session",
                0xff27e5fd,
                false,
                ConferenceDay.DAY_2.start.plusMinutes(30L),
                ConferenceDay.DAY_2.end.minusMinutes(150L)));
        add(new Block(
                "Codelabs",
                "codelab",
                0xff4768fd,
                true,
                ConferenceDay.DAY_2.start.plusMinutes(30L),
                ConferenceDay.DAY_2.end.minusHours(2L)));
        add(new Block(
                "Sandbox",
                "sandbox",
                0xff4768fd,
                true,
                ConferenceDay.DAY_2.start.plusMinutes(30L),
                ConferenceDay.DAY_2.end.minusHours(2L)));
        add(new Block(
                "Office Hours & App Review",
                "office_hours",
                0xff4768fd,
                true,
                ConferenceDay.DAY_2.start.plusMinutes(30L),
                ConferenceDay.DAY_2.end.minusHours(2L)));
        add(new Block(
                "Lunch",
                "meal",
                0xff31e7b6,
                false,
                ConferenceDay.DAY_2.start.plusMinutes(210L),
                ConferenceDay.DAY_2.start.plusMinutes(390L)));
        add(new Block(
                "Concert",
                "concert",
                0xff202124,
                true,
                ConferenceDay.DAY_2.end.minusMinutes(90L),
                ConferenceDay.DAY_2.end)));

        add(new Block(
                "Breakfast",
                "meal",
                0xff31e7b6,
                false,
                ConferenceDay.DAY_3.start,
                ConferenceDay.DAY_3.start.plusHours(2L)));
        add(new Block(
                "Badge & device pick-up",
                "badge",
                0xffe6e6e6,
                false,
                ConferenceDay.DAY_3.start,
                ConferenceDay.DAY_3.end));
        add(new Block(
                "I/O Store",
                "store",
                0xffffffff,
                0xffff6c00,
                false,
                ConferenceDay.DAY_3.start,
                ConferenceDay.DAY_3.end.plusHours(1L)));
        add(new Block(
                "Sessions",
                "session",
                0xff27e5fd,
                false,
                ConferenceDay.DAY_3.start.plusMinutes(30L),
                ConferenceDay.DAY_3.end.plusMinutes(30L)));
        add(new Block(
                "Codelabs",
                "codelab",
                0xff4768fd,
                true,
                ConferenceDay.DAY_3.start.plusMinutes(30L),
                ConferenceDay.DAY_3.end));
        add(new Block(
                "Sandbox",
                "sandbox",
                0xff4768fd,
                true,
                ConferenceDay.DAY_3.start.plusMinutes(30L),
                ConferenceDay.DAY_3.end));
        add(new Block(
                "Office Hours & App Review",
                "office_hours",
                0xff4768fd,
                true,
                ConferenceDay.DAY_3.start.plusMinutes(30L),
                ConferenceDay.DAY_3.end));
        add(new Block(
                "Lunch",
                "meal",
                0xff31e7b6,
                false,
                ConferenceDay.DAY_3.start.plusMinutes(210L),
                ConferenceDay.DAY_3.start.plusMinutes(390L)));
    }};
}