package com.google.samples.apps.iosched.shared.util.testdata;

import android.graphics.Color;
import com.google.samples.apps.iosched.shared.util.ColorUtils;
import timber.log.Timber;

public class ColorDeserializer {

    public static int parseColor(String colorString) {
        if (colorString != null) {
            try {
                return ColorUtils.parseHexColor(colorString);
            } catch (Throwable t) {
                Timber.d(t, "Failed to parse color");
                return Color.TRANSPARENT;
            }
        } else {
            return Color.TRANSPARENT;
        }
    }
}