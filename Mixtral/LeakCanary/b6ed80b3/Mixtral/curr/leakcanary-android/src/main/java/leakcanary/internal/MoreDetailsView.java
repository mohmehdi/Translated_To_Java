

package leakcanary.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.squareup.leakcanary.R;

public class MoreDetailsView extends View {

    private Paint iconPaint;
    private boolean opened;

    public MoreDetailsView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources resources = getResources();
        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int strokeSize = resources.getDimensionPixelSize(R.dimen.leak_canary_more_stroke_width);
        iconPaint.setStrokeWidth(strokeSize);

        @SuppressLint("CustomViewStyleable")
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.leak_canary_MoreDetailsView);
        final int plusColor = a.getColor(R.styleable.leak_canary_MoreDetailsView_leak_canary_plus_color, Color.BLACK);
        a.recycle();

        iconPaint.setColor(plusColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int width = getWidth();
        final int height = getHeight();
        final int halfHeight = height / 2;
        final int halfWidth = width / 2;

        if (opened) {
            canvas.drawLine(0f, halfHeight, width, halfHeight, iconPaint);
        } else {
            canvas.drawLine(0f, halfHeight, width, halfHeight, iconPaint);
            canvas.drawLine(halfWidth, 0f, halfWidth, height, iconPaint);
        }
    }

    public void setOpened(boolean opened) {
        if (opened != this.opened) {
            this.opened = opened;
            invalidate();
        }
    }
}