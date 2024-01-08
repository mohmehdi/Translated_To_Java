package com.google.samples.apps.iosched.shared.model;

import org.threeten.bp.ZonedDateTime;

public class Block {

    private String title;
    private String type;
    private int color;
    private int strokeColor;
    private boolean isDark;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;

    public Block(String title, String type, int color, int strokeColor, boolean isDark, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.title = title;
        this.type = type;
        this.color = color;
        this.strokeColor = strokeColor;
        this.isDark = isDark;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public int getColor() {
        return color;
    }

    public int getStrokeColor() {
        return strokeColor;
    }

    public boolean isDark() {
        return isDark;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }
}