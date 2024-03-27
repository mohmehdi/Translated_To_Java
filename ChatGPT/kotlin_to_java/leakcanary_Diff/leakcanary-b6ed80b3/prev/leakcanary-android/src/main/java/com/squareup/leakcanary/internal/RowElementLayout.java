
package com.squareup.leakcanary.internal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.leakcanary.R;

public class RowElementLayout extends ViewGroup {

    private final int connectorWidth;
    private final int rowMargins;
    private final int moreSize;
    private final int minHeight;
    private final int titleMarginTop;
    private final int moreMarginTop;

    private View connector;
    private View moreButton;
    private View title;
    private View details;

    public RowElementLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        int connectorWidth;
        int rowMargins;
        int moreSize;
        int minHeight;
        int titleMarginTop;
        int moreMarginTop;

        connectorWidth = getResources().getDimensionPixelSize(R.dimen.leak_canary_connector_width);
        rowMargins = getResources().getDimensionPixelSize(R.dimen.leak_canary_row_margins);
        moreSize = getResources().getDimensionPixelSize(R.dimen.leak_canary_more_size);
        minHeight = getResources().getDimensionPixelSize(R.dimen.leak_canary_row_min);
        titleMarginTop = getResources().getDimensionPixelSize(R.dimen.leak_canary_row_title_margin_top);
        moreMarginTop = getResources().getDimensionPixelSize(R.dimen.leak_canary_more_margin_top);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        connector = findViewById(R.id.leak_canary_row_connector);
        moreButton = findViewById(R.id.leak_canary_row_more);
        title = findViewById(R.id.leak_canary_row_title);
        details = findViewById(R.id.leak_canary_row_details);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        int titleWidth = availableWidth - connectorWidth - moreSize - 4 * rowMargins;
        int titleWidthSpec = View.MeasureSpec.makeMeasureSpec(titleWidth, View.MeasureSpec.AT_MOST);
        int titleHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        title.measure(titleWidthSpec, titleHeightSpec);

        int moreSizeSpec = View.MeasureSpec.makeMeasureSpec(moreSize, View.MeasureSpec.EXACTLY);
        moreButton.measure(moreSizeSpec, moreSizeSpec);

        int totalHeight = titleMarginTop + title.getMeasuredHeight();

        int detailsWidth = availableWidth - connectorWidth - 3 * rowMargins;
        int detailsWidthSpec = View.MeasureSpec.makeMeasureSpec(detailsWidth, View.MeasureSpec.AT_MOST);
        int detailsHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        details.measure(detailsWidthSpec, detailsHeightSpec);
        if (details.getVisibility() != View.GONE) {
            totalHeight += details.getMeasuredHeight();
        }
        totalHeight = Math.max(totalHeight, minHeight);

        int connectorWidthSpec = View.MeasureSpec.makeMeasureSpec(connectorWidth, View.MeasureSpec.EXACTLY);
        int connectorHeightSpec = View.MeasureSpec.makeMeasureSpec(totalHeight, View.MeasureSpec.EXACTLY);

        connector.measure(connectorWidthSpec, connectorHeightSpec);
        setMeasuredDimension(availableWidth, totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = getMeasuredWidth();
        int connectorRight = rowMargins + connector.getMeasuredWidth();
        connector.layout(rowMargins, 0, connectorRight, connector.getMeasuredHeight());

        moreButton.layout(
                width - rowMargins - moreSize, moreMarginTop, width - rowMargins,
                moreMarginTop + moreSize
        );

        int titleLeft = connectorRight + rowMargins;
        int titleBottom = titleMarginTop + title.getMeasuredHeight();
        title.layout(titleLeft, titleMarginTop, titleLeft + title.getMeasuredWidth(), titleBottom);

        if (details.getVisibility() != View.GONE) {
            details.layout(
                    titleLeft, titleBottom, width - rowMargins,
                    titleBottom + details.getMeasuredHeight()
            );
        }
    }
}