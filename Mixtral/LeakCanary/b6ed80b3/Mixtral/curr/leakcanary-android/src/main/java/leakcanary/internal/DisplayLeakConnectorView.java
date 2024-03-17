

package leakcanary.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;
import com.squareup.leakcanary.R;

public class DisplayLeakConnectorView extends View {

    private Paint classNamePaint;
    private Paint leakPaint;
    private Paint clearPaint;
    private Paint referencePaint;
    private float strokeSize;
    private float circleY;

    private Type type;
    private Bitmap cache;

    public enum Type {
        HELP,
        START,
        START_LAST_REACHABLE,
        NODE_UNKNOWN,
        NODE_FIRST_UNREACHABLE,
        NODE_UNREACHABLE,
        NODE_REACHABLE,
        NODE_LAST_REACHABLE,
        END,
        END_FIRST_UNREACHABLE
    }

    public DisplayLeakConnectorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources resources = context.getResources();

        type = Type.NODE_UNKNOWN;
        circleY = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_center_y);
        strokeSize = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_stroke_size);

        classNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        classNamePaint.setColor(resources.getColor(R.color.leak_canary_class_name));
        classNamePaint.setStrokeWidth(strokeSize);

        leakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leakPaint.setColor(resources.getColor(R.color.leak_canary_leak));
        leakPaint.setStyle(Paint.Style.STROKE);
        leakPaint.setStrokeWidth(strokeSize);
        final float pathLines = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_leak_dash_line);
        final float pathGaps = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_leak_dash_gap);
        leakPaint.setPathEffect(new DashPathEffect(new float[]{pathLines, pathGaps}, 0f));

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        referencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        referencePaint.setColor(resources.getColor(R.color.leak_canary_reference));
        referencePaint.setStrokeWidth(strokeSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();

        if (cache != null && (cache.getWidth() != width || cache.getHeight() != height)) {
            cache.recycle();
            cache = null;
        }

        if (cache == null) {
            cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            final Canvas cacheCanvas = new Canvas(cache);

            switch (type) {
                case NODE_UNKNOWN:
                    drawItems(cacheCanvas, leakPaint, leakPaint);
                    break;
                case NODE_UNREACHABLE:
                case NODE_REACHABLE:
                    drawItems(cacheCanvas, referencePaint, referencePaint);
                    break;
                case NODE_FIRST_UNREACHABLE:
                    drawItems(cacheCanvas, leakPaint, referencePaint);
                    break;
                case NODE_LAST_REACHABLE:
                    drawItems(cacheCanvas, referencePaint, leakPaint);
                    break;
                case START:
                    drawStartLine(cacheCanvas);
                    drawItems(cacheCanvas, null, referencePaint);
                    break;
                case START_LAST_REACHABLE:
                    drawStartLine(cacheCanvas);
                    drawItems(cacheCanvas, null, leakPaint);
                    break;
                case END:
                    drawItems(cacheCanvas, referencePaint, null);
                    break;
                case END_FIRST_UNREACHABLE:
                    drawItems(cacheCanvas, leakPaint, null);
                    break;
                case HELP:
                    drawRoot(cacheCanvas);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown type " + type);
            }
        }
        canvas.drawBitmap(cache, 0f, 0f, null);
    }

    private void drawStartLine(Canvas cacheCanvas) {
        final int width = getMeasuredWidth();
        final float halfWidth = width / 2f;
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, circleY, classNamePaint);
    }

    private void drawRoot(Canvas cacheCanvas) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        final float halfWidth = width / 2f;
        final float radiusClear = halfWidth - strokeSize / 2f;
        cacheCanvas.drawRect(0f, 0f, width, radiusClear, classNamePaint);
        cacheCanvas.drawCircle(0f, radiusClear, radiusClear, clearPaint);
        cacheCanvas.drawCircle(width, radiusClear, radiusClear, clearPaint);
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, height, classNamePaint);
    }

    private void drawItems(
            Canvas cacheCanvas,
            Paint arrowHeadPaint,
            Paint nextArrowPaint
    ) {
        if (arrowHeadPaint != null) {
            drawArrowHead(cacheCanvas, arrowHeadPaint);
        }
        if (nextArrowPaint != null) {
            drawNextArrowLine(cacheCanvas, nextArrowPaint);
        }
        drawInstanceCircle(cacheCanvas);
    }

    private void drawArrowHead(
            Canvas cacheCanvas,
            Paint paint
    ) {
        final int width = getMeasuredWidth();
        final float halfWidth = width / 2f;
        final float circleRadius = width / 3f;

        final float arrowHeight = halfWidth / 2 * (float) Math.sqrt(2);
        final float halfStrokeSize = strokeSize / 2;
        final float translateY = circleY - arrowHeight - circleRadius * 2 - strokeSize;

        final float lineYEnd = circleY - circleRadius - strokeSize / 2;
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, lineYEnd, paint);
        cacheCanvas.translate(halfWidth, translateY);
        cacheCanvas.rotate(45f);
        cacheCanvas.drawLine(
                0f, halfWidth, halfWidth + halfStrokeSize, halfWidth,
                paint
        );
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, halfWidth, paint);
        cacheCanvas.rotate(-45f);
        cacheCanvas.translate(-halfWidth, -translateY);
    }

    private void drawNextArrowLine(
            Canvas cacheCanvas,
            Paint paint
    ) {
        final int height = getMeasuredHeight();
        final int width = getMeasuredWidth();
        final float centerX = width / 2f;
        cacheCanvas.drawLine(centerX, circleY, centerX, height, paint);
    }

    private void drawInstanceCircle(Canvas cacheCanvas) {
        final int width = getMeasuredWidth();
        final float circleX = width / 2f;
        final float circleRadius = width / 3f;
        cacheCanvas.drawCircle(circleX, circleY, circleRadius, classNamePaint);
    }

    public void setType(Type type) {
        if (type != this.type) {
            this.type = type;
            if (cache != null) {
                cache.recycle();
                cache = null;
            }
            invalidate();
        }
    }

    static {
        final double SQRT_TWO = Math.sqrt(2);
        CLEAR_XFER_MODE = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
    }
}