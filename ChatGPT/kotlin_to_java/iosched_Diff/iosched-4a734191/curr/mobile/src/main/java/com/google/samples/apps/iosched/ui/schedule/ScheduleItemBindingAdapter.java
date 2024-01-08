package com.google.samples.apps.iosched.ui.schedule;

import android.content.Context;
import android.widget.TextView;
import com.google.samples.apps.iosched.R;
import org.threeten.bp.Duration;
import org.threeten.bp.ZonedDateTime;

public class ScheduleItemBindingAdapter {

    @BindingAdapter(value = {"sessionStart", "sessionEnd", "sessionRoom"}, requireAll = true)
    public static void sessionLengthLocation(
            TextView textView,
            ZonedDateTime startTime,
            ZonedDateTime endTime,
            String room
    ) {
        textView.setText(textView.getContext().getString(
                R.string.session_duration_location,
                durationString(textView.getContext(), Duration.between(startTime, endTime)), room
        ));
    }

    private static String durationString(Context context, Duration duration) {
        long hours = duration.toHours();
        if (hours > 0L) {
            return context.getResources().getQuantityString(R.plurals.duration_hours, (int) hours, hours);
        } else {
            long minutes = duration.toMinutes();
            return context.getResources().getQuantityString(R.plurals.duration_minutes, (int) minutes, minutes);
        }
    }
}