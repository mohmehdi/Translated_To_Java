

package com.google.samples.apps.iosched.ui.schedule;

import android.content.Context;
import android.databinding.BindingAdapter;
import android.widget.TextView;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.util.duration;
import java.time.Duration;
import java.util.Locale;
import java.util.ResourceBundle;

public class ScheduleItemBindingAdapter {

    @BindingAdapter("sessionLengthLocation")
    public static void sessionLengthLocation(TextView textView, Session session) {
        Context context = textView.getContext();
        String durationString = durationString(context, session.getDuration());
        String roomName = session.getRoom().getName();
        textView.setText(context.getString(R.string.session_duration_location, durationString, roomName));
    }

    private static String durationString(Context context, Duration duration) {
        long hours = Duration.between(duration, Duration.ZERO).toHours();
        Locale locale = context.getResources().getConfiguration().getLocale();
        ResourceBundle bundle = ResourceBundle.getBundle("com.google.samples.apps.iosched.R", locale);
        int hoursPlurals = Integer.parseInt(bundle.getString("plurals/duration_hours"));
        int minutesPlurals = Integer.parseInt(bundle.getString("plurals/duration_minutes"));
        if (hours > 0) {
            return String.format(locale, bundle.getString(hoursPlurals), hours, hours);
        } else {
            long minutes = duration.toMinutes();
            return String.format(locale, bundle.getString(minutesPlurals), minutes, minutes);
        }
    }
}