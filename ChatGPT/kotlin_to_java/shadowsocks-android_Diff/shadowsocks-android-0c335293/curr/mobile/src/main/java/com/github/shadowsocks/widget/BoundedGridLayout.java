package com.github.shadowsocks.widget;

import android.content.Context;
import android.support.v7.widget.GridLayout;
import android.util.AttributeSet;
import com.github.shadowsocks.R;

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

    public BoundedGridLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BoundedGridLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int newWidthSpec = boundedWidth <= 0 || boundedWidth >= MeasureSpec.getSize(widthSpec) ? widthSpec
                : MeasureSpec.makeMeasureSpec(boundedWidth, MeasureSpec.getMode(widthSpec));
        int newHeightSpec = boundedHeight <= 0 || boundedHeight >= MeasureSpec.getSize(heightSpec) ? heightSpec
                : MeasureSpec.makeMeasureSpec(boundedHeight, MeasureSpec.getMode(heightSpec));
        super.onMeasure(newWidthSpec, newHeightSpec);
    }
}