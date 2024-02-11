

package com.google.samples.apps.iosched.shared.model;

import org.threeten.bp.ZonedDateTime;

public class Block {

    private final String title;
    private final String type;
    private final int color;
    private final int strokeColor;
    private final boolean isDark;
    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;

    public Block(String title, String type, int color, ZonedDateTime startTime, ZonedDateTime endTime) {
        this(title, type, color, color, false, startTime, endTime);
    }

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