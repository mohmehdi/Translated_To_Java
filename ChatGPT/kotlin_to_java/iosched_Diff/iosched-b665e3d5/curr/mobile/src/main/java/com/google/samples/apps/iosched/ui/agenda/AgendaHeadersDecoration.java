package com.google.samples.apps.iosched.ui.agenda;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.content.res.TypedArrayUtils;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.State;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;

public class AgendaHeadersDecoration extends ItemDecoration {

    private final TextPaint paint;
    private final int width;
    private final int paddingTop;
    private final DateTimeFormatter dateFormatter;
    private final DateTimeFormatter dayFormatter;
    private final int dayTextSize;

    private final Map<Integer, StaticLayout> daySlots;

    public AgendaHeadersDecoration(Context context, List<Block> blocks) {
        TypedArray attrs = context.obtainStyledAttributes(
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
        } catch (Exception ignored) {
        }

        width = attrs.getDimensionPixelSizeOrThrow(R.styleable.DateHeader_android_width);
        paddingTop = attrs.getDimensionPixelSizeOrThrow(R.styleable.DateHeader_android_paddingTop);
        dayTextSize = attrs.getDimensionPixelSizeOrThrow(R.styleable.DateHeader_dayTextSize);
        attrs.recycle();

        dateFormatter = DateTimeFormatter.ofPattern("d");
        dayFormatter = DateTimeFormatter.ofPattern("eee");

        daySlots = indexAgendaHeaders(blocks);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position <= 0) return;

        if (daySlots.containsKey(position)) {
            outRect.top = paddingTop;
        } else if (daySlots.containsKey(position + 1)) {
            outRect.bottom = paddingTop;
        }
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, State state) {
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
                    if (layout != null) {
                        paint.setAlpha((int) (view.getAlpha() * 255));
                        int top = Math.min(Math.max(viewTop + paddingTop, paddingTop), prevHeaderTop - layout.getHeight());
                        c.translate(0, top);
                        layout.draw(c);
                        earliestFoundHeaderPos = position;
                        prevHeaderTop = viewTop - paddingTop - paddingTop;
                        c.translate(0, -top);
                    }
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : daySlots.keySet()) {
            if (headerPos < earliestFoundHeaderPos) {
                StaticLayout layout = daySlots.get(headerPos);
                if (layout != null) {
                    int top = Math.min(prevHeaderTop - layout.getHeight(), paddingTop);
                    c.translate(0, top);
                    layout.draw(c);
                    c.translate(0, -top);
                }
                break;
            }
        }
    }

    private StaticLayout createHeader(ZonedDateTime day) {
        SpannableStringBuilder text = new SpannableStringBuilder(dateFormatter.format(day));
        text.append(System.lineSeparator());
        text.append(dayFormatter.format(day).toUpperCase(), new AbsoluteSizeSpan(dayTextSize), new StyleSpan(StyleSpan.BOLD));

        return new StaticLayout(text, paint, width, Alignment.ALIGN_CENTER, 1f, 0f, false);
    }

}
