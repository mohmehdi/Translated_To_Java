

package com.squareup.leakcanary.internal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import com.squareup.leakcanary.R;

public class RowElementLayout extends ViewGroup {

  private int connectorWidth;
  private int rowMargins;
  private int moreSize;
  private int minHeight;
  private int titleMarginTop;
  private int moreMarginTop;

  private View connector;
  private View moreButton;
  private View title;
  private View details;

  public RowElementLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    initializeDimensions(context);
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
    final int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
    final int titleWidth = availableWidth - connectorWidth - moreSize - 4 * rowMargins;
    final int titleWidthSpec = MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.AT_MOST);
    final int titleHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    title.measure(titleWidthSpec, titleHeightSpec);

    final int moreSizeSpec = MeasureSpec.makeMeasureSpec(moreSize, MeasureSpec.EXACTLY);
    moreButton.measure(moreSizeSpec, moreSizeSpec);

    int totalHeight = titleMarginTop + title.getMeasuredHeight();

    final int detailsWidth = availableWidth - connectorWidth - 3 * rowMargins;
    final int detailsWidthSpec = MeasureSpec.makeMeasureSpec(detailsWidth, MeasureSpec.AT_MOST);
    final int detailsHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    details.measure(detailsWidthSpec, detailsHeightSpec);
    if (details.getVisibility() != View.GONE) {
      totalHeight += details.getMeasuredHeight();
    }
    totalHeight = Math.max(totalHeight, minHeight);

    final int connectorWidthSpec = MeasureSpec.makeMeasureSpec(connectorWidth, MeasureSpec.EXACTLY);
    final int connectorHeightSpec = MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY);

    connector.measure(connectorWidthSpec, connectorHeightSpec);
    setMeasuredDimension(availableWidth, totalHeight);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int width = measuredWidth;
    final int connectorRight = rowMargins + connector.getMeasuredWidth();
    connector.layout(rowMargins, 0, connectorRight, connector.getMeasuredHeight());

    moreButton.layout(
        width - rowMargins - moreSize, moreMarginTop, width - rowMargins,
        moreMarginTop + moreSize
    );

    final int titleLeft = connectorRight + rowMargins;
    final int titleBottom = titleMarginTop + title.getMeasuredHeight();
    title.layout(titleLeft, titleMarginTop, titleLeft + title.getMeasuredWidth(), titleBottom);

    if (details.getVisibility() != View.GONE) {
      details.layout(
          titleLeft, titleBottom, width - rowMargins,
          titleBottom + details.getMeasuredHeight()
      );
    }
  }
}