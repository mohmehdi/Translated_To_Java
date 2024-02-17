package com.github.shadowsocks.widget;

import android.content.Context;
import android.support.v7.widget.GridLayout;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.github.shadowsocks.R;

public class BoundedGridLayout extends GridLayout {
    private int boundedWidth;
    private int boundedHeight;

    public BoundedGridLayout(Context context) {
        super(context);
    }

    public BoundedGridLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init(context, attrs, 0, R.style.Widget_Shadowsocks_BoundedGridLayout);
    }

    public BoundedGridLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(
                boundedWidth <= 0 || boundedWidth >= MeasureSpec.getSize(widthSpec)
                        ? widthSpec
                        : MeasureSpec.makeMeasureSpec(boundedWidth, MeasureSpec.getMode(widthSpec)),
                boundedHeight <= 0 || boundedHeight >= MeasureSpec.getSize(heightSpec)
                        ? heightSpec
                        : MeasureSpec.makeMeasureSpec(boundedHeight, MeasureSpec.getMode(heightSpec)));
    }
}