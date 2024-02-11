

package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.support.annotation.ColorInt;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.State;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.view.View;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorRes;
import androidx.annotation.Dimen;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Block;
import java.text.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScheduleAgendaHeadersDecoration extends ItemDecoration {

    private final TextPaint textPaint;
    private final Paint dividerPaint;
    private final int width;
    private final int padding;
    private final int margin;
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("eee", Locale.US);
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d", Locale.US);
    private final int dateTextSize;

    public ScheduleAgendaHeadersDecoration(Context context, List<Block> blocks) {
        final Resources.Theme theme = context.getTheme();
        final TypedArray attrs = theme.obtainStyledAttributes(
                R.style.Widget_IOSched_DateHeaders,
                R.styleable.DateHeader);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(attrs.getColor(R.styleable.DateHeader_android_textColor, 0));
        textPaint.setTextSize(attrs.getDimension(R.styleable.DateHeader_dayTextSize, 0));
        final int fontResId = attrs.getResourceId(R.styleable.DateHeader_android_fontFamily, 0);
        if (fontResId != 0) {
            textPaint.setTypeface(AppCompatResources.getFont(context, fontResId));
        }

        dividerPaint = new Paint();
        dividerPaint.setColor(attrs.getColor(R.styleable.DateHeader_android_divider, 0));
        dividerPaint.setStrokeWidth(attrs.getDimension(R.styleable.DateHeader_android_dividerHeight, 0));

        width = attrs.getDimensionPixelSize(R.styleable.DateHeader_android_width, 0);
        padding = attrs.getDimensionPixelSize(R.styleable.DateHeader_android_padding, 0);
        margin = attrs.getDimensionPixelSize(R.styleable.DateHeader_android_layout_margin, 0);
        dateTextSize = attrs.getDimensionPixelSize(R.styleable.DateHeader_dateTextSize, 0);

        attrs.recycle();

        final List<Map.Entry<Integer, StaticLayout>> daySlots = new ArrayList<>();
        for (final Map.Entry<Integer, ZonedDateTime> entry : indexAgendaHeaders(blocks).entrySet()) {
            daySlots.add(Map.entry(entry.getKey(), createHeader(entry.getValue())));
        }
        this.daySlots = new HashMap<>(daySlots);
    }

    private final Map<Integer, StaticLayout> daySlots;

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
        final int position = parent.getChildAdapterPosition(view);
        if (position <= 0) return;

        if (daySlots.containsKey(position)) {
            outRect.top = padding;
        } else if (daySlots.containsKey(position + 1)) {
            outRect.bottom = padding;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @Nullable State state) {
        if (daySlots.isEmpty()) return;

        int earliestFoundHeaderPos = -1;
        int prevHeaderTop = Integer.MAX_VALUE;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            final View view = parent.getChildAt(i);
            final int viewTop = view.getTop() + view.getTranslationY().intValue();
            if (view.getBottom() > 0 && viewTop < parent.getHeight()) {
                final int position = parent.getChildAdapterPosition(view);
                if (daySlots.containsKey(position)) {
                    final StaticLayout layout = daySlots.get(position);
                    final int top = Math.max(padding, Math.min(prevHeaderTop - layout.getHeight(), viewTop + padding));
                    c.save();
                    c.translate(0, top);
                    layout.draw(c);
                    if (position != 0) {
                        c.drawLine(0, -2 * padding, parent.getWidth(), -2 * padding, dividerPaint);
                    }
                    c.restore();
                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop - padding - padding;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : daySlots.keySet().descendingSet()) {
            if (headerPos < earliestFoundHeaderPos) {
                final StaticLayout layout = daySlots.get(headerPos);
                final int top = Math.max(padding, prevHeaderTop - layout.getHeight());
                c.save();
                c.translate(0, top);
                layout.draw(c);
                c.restore();
                break;
            }
        }
    }

    private StaticLayout createHeader(@NonNull ZonedDateTime day) {
        final SpannableStringBuilder text = new SpannableStringBuilder(dayFormatter.format(day).toUpperCase());
        text.append("\n");
        text.setSpan(new AbsoluteSizeSpan(dateTextSize), text.length() - dateFormatter.toString().length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(dateFormatter.format(day));

        return new StaticLayout(text, textPaint, width - margin, Layout.Alignment.ALIGN_OPPOSITE, 1.0f, 0.0f, false);
    }


}