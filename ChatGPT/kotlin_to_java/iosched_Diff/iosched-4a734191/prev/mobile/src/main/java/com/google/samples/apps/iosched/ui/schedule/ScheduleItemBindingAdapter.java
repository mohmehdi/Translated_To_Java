package com.google.samples.apps.iosched.ui.schedule;

import android.content.Context;
import android.widget.TextView;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.util.duration;
import org.threeten.bp.Duration;

public class ScheduleItemBindingAdapter {

    @BindingAdapter("sessionLengthLocation")
    public static void sessionLengthLocation(TextView textView, Session session) {
        textView.setText(textView.getContext().getString(R.string.session_duration_location,
                durationString(textView.getContext(), session.getDuration()), session.getRoom().getName()));
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