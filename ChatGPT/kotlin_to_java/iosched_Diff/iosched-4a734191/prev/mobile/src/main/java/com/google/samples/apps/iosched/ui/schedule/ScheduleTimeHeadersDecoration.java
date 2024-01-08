package com.google.samples.apps.iosched.ui.schedule;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import androidx.content.res.getColorOrThrow;
import androidx.content.res.getDimensionOrThrow;
import androidx.content.res.getDimensionPixelSizeOrThrow;
import androidx.graphics.withTranslation;
import androidx.text.inSpans;
import androidx.view.get;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Session;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleTimeHeadersDecoration extends RecyclerView.ItemDecoration {

    private TextPaint paint;
    private int width;
    private int paddingTop;
    private int meridiemTextSize;
    private DateTimeFormatter hourFormatter;
    private DateTimeFormatter meridiemFormatter;
    private Map<Integer, StaticLayout> timeSlots;

    public ScheduleTimeHeadersDecoration(Context context, List<Session> sessions) {
        Resources res = context.getResources();
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(res.getColorOrThrow(R.color.time_header_text_color));
        paint.setTextSize(res.getDimensionOrThrow(R.dimen.time_header_hour_text_size));
        try {
            paint.setTypeface(ResourcesCompat.getFont(context, R.font.time_header_font_family));
        } catch (Resources.NotFoundException nfe) {
        }
        width = res.getDimensionPixelSizeOrThrow(R.dimen.time_header_width);
        paddingTop = res.getDimensionPixelSizeOrThrow(R.dimen.time_header_padding_top);
        meridiemTextSize = res.getDimensionPixelSizeOrThrow(R.dimen.time_header_meridiem_text_size);
        hourFormatter = DateTimeFormatter.ofPattern("h");
        meridiemFormatter = DateTimeFormatter.ofPattern("a");

        timeSlots = new HashMap<>();
        indexSessionHeaders(sessions);
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (timeSlots.isEmpty()) return;

        int earliestFoundHeaderPos = -1;
        int prevHeaderTop = Integer.MAX_VALUE;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            RecyclerView.ViewHolder viewHolder = parent.getChildViewHolder(parent.getChildAt(i));
            int viewTop = viewHolder.itemView.getTop() + (int) viewHolder.itemView.getTranslationY();
            if (viewHolder.itemView.getBottom() > 0 && viewTop < parent.getHeight()) {
                int position = viewHolder.getAdapterPosition();
                if (timeSlots.containsKey(position)) {
                    StaticLayout headerLayout = timeSlots.get(position);
                    int top = Math.max(viewTop + paddingTop, paddingTop);
                    top = Math.min(top, prevHeaderTop - headerLayout.getHeight());
                    c.withTranslation(0, top, 0) {
                        headerLayout.draw(c);
                    }
                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : timeSlots.keySet()) {
            if (headerPos < earliestFoundHeaderPos) {
                StaticLayout headerLayout = timeSlots.get(headerPos);
                int top = Math.min(prevHeaderTop - headerLayout.getHeight(), paddingTop);
                c.withTranslation(0, top, 0) {
                    headerLayout.draw(c);
                }
                break;
            }
        }
    }

    private void indexSessionHeaders(List<Session> sessions) {
        for (int i = 0; i < sessions.size(); i++) {
            Session session = sessions.get(i);
            ZonedDateTime startTime = session.getStartTime();
            StaticLayout headerLayout = createHeader(startTime);
            timeSlots.put(i, headerLayout);
        }
    }

    private StaticLayout createHeader(ZonedDateTime startTime) {
        SpannableStringBuilder text = new SpannableStringBuilder(hourFormatter.format(startTime));
        text.append('\n');
        text.inSpans(new AbsoluteSizeSpan(meridiemTextSize), new StyleSpan(Typeface.BOLD), () -> {
            text.append(meridiemFormatter.format(startTime).toUpperCase());
        });
        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
    }
}