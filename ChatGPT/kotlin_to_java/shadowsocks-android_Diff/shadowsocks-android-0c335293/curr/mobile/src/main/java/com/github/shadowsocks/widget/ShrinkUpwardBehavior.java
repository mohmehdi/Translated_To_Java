package com.github.shadowsocks.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.SnackbarAnimation;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

public class ShrinkUpwardBehavior extends CoordinatorLayout.Behavior<View> {
    private AccessibilityManager accessibility;

    public ShrinkUpwardBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        accessibility = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
        child.getLayoutParams().height = (int) dependency.getY();
        child.requestLayout();
        return true;
    }

    @Override
    public void onDependentViewRemoved(CoordinatorLayout parent, View child, View dependency) {
        if (accessibility.isEnabled()) {
            child.getLayoutParams().height = parent.getHeight();
        } else {
            ValueAnimator animator = new ValueAnimator();
            int start = child.getHeight();
            animator.setIntValues(start, parent.getHeight());
            animator.setInterpolator(SnackbarAnimation.FAST_OUT_SLOW_IN_INTERPOLATOR);
            animator.setDuration(SnackbarAnimation.ANIMATION_DURATION);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    child.getLayoutParams().height = (int) animator.getAnimatedValue();
                    child.requestLayout();
                }
            });
            animator.start();
        }
    }
}