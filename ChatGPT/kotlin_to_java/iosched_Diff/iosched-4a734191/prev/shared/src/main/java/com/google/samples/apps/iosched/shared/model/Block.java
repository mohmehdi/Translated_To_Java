package com.google.samples.apps.iosched.shared.model;

import org.threeten.bp.ZonedDateTime;

public class Block {

    private String title;
    private String subtitle;
    private String kind;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;

    public Block(String title, String subtitle, String kind, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.title = title;
        this.subtitle = subtitle;
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getKind() {
        return kind;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }
}