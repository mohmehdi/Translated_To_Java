package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.databinding.BindingAdapter;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

public class AgendaItemBindingAdapter {

    private static DateTimeFormatter agendaTimePattern = DateTimeFormatter.ofPattern("h:mm a");

    @BindingAdapter(value = {"agendaColor", "agendaStrokeColor", "agendaStrokeWidth"}, requireAll = true)
    public static void agendaColor(View view, int fillColor, int strokeColor, float strokeWidth) {
        view.setBackground((view.getBackground() instanceof GradientDrawable) ? (GradientDrawable) view.getBackground() : new GradientDrawable());
        GradientDrawable background = (GradientDrawable) view.getBackground();
        background.setColor(fillColor);
        background.setStroke((int) strokeWidth, strokeColor);
    }

    @BindingAdapter("agendaIcon")
    public static void agendaIcon(ImageView imageView, String type) {
        int iconId;
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
                break;
        }
        imageView.setImageDrawable(AppCompatResources.getDrawable(imageView.getContext(), iconId));
    }

    @BindingAdapter(value = {"startTime", "endTime", "timeZoneId"}, requireAll = true)
    public static void agendaDuration(TextView textView, ZonedDateTime startTime, ZonedDateTime endTime, ZoneId timeZoneId) {
        textView.setText(textView.getContext().getString(
                R.string.agenda_duration,
                agendaTimePattern.format(TimeUtils.zonedTime(startTime, timeZoneId)),
                agendaTimePattern.format(TimeUtils.zonedTime(endTime, timeZoneId))
        ));
    }
}