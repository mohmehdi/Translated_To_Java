package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.view.View;

import androidx.content.res.getColorOrThrow;
import androidx.content.res.getDimensionOrThrow;
import androidx.content.res.getDimensionPixelSizeOrThrow;
import androidx.content.res.getResourceIdOrThrow;
import androidx.graphics.withTranslation;
import androidx.text.inSpans;
import androidx.view.get;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Block;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleAgendaHeadersDecoration extends RecyclerView.ItemDecoration {

    private TextPaint textPaint;
    private Paint dividerPaint;
    private int width;
    private int padding;
    private int margin;
    private DateTimeFormatter dayFormatter;
    private DateTimeFormatter dateFormatter;
    private int dateTextSize;
    private Map<Integer, StaticLayout> daySlots;

    public ScheduleAgendaHeadersDecoration(Context context, List<Block> blocks) {
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint = new Paint();
        dayFormatter = DateTimeFormatter.ofPattern("eee");
        dateFormatter = DateTimeFormatter.ofPattern("d");

        Resources.Theme theme = context.getTheme();
        Resources res = context.getResources();
        int style = R.style.Widget_IOSched_DateHeaders;
        int[] attrs = new int[]{R.attr.DateHeader_android_textColor, R.attr.DateHeader_dayTextSize,
                R.attr.DateHeader_android_fontFamily, R.attr.DateHeader_android_divider,
                R.attr.DateHeader_android_dividerHeight, R.attr.DateHeader_android_width,
                R.attr.DateHeader_android_padding, R.attr.DateHeader_android_layout_margin,
                R.attr.DateHeader_dateTextSize};

        TypedArray typedArray = theme.obtainStyledAttributes(style, attrs);
        textPaint.setColor(typedArray.getColorOrThrow(0));
        textPaint.setTextSize(typedArray.getDimensionOrThrow(1));
        try {
            textPaint.setTypeface(ResourcesCompat.getFont(context, typedArray.getResourceIdOrThrow(2)));
        } catch (Resources.NotFoundException nfe) {
        }
        dividerPaint.setColor(typedArray.getColorOrThrow(3));
        dividerPaint.setStrokeWidth(typedArray.getDimensionOrThrow(4));
        width = typedArray.getDimensionPixelSizeOrThrow(5);
        padding = typedArray.getDimensionPixelSizeOrThrow(6);
        margin = typedArray.getDimensionPixelSizeOrThrow(7);
        dateTextSize = typedArray.getDimensionPixelSizeOrThrow(8);
        typedArray.recycle();

        daySlots = new HashMap<>();
        indexAgendaHeaders(blocks).forEach(block -> {
            daySlots.put(block.getFirst(), createHeader(block.getSecond()));
        });
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position <= 0) return;

        if (daySlots.containsKey(position)) {
            outRect.top = padding;
        } else if (daySlots.containsKey(position + 1)) {
            outRect.bottom = padding;
        }
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (daySlots.isEmpty()) return;

        int earliestFoundHeaderPos = -1;
        int prevHeaderTop = Integer.MAX_VALUE;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View view = parent.getChildAt(i);
            int viewTop = view.getTop() + (int) view.getTranslationY();
            if (view.getBottom() > 0 && viewTop < parent.getHeight()) {
                int position = parent.getChildAdapterPosition(view);
                if (daySlots.containsKey(position)) {
                    StaticLayout layout = daySlots.get(position);
                    int top = Math.max(viewTop + padding, padding);
                    top = Math.min(top, prevHeaderTop - layout.getHeight());
                    c.withTranslation(0, top).draw(layout);

                    if (position != 0) {
                        float dividerY = padding * -2f;
                        c.drawLine(0f, dividerY, parent.getWidth(), dividerY, dividerPaint);
                    }

                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop - padding - padding;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : daySlots.keySet()) {
            if (headerPos < earliestFoundHeaderPos) {
                StaticLayout layout = daySlots.get(headerPos);
                int top = Math.min(prevHeaderTop - layout.getHeight(), padding);
                c.withTranslation(0, top).draw(layout);
                break;
            }
        }
    }

    private StaticLayout createHeader(ZonedDateTime day) {
        SpannableStringBuilder text = new SpannableStringBuilder(dayFormatter.format(day).toUpperCase());
        text.append(System.lineSeparator());
        text.append(dateFormatter.format(day), new AbsoluteSizeSpan(dateTextSize), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        return new StaticLayout(text, textPaint, width - margin, Alignment.ALIGN_OPPOSITE, 1f, 0f, false);
    }
}