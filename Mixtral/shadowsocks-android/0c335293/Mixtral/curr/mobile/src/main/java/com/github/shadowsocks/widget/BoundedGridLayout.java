package com.github.shadowsocks.widget;

import android.content.Context;
import android.support.v7.widget.GridLayout;
import android.util.AttributeSet;

public class BoundedGridLayout extends GridLayout {
    private int boundedWidth;
    private int boundedHeight;

    public BoundedGridLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BoundedGridLayout, defStyleAttr, defStyleRes);
        boundedWidth = arr.getDimensionPixelSize(R.styleable.BoundedGridLayout_bounded_width, 0);
        boundedHeight = arr.getDimensionPixelSize(R.styleable.BoundedGridLayout_bounded_height, 0);
        arr.recycle();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(
                boundedWidth <= 0 || boundedWidth >= MeasureSpec.getSize(widthSpec)
                        ? widthSpec
                        : MeasureSpec.makeMeasureSpec(boundedWidth, MeasureSpec.getMode(widthSpec)),
                boundedHeight <= 0 || boundedHeight >= MeasureSpec.getSize(heightSpec)
                        ? heightSpec
                        : MeasureSpec.makeMeasureSpec(boundedHeight, MeasureSpec.getMode(heightSpec))
        );
    }
}