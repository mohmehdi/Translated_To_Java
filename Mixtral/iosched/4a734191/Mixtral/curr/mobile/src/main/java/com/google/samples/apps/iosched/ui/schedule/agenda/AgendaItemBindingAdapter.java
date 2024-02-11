

package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.content.Context;
import android.databinding.BindingAdapter;
import android.graphics.drawable.GradientDrawable;
import android.support.v7.content.res.AppCompatResources;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.samples.apps.iosched.R;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

public class AgendaItemBindingAdapter {

    private static final DateTimeFormatter agendaTimePattern = DateTimeFormatter.ofPattern("h:mm a");

    @BindingAdapter(value = {"agendaColor", "agendaStrokeColor", "agendaStrokeWidth"}, requireAll = true)
    public static void agendaColor(View view, int fillColor, int strokeColor, float strokeWidth) {
        GradientDrawable drawable = (GradientDrawable) (view.getBackground() != null ? view.getBackground() : new GradientDrawable());
        drawable.setColor(fillColor);
        drawable.setStroke(Math.round(strokeWidth), strokeColor);
        view.setBackground(drawable);
    }

    @BindingAdapter("agendaIcon")
    public static void agendaIcon(ImageView imageView, String type) {
        int iconId = -1;
        switch (type) {
            case "after_hours":
                iconId = R.drawable.ic_agenda_after_hours;
                break;
            case "badge":
                iconId = R.drawable.ic_agenda_badge;
                break;
            case "codelab":
                iconId = R.drawable.ic_agenda_codelab;
                break;
            case "concert":
                iconId = R.drawable.ic_agenda_concert;
                break;
            case "keynote":
                iconId = R.drawable.ic_agenda_keynote;
                break;
            case "meal":
                iconId = R.drawable.ic_agenda_meal;
                break;
            case "office_hours":
                iconId = R.drawable.ic_agenda_office_hours;
                break;
            case "sandbox":
                iconId = R.drawable.ic_agenda_sandbox;
                break;
            case "store":
                iconId = R.drawable.ic_agenda_store;
                break;
            default:
                iconId = R.drawable.ic_agenda_session;
        }
        imageView.setImageDrawable(AppCompatResources.getDrawable(imageView.getContext(), iconId));
    }

    @BindingAdapter(value = {"startTime", "endTime"}, requireAll = true)
    public static void agendaDuration(TextView textView, ZonedDateTime startTime, ZonedDateTime endTime) {
        textView.setText(textView.getContext().getString(R.string.agenda_duration,
                agendaTimePattern.format(startTime),
                agendaTimePattern.format(endTime)));
    }
}