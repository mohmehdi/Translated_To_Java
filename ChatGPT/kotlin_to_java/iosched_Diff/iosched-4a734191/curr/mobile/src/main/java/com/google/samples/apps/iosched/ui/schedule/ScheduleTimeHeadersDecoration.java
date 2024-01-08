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
import androidx.content.res.getResourceIdOrThrow;
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
        paint.setColor(res.getColorOrThrow(R.styleable.TimeHeader_android_textColor));
        paint.setTextSize(res.getDimensionOrThrow(R.styleable.TimeHeader_hourTextSize));
        try {
            paint.setTypeface(ResourcesCompat.getFont(
                    context,
                    res.getResourceIdOrThrow(R.styleable.TimeHeader_android_fontFamily)
            ));
        } catch (Resources.NotFoundException nfe) {
        }
        width = res.getDimensionPixelSizeOrThrow(R.styleable.TimeHeader_android_width);
        paddingTop = res.getDimensionPixelSizeOrThrow(R.styleable.TimeHeader_android_paddingTop);
        meridiemTextSize = res.getDimensionPixelSizeOrThrow(R.styleable.TimeHeader_meridiemTextSize);
        hourFormatter = DateTimeFormatter.ofPattern("h");
        meridiemFormatter = DateTimeFormatter.ofPattern("a");

        timeSlots = new HashMap<>();
        indexSessionHeaders(sessions).forEach(it -> {
            timeSlots.put(it.getFirst(), createHeader(it.getSecond()));
        });
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
                    StaticLayout layout = timeSlots.get(position);
                    int top = Math.max(viewTop + paddingTop, paddingTop);
                    top = Math.min(top, prevHeaderTop - layout.getHeight());
                    c.withTranslation(0, top).draw(layout);
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
                StaticLayout layout = timeSlots.get(headerPos);
                int top = Math.min(prevHeaderTop - layout.getHeight(), paddingTop);
                c.withTranslation(0, top).draw(layout);
                break;
            }
        }
    }

    private StaticLayout createHeader(ZonedDateTime startTime) {
        SpannableStringBuilder text = new SpannableStringBuilder(hourFormatter.format(startTime));
        text.append(System.lineSeparator());
        text.append(meridiemFormatter.format(startTime).toUpperCase());
        text.setSpan(new AbsoluteSizeSpan(meridiemTextSize), text.length() - 2, text.length(), 0);
        text.setSpan(new StyleSpan(Typeface.BOLD), text.length() - 2, text.length(), 0);
        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
    }

    private List<Pair<Integer, ZonedDateTime>> indexSessionHeaders(List<Session> sessions) {
        // Implementation not provided
        return null;
    }
}