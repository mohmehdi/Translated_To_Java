

package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.State;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleAgendaHeadersDecoration implements ItemDecoration {

    private final TextPaint paint;
    private final int width;
    private final int paddingTop;
    private final DateTimeFormatter dateFormatter;
    private final DateTimeFormatter dayFormatter;
    private final int dayTextSize;
    private final Map<Integer, StaticLayout> daySlots;

    public ScheduleAgendaHeadersDecoration(Context context, List<Block> blocks) {
        int[] attrs = context.obtainStyledAttributes(
                R.style.Widget_IOSched_DateHeaders,
                R.styleable.DateHeader);

        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(attrs.getColor(R.styleable.DateHeader_android_textColor));
        paint.setTextSize(attrs.getDimension(R.styleable.DateHeader_dateTextSize, 16));

        Typeface typeface = ResourcesCompat.getFont(context, attrs.getResourceId(
                R.styleable.DateHeader_android_fontFamily, -1));
        if (typeface != null) {
            paint.setTypeface(typeface);
        }

        width = attrs.getDimensionPixelSize(R.styleable.DateHeader_android_width, 0);
        paddingTop = attrs.getDimensionPixelSize(R.styleable.DateHeader_android_paddingTop, 0);
        dayTextSize = attrs.getDimensionPixelSize(R.styleable.DateHeader_dayTextSize, 0);

        attrs.recycle();

        List<Map.Entry<Integer, String>> indexedAgendaHeaders = indexAgendaHeaders(blocks);
        daySlots = new HashMap<>();
        for (Map.Entry<Integer, String> entry : indexedAgendaHeaders) {
            daySlots.put(entry.getKey(), createHeader(entry.getValue()));
        }
    }



    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position <= 0) {
            return;
        }

        if (daySlots.containsKey(position)) {
            outRect.top = paddingTop;
        } else if (daySlots.containsKey(position + 1)) {
            outRect.bottom = paddingTop;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull State state) {
        if (daySlots.isEmpty() || parent.isEmpty()) {
            return;
        }

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
                    int top = Math.max(paddingTop, Math.min(prevHeaderTop - layout.getHeight(), viewTop + paddingTop));
                    c.withTranslation(top, 0).draw(layout);
                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop - paddingTop - paddingTop;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : daySlots.keySet().descendingSet()) {
            if (headerPos < earliestFoundHeaderPos) {
                StaticLayout layout = daySlots.get(headerPos);
                int top = Math.max(paddingTop, Math.min(prevHeaderTop - layout.getHeight(), paddingTop));
                c.withTranslation(top, 0).draw(layout);
            }
        }
    }
    private StaticLayout createHeader(ZonedDateTime day) {
        SpannableStringBuilder text = new SpannableStringBuilder(dateFormatter.format(day));
        text.append(System.lineSeparator());
        text.append(dateFormatter.format(day).toUpperCase());
        text.setSpan(new AbsoluteSizeSpan(dayTextSize), text.length() - dateFormatter.format(day).length(), text.length(), 0);
        text.setSpan(new StyleSpan(Typeface.BOLD), text.length() - dateFormatter.format(day).length(), text.length(), 0);

        return new StaticLayout(text, paint, width, Alignment.ALIGN_CENTER, 1.0f, 0.0f, true);
    }

}