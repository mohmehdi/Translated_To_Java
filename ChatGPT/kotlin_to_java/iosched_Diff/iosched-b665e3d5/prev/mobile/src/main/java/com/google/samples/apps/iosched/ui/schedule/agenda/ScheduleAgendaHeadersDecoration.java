package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.content.res.TypedArrayUtils;
import androidx.core.graphics.withTranslation;
import androidx.core.text.inSpans;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;

public class ScheduleAgendaHeadersDecoration extends RecyclerView.ItemDecoration {

    private final TextPaint paint;
    private final int width;
    private final int paddingTop;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d");
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("eee");
    private final int dayTextSize;

    private final Map<Integer, StaticLayout> daySlots;

    public ScheduleAgendaHeadersDecoration(Context context, List<Block> blocks) {
        android.content.res.TypedArray attrs = context.obtainStyledAttributes(
                R.style.Widget_IOSched_DateHeaders,
                R.styleable.DateHeader
        );
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(attrs.getColorOrThrow(R.styleable.DateHeader_android_textColor));
        paint.setTextSize(attrs.getDimensionOrThrow(R.styleable.DateHeader_dateTextSize));
        try {
            paint.setTypeface(ResourcesCompat.getFont(
                    context,
                    attrs.getResourceIdOrThrow(R.styleable.DateHeader_android_fontFamily)
            ));
        } catch (Exception e) {
            
        }
        width = attrs.getDimensionPixelSizeOrThrow(R.styleable.DateHeader_android_width);
        paddingTop = attrs.getDimensionPixelSizeOrThrow(R.styleable.DateHeader_android_paddingTop);
        dayTextSize = attrs.getDimensionPixelSizeOrThrow(R.styleable.DateHeader_dayTextSize);
        attrs.recycle();

        daySlots = indexAgendaHeaders(blocks).stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> createHeader(entry.getValue())
                ));
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position <= 0) return;

        if (daySlots.containsKey(position)) {
            outRect.top = paddingTop;
        } else if (daySlots.containsKey(position + 1)) {
            outRect.bottom = paddingTop;
        }
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (daySlots.isEmpty() || ViewCompat.isEmpty(parent)) return;

        int earliestFoundHeaderPos = -1;
        int prevHeaderTop = Integer.MAX_VALUE;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View view = parent.getChildAt(i);
            int viewTop = view.getTop() + (int) view.getTranslationY();
            if (view.getBottom() > 0 && viewTop < parent.getHeight()) {
                int position = parent.getChildAdapterPosition(view);
                if (daySlots.containsKey(position)) {
                    StaticLayout layout = daySlots.get(position);
                    paint.setAlpha((int) (view.getAlpha() * 255));
                    int top = Math.min(Math.max(viewTop + paddingTop, paddingTop), prevHeaderTop - layout.getHeight());
                    c.withTranslation(0, top).drawText(layout);
                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop - paddingTop - paddingTop;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : daySlots.keySet()) {
            if (headerPos < earliestFoundHeaderPos) {
                StaticLayout layout = daySlots.get(headerPos);
                int top = Math.min(prevHeaderTop - layout.getHeight(), paddingTop);
                c.withTranslation(0, top).drawText(layout);
                break;
            }
        }
    }

    private StaticLayout createHeader(ZonedDateTime day) {
        SpannableStringBuilder text = new SpannableStringBuilder(dateFormatter.format(day));
        text.append(System.lineSeparator());
        text.inSpans(new AbsoluteSizeSpan(dayTextSize), new StyleSpan(Typeface.BOLD), () ->
                text.append(dayFormatter.format(day).toUpperCase())
        );
        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
    }
}