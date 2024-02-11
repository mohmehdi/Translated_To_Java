

package com.google.samples.apps.iosched.ui.schedule;

import android.content.Context;
import android.databinding.BindingAdapter;
import android.widget.TextView;
import com.google.samples.apps.iosched.R;
import org.threeten.bp.Duration;
import org.threeten.bp.ZonedDateTime;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.PluralFormatException;
import java.util.ResourceBundle;

public class ScheduleItemBindingAdapter {

    @BindingAdapter(value = {"sessionStart", "sessionEnd", "sessionRoom"}, requireAll = true)
    public static void sessionLengthLocation(TextView textView, ZonedDateTime startTime, ZonedDateTime endTime, String room) {
        String durationString = durationString(textView.getContext(), Duration.between(startTime, endTime));
        String format = textView.getContext().getString(R.string.session_duration_location);
        MessageFormat messageFormat = new MessageFormat(format, Locale.getDefault());
        Object[] args = {durationString, room};
        try {
            messageFormat.format(args);
        } catch (PluralFormatException e) {
            e.printStackTrace();
        }
        textView.setText(messageFormat.format(args));
    }

    private static String durationString(Context context, Duration duration) {
        long hours = duration.toHours();
        ResourceBundle bundle = ResourceBundle.getBundle(context.getResources().getResourcePackageName(R.plurals.duration_hours), context.getResources().getConfiguration().locale);
        if (hours > 0) {
            return bundle.getString("duration_hours");
        } else {
            long minutes = duration.toMinutes();
            bundle = ResourceBundle.getBundle(context.getResources().getResourcePackageName(R.plurals.duration_minutes), context.getResources().getConfiguration().locale);
            return bundle.getString("duration_minutes");
        }
    }
}