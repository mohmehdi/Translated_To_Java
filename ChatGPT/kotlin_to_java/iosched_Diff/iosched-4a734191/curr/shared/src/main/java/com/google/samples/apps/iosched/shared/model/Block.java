package com.google.samples.apps.iosched.shared.model;

import org.threeten.bp.ZonedDateTime;

public class Block {

    public final String title;
    public final String type;
    public final int color;
    public final int strokeColor;
    public final boolean isDark;
    public final ZonedDateTime startTime;
    public final ZonedDateTime endTime;

    public Block(String title, String type, int color, int strokeColor, boolean isDark, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.title = title;
        this.type = type;
        this.color = color;
        this.strokeColor = strokeColor;
        this.isDark = isDark;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}