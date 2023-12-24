
package com.google.samples.apps.sunflower.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.google.android.material.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.ShapeAppearancePathProvider;

public class MaskedCardView extends MaterialCardView {
    @SuppressLint("RestrictedApi")
    private ShapeAppearancePathProvider pathProvider = new ShapeAppearancePathProvider();
    private Path path = new Path();
    private ShapeAppearanceModel shapeAppearance;
    private RectF rectF = new RectF(0f, 0f, 0f, 0f);

    public MaskedCardView(Context context) {
        super(context);
        init(context, null, R.attr.materialCardViewStyle);
    }

    public MaskedCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, R.attr.materialCardViewStyle);
    }

    public MaskedCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        shapeAppearance = ShapeAppearanceModel.builder(
                context,
                attrs,
                defStyle,
                R.style.Widget_MaterialComponents_CardView
        ).build();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.clipPath(path);
        super.onDraw(canvas);
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        rectF.right = w;
        rectF.bottom = h;
        pathProvider.calculatePath(shapeAppearance, 1f, rectF, path);
        super.onSizeChanged(w, h, oldw, oldh);
    }
}
