

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
import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.content.res.ColorResource;
import androidx.content.res.ConfigurationAwareResources;
import androidx.content.res.FontResource;
import androidx.content.res.ResourcesCompatKt;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.util.DateTimeUtilsKt;
import java.text.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleTimeHeadersDecoration extends RecyclerView.ItemDecoration {

    private final Paint paint;
    private final int width;
    private final int paddingTop;
    private final int meridiemTextSize;
    private final DateTimeFormatter hourFormatter;
    private final DateTimeFormatter meridiemFormatter;

    public ScheduleTimeHeadersDecoration(Context context, List<Session> sessions) {
        ConfigurationAwareResources resources = new ConfigurationAwareResources(context);
        int[] timeHeaderStyleable = resources.obtainStyleable(R.style.Widget_IOSched_TimeHeaders, R.styleable.TimeHeader);

        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(resources.getColorOrThrow(timeHeaderStyleable, R.styleable.TimeHeader_android_textColor));
        paint.setTextSize(resources.getDimensionOrThrow(timeHeaderStyleable, R.styleable.TimeHeader_hourTextSize));

        @StyleRes int fontResId = timeHeaderStyleable.getResourceIdOrThrow(R.styleable.TimeHeader_android_fontFamily);
        try {
            Typeface typeface = ResourcesCompat.getFont(context, fontResId);
            paint.setTypeface(typeface);
        } catch (Resources.NotFoundException e) {
            // Handle exception or ignore
        }

        width = resources.getDimensionPixelSizeOrThrow(timeHeaderStyleable, R.styleable.TimeHeader_android_width);
        paddingTop = resources.getDimensionPixelSizeOrThrow(timeHeaderStyleable, R.styleable.TimeHeader_android_paddingTop);
        meridiemTextSize = resources.getDimensionPixelSizeOrThrow(timeHeaderStyleable, R.styleable.TimeHeader_meridiemTextSize);

        hourFormatter = DateTimeFormatter.ofPattern("h");
        meridiemFormatter = DateTimeFormatter.ofPattern("a");

        timeHeaderStyleable.recycle();

        Map<Integer, StaticLayout> timeSlots = indexSessionHeaders(sessions).entrySet().stream()
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), createHeader(entry.getValue())), HashMap::putAll);
        this.timeSlots = timeSlots;
    }


    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @Nullable RecyclerView.State state) {
        if (timeSlots.isEmpty()) return;

        int earliestFoundHeaderPos = -1;
        int prevHeaderTop = Integer.MAX_VALUE;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            RecyclerView.ViewHolder viewHolder = parent.getChildViewHolder(parent.getChildAt(i));
            int viewTop = viewHolder.itemView.getTop() + viewHolder.itemView.getTranslationY();
            if (viewHolder.itemView.getBottom() > 0 && viewTop < parent.getHeight()) {
                int position = parent.getChildAdapterPosition(viewHolder.itemView);
                if (timeSlots.containsKey(position)) {
                    StaticLayout timeSlot = timeSlots.get(position);
                    int top = Math.max(paddingTop, Math.min(prevHeaderTop - timeSlot.getHeight(), viewTop + paddingTop));
                    c.withTranslation(top, (float) timeSlot.getHeight());
                    timeSlot.draw(c);
                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (Map.Entry<Integer, StaticLayout> entry : timeSlots.entrySet().stream().sorted(Map.Entry.<Integer, StaticLayout>comparingByKey().reversed()).collect(HashMap::new, (map, entry1) -> {
            if (entry1.getKey() < earliestFoundHeaderPos) {
                map.put(entry1.getKey(), entry1.getValue());
            }
        }, HashMap::putAll).entrySet()) {
            StaticLayout timeSlot = entry.getValue();
            int top = Math.max(paddingTop, Math.min(prevHeaderTop - timeSlot.getHeight(), paddingTop));
            c.withTranslation(top, (float) timeSlot.getHeight());
            timeSlot.draw(c);
        }
    }

    private StaticLayout createHeader(ZonedDateTime startTime) {
        String text = hourFormatter.format(startTime) + System.lineSeparator() +
                meridiemFormatter.format(startTime).toUpperCase();

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        spannableStringBuilder.setSpan(new AbsoluteSizeSpan(meridiemTextSize), text.length() - meridiemFormatter.format(startTime).length(), text.length(), 0);
        spannableStringBuilder.setSpan(new StyleSpan(Typeface.BOLD), text.length() - meridiemFormatter.format(startTime).length(), text.length(), 0);

        return new StaticLayout(spannableStringBuilder, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
    }
}