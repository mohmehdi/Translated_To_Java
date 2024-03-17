

package leakcanary.internal;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;
import android.text.style.UnderlineSpan;
import com.squareup.leakcanary.R;

public class SquigglySpan implements ReplacementSpan {

    private final Paint squigglyPaint;
    private final Path path;
    private final int referenceColor;
    private final float halfStrokeWidth;
    private final float amplitude;
    private final float halfWaveHeight;
    private final float periodDegrees;
    private int width;

    public SquigglySpan(Resources resources) {
        squigglyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        squigglyPaint.setStyle(Paint.Style.STROKE);
        squigglyPaint.setColor(resources.getColor(R.color.leak_canary_leak));
        float strokeWidth = resources.getDimensionPixelSize(R.dimen.leak_canary_squiggly_span_stroke_width);
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, strokeWidth, resources.getDisplayMetrics());
        squigglyPaint.setStrokeWidth(strokeWidth);

        halfStrokeWidth = strokeWidth / 2;
        amplitude = resources.getDimensionPixelSize(R.dimen.leak_canary_squiggly_span_amplitude);
        periodDegrees = resources.getDimensionPixelSize(R.dimen.leak_canary_squiggly_span_period_degrees);
        path = new Path();
        float waveHeight = 2 * amplitude + strokeWidth;
        halfWaveHeight = waveHeight / 2;
        referenceColor = resources.getColor(R.color.leak_canary_reference);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        width = Math.round(paint.measureText(text, start, end));
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        squigglyHorizontalPath(path, x + halfStrokeWidth, x + width - halfStrokeWidth, bottom - halfWaveHeight, amplitude, periodDegrees);
        canvas.drawPath(path, squigglyPaint);

        paint.setColor(referenceColor);
        canvas.drawText(text, start, end, x, y, paint);
    }

    public static void replaceUnderlineSpans(SpannableStringBuilder builder, Resources resources) {
        UnderlineSpan[] underlineSpans = builder.getSpans(0, builder.length(), UnderlineSpan.class);
        for (UnderlineSpan span : underlineSpans) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            builder.removeSpan(span);
            builder.setSpan(new SquigglySpan(resources), start, end, 0);
        }
    }

    private static void squigglyHorizontalPath(Path path, float left, float right, float centerY, float amplitude, float periodDegrees) {
        path.reset();

        float y;
        path.moveTo(left, centerY);
        float period = (2 * (float) Math.PI / periodDegrees);

        float x = 0f;
        while (x <= right - left) {
            y = (amplitude * (float) Math.sin((40 + period * x) * Math.PI / 180) + centerY);
            path.lineTo(left + x, y);
            x += 1f;
        }
    }
}