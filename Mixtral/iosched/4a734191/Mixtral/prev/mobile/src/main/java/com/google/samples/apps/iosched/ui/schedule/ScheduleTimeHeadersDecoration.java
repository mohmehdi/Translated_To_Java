

package com.google.samples.apps.iosched.ui.schedule;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontResourceEntry;
import android.support.annotation.ColorInt;
import android.support.annotation.Dimension;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.FontRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.util.DateTimeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class ScheduleTimeHeadersDecoration extends RecyclerView.ItemDecoration {

    private final TextPaint paint;
    private final int width;
    private final int paddingTop;
    private final int meridiemTextSize;
    private final DateTimeFormatter hourFormatter;
    private final DateTimeFormatter meridiemFormatter;

    public ScheduleTimeHeadersDecoration(Context context, List<Session> sessions) {
        Resources.Theme theme = context.getTheme();
        TypedArray attrs = theme.obtainStyledAttributes(R.style.Widget_IOSched_TimeHeaders,
                new int[]{R.styleable.TimeHeader_android_textColor,
                        R.styleable.TimeHeader_hourTextSize,
                        R.styleable.TimeHeader_android_fontFamily,
                        R.styleable.TimeHeader_android_width,
                        R.styleable.TimeHeader_android_paddingTop,
                        R.styleable.TimeHeader_meridiemTextSize});

        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(attrs.getColor(R.styleable.TimeHeader_android_textColor, 0));
        paint.setTextSize(attrs.getDimension(R.styleable.TimeHeader_hourTextSize, 0));

        Typeface typeface = null;
        try {
            int fontResId = attrs.getResourceId(R.styleable.TimeHeader_android_fontFamily, 0);
            if (fontResId != 0) {
                List<FontResourceEntry> fonts =
                        context.getFonts().getFont(context.getResources(), fontResId);
                if (fonts != null && !fonts.isEmpty()) {
                    typeface = Font.createFromFontInfo(context.getResources(), fonts.get(0),
                            attrs.getDimensionPixelSize(R.styleable.TimeHeader_android_fontSize, 0),
                            Collections.emptyList());
                }
            }
        } catch (Resources.NotFoundException e) {
            // Ignore
        }
        if (typeface != null) {
            paint.setTypeface(typeface);
        }

        width = attrs.getDimensionPixelSize(R.styleable.TimeHeader_android_width, 0);
        paddingTop = attrs.getDimensionPixelSize(R.styleable.TimeHeader_android_paddingTop, 0);
        meridiemTextSize = attrs.getDimensionPixelSize(R.styleable.TimeHeader_meridiemTextSize, 0);
        attrs.recycle();

        List<Integer> sessionHeaderPositions = indexSessionHeaders(sessions);
        Map<Integer, StaticLayout> timeSlots = new HashMap<>();
        for (int i = 0; i < sessionHeaderPositions.size(); i++) {
            int position = sessionHeaderPositions.get(i);
            Session session = sessions.get(position);
            ZonedDateTime startTime = DateTimeUtils.toZonedDateTime(session.getStartTime());
            timeSlots.put(position, createHeader(startTime));
        }
        this.timeSlots = timeSlots;

        hourFormatter = DateTimeFormatter.ofPattern("h");
        meridiemFormatter = DateTimeFormatter.ofPattern("a");
    }

    private final Map<Integer, StaticLayout> timeSlots;

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
            @Nullable RecyclerView.State state) {
        if (timeSlots.isEmpty()) {
            return;
        }

        int earliestFoundHeaderPos = -1;
        int prevHeaderTop = Integer.MAX_VALUE;

        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            RecyclerView.ViewHolder viewHolder = parent.getChildViewHolder(parent.getChildAt(i));
            int viewTop = parent.getChildAt(i).getTop() + parent.getChildAt(i).getTranslationY();
            if (parent.getChildAt(i).getBottom() > 0 && viewTop < parent.getHeight()) {
                int position = parent.getChildAdapterPosition(parent.getChildAt(i));
                if (timeSlots.containsKey(position)) {
                    StaticLayout timeSlot = timeSlots.get(position);
                    int top = Math.max(viewTop + paddingTop, paddingTop)
                            .coerceAtMost(prevHeaderTop - timeSlot.getHeight());
                    c.save();
                    c.translate(0, top);
                    timeSlot.draw(c);
                    c.restore();
                    earliestFoundHeaderPos = position;
                    prevHeaderTop = viewTop;
                }
            }
        }

        if (earliestFoundHeaderPos < 0) {
            earliestFoundHeaderPos = parent.getChildAdapterPosition(parent.getChildAt(0)) + 1;
        }

        for (int headerPos : timeSlots.keySet().descendingSet()) {
            if (headerPos < earliestFoundHeaderPos) {
                StaticLayout timeSlot = timeSlots.get(headerPos);
                int top = Math.max(prevHeaderTop - timeSlot.getHeight(), paddingTop);
                c.save();
                c.translate(0, top);
                timeSlot.draw(c);
                c.restore();
            }
        }
    }

    private StaticLayout createHeader(ZonedDateTime startTime) {
        String hour = hourFormatter.format(startTime);
        String meridiem = meridiemFormatter.format(startTime).toUpperCase();
        SpannableStringBuilder text = new SpannableStringBuilder(hour);
        text.append('\n');
        text.setSpan(new AbsoluteSizeSpan(meridiemTextSize), text.length() - 1, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), text.length() - 1, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f,
                false);
    }

 
}