package com.github.shadowsocks.widget;

import android.content.Context;
import android.support.v7.widget.GridLayout;
import android.util.AttributeSet;

public class BoundedGridLayout extends GridLayout {
    private int boundedWidth;
    private int boundedHeight;

    public BoundedGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs) {
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BoundedGridLayout);
        boundedWidth = arr.getDimensionPixelSize(R.styleable.BoundedGridLayout_bounded_width, 0);
        boundedHeight = arr.getDimensionPixelSize(R.styleable.BoundedGridLayout_bounded_height, 0);
        arr.recycle();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);

        if (boundedWidth > 0 && boundedWidth < width) {
            width = boundedWidth;
        }

        if (boundedHeight > 0 && boundedHeight < height) {
            height = boundedHeight;
        }

        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(widthSpec)),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.getMode(heightSpec))
        );
    }
}