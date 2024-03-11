package com.google.samples.apps.iosched.shared.model;

import org.threeten.bp.ZonedDateTime;

public class Block {

    public final String title;
    public final String subtitle;
    public final String kind;
    public final ZonedDateTime startTime;
    public final ZonedDateTime endTime;

    public Block(String title, String subtitle, String kind, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.title = title;
        this.subtitle = subtitle;
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}