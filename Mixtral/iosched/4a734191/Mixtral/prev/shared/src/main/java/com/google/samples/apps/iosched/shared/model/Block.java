package com.google.samples.apps.iosched.shared.model;

import org.threeten.bp.ZonedDateTime;

import java.io.Serializable;

public class Block implements Serializable {

    private final String title;
    private final String subtitle;
    private final String kind;
    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;

    public Block(String title, String subtitle, String kind, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.title = title;
        this.subtitle = subtitle;
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
    }

   
}