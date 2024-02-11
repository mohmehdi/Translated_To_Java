

package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

public class RemoteAgendaDataSource implements AgendaDataSource {

    @Override
    public List<Block> getAgenda() {
        return AGENDA;
    }

    private static final List<Block> AGENDA = Arrays.asList(
            new Block(
                    "Breakfast",
                    "meal",
                    0xff31e7b6,
                    false,
                    ConferenceDay.DAY_1.start,
                    ConferenceDay.DAY_1.start.plusMinutes(150)),
            new Block(
                    "Badge pick-up",
                    "badge",
                    0xffe6e6e6,
                    false,
                    ConferenceDay.DAY_1.start,
                    ConferenceDay.DAY_1.start.plusHours(12)),
            new Block(
                    "Keynote",
                    "keynote",
                    0xfffcd230,
                    false,
                    ConferenceDay.DAY_1.start.plusHours(3),
                    ConferenceDay.DAY_1.start.plusMinutes(270)),
            new Block(
                    "Lunch",
                    "meal",
                    0xff31e7b6,
                    false,
                    ConferenceDay.DAY_1.start.plusMinutes(270),
                    ConferenceDay.DAY_1.start.plusMinutes(450)),
            new Block(
                    "Codelabs",
                    "codelab",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_1.start.plusMinutes(270),
                    ConferenceDay.DAY_1.start.plusMinutes(750)),
            new Block(
                    "Sandbox",
                    "sandbox",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_1.start.plusMinutes(270),
                    ConferenceDay.DAY_1.start.plusMinutes(750)),
            new Block(
                    "Office Hours & App Review",
                    "office_hours",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_1.start.plusMinutes(270),
                    ConferenceDay.DAY_1.start.plusMinutes(750)),
            new Block(
                    "I/O Store",
                    "store",
                    0xffffffff,
                    0xffff6c00,
                    false,
                    ConferenceDay.DAY_1.start.plusMinutes(270),
                    ConferenceDay.DAY_1.start.plusMinutes(750)),
            new Block(
                    "Keynote",
                    "keynote",
                    0xfffcd230,
                    false,
                    ConferenceDay.DAY_1.start.plusHours(5),
                    ConferenceDay.DAY_1.start.plusHours(6)),
            new Block(
                    "Sessions",
                    "session",
                    0xff27e5fd,
                    false,
                    ConferenceDay.DAY_1.start.plusHours(7),
                    ConferenceDay.DAY_1.start.plusHours(12)),
            new Block(
                    "After hours party",
                    "after_hours",
                    0xff202124,
                    true,
                    ConferenceDay.DAY_1.end.minusHours(3),
                    ConferenceDay.DAY_1.end),

            new Block(
                    "Breakfast",
                    "meal",
                    0xff31e7b6,
                    false,
                    ConferenceDay.DAY_2.start,
                    ConferenceDay.DAY_2.start.plusHours(2)),
            new Block(
                    "Badge & device pick-up",
                    "badge",
                    0xffe6e6e6,
                    false,
                    ConferenceDay.DAY_2.start,
                    ConferenceDay.DAY_2.start.plusHours(11)),
            new Block(
                    "I/O Store",
                    "store",
                    0xffffffff,
                    0xffff6c00,
                    false,
                    ConferenceDay.DAY_2.start,
                    ConferenceDay.DAY_2.start.plusHours(12)),
            new Block(
                    "Sessions",
                    "session",
                    0xff27e5fd,
                    false,
                    ConferenceDay.DAY_2.start.plusMinutes(30),
                    ConferenceDay.DAY_2.end.minusMinutes(150)),
            new Block(
                    "Codelabs",
                    "codelab",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_2.start.plusMinutes(30),
                    ConferenceDay.DAY_2.end.minusHours(2)),
            new Block(
                    "Sandbox",
                    "sandbox",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_2.start.plusMinutes(30),
                    ConferenceDay.DAY_2.end.minusHours(2)),
            new Block(
                    "Office Hours & App Review",
                    "office_hours",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_2.start.plusMinutes(30),
                    ConferenceDay.DAY_2.end.minusHours(2)),
            new Block(
                    "Lunch",
                    "meal",
                    0xff31e7b6,
                    false,
                    ConferenceDay.DAY_2.start.plusMinutes(210),
                    ConferenceDay.DAY_2.start.plusMinutes(390)),
            new Block(
                    "Concert",
                    "concert",
                    0xff202124,
                    true,
                    ConferenceDay.DAY_2.end.minusMinutes(90),
                    ConferenceDay.DAY_2.end),

            new Block(
                    "Breakfast",
                    "meal",
                    0xff31e7b6,
                    false,
                    ConferenceDay.DAY_3.start,
                    ConferenceDay.DAY_3.start.plusHours(2)),
            new Block(
                    "Badge & device pick-up",
                    "badge",
                    0xffe6e6e6,
                    false,
                    ConferenceDay.DAY_3.start,
                    ConferenceDay.DAY_3.end),
            new Block(
                    "I/O Store",
                    "store",
                    0xffffffff,
                    0xffff6c00,
                    false,
                    ConferenceDay.DAY_3.start,
                    ConferenceDay.DAY_3.end.plusHours(1)),
            new Block(
                    "Sessions",
                    "session",
                    0xff27e5fd,
                    false,
                    ConferenceDay.DAY_3.start.plusMinutes(30),
                    ConferenceDay.DAY_3.end.plusMinutes(30)),
            new Block(
                    "Codelabs",
                    "codelab",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_3.start.plusMinutes(30),
                    ConferenceDay.DAY_3.end),
            new Block(
                    "Sandbox",
                    "sandbox",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_3.start.plusMinutes(30),
                    ConferenceDay.DAY_3.end),
            new Block(
                    "Office Hours & App Review",
                    "office_hours",
                    0xff4768fd,
                    true,
                    ConferenceDay.DAY_3.start.plusMinutes(30),
                    ConferenceDay.DAY_3.end),
            new Block(
                    "Lunch",
                    "meal",
                    0xff31e7b6,
                    false,
                    ConferenceDay.DAY_3.start.plusMinutes(210),
                    ConferenceDay.DAY_3.start.plusMinutes(390))
    );
}