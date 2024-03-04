package com.github.shadowsocks.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.design.widget.FloatingActionButton;
import android.support.graphics.drawable.Animatable2Compat;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v7.widget.TooltipCompat;
import android.util.AttributeSet;
import android.view.View;

import com.github.shadowsocks.App;

import java.util.ArrayDeque;
import java.util.Deque;

public class ServiceButton extends FloatingActionButton {
    private Animatable2Compat.AnimationCallback callback = new Animatable2Compat.AnimationCallback() {
        @Override
        public void onAnimationEnd(Drawable drawable) {
            super.onAnimationEnd(drawable);
            AnimatedVectorDrawableCompat next = animationQueue.peek();
            if (next == null) {
                return;
            }
            if (next.getCurrent() == drawable) {
                animationQueue.pop();
                next = animationQueue.peek();
                if (next == null) {
                    return;
                }
            }
            setImageDrawable(next);
            next.start();
        }
    };

    private AnimatedVectorDrawableCompat createIcon(@DrawableRes int resId) {
        AnimatedVectorDrawableCompat result = AnimatedVectorDrawableCompat.create(getContext(), resId);
        if (result != null) {
            result.registerAnimationCallback(callback);
        }
        return result;
    }

    private AnimatedVectorDrawableCompat iconStopped = createIcon(R.drawable.ic_service_stopped);
    private AnimatedVectorDrawableCompat iconConnecting = createIcon(R.drawable.ic_service_connecting);
    private AnimatedVectorDrawableCompat iconConnected = createIcon(R.drawable.ic_service_connected);
    private AnimatedVectorDrawableCompat iconStopping = createIcon(R.drawable.ic_service_stopping);
    private Deque<AnimatedVectorDrawableCompat> animationQueue = new ArrayDeque<>();

    private boolean checked = false;

    public ServiceButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ServiceButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ServiceButton(Context context) {
        super(context);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (checked) {
            View.mergeDrawableStates(drawableState, new int[]{android.R.attr.state_checked});
        }
        return drawableState;
    }

    public void changeState(int state, boolean animate) {
        switch (state) {
            case BaseService.CONNECTING:
                changeState(iconConnecting, animate);
                break;
            case BaseService.CONNECTED:
                changeState(iconConnected, animate);
                break;
            case BaseService.STOPPING:
                changeState(iconStopping, animate);
                break;
            default:
                changeState(iconStopped, animate);
                break;
        }
        if (state == BaseService.CONNECTED) {
            checked = true;
            TooltipCompat.setTooltipText(this, getContext().getString(R.string.stop));
        } else {
            checked = false;
            TooltipCompat.setTooltipText(this, getContext().getString(R.string.connect));
        }
        refreshDrawableState();
        setEnabled(false);
        if (state == BaseService.CONNECTED || state == BaseService.STOPPED) {
        app.handler.postDelayed(() -> setEnabled(state == BaseService.CONNECTED || state == BaseService.STOPPED), 1000);
        }
    }

    private boolean counters(AnimatedVectorDrawableCompat a, AnimatedVectorDrawableCompat b) {
        return a == iconStopped && b == iconConnecting ||
                a == iconConnecting && b == iconStopped ||
                a == iconConnected && b == iconStopping ||
                a == iconStopping && b == iconConnected;
    }

    private void changeState(AnimatedVectorDrawableCompat icon, boolean animate) {
        if (animate) {
            if (animationQueue.size() < 2 || !counters(animationQueue.getLast(), icon)) {
                animationQueue.add(icon);
                if (animationQueue.size() == 1) {
                    setImageDrawable(icon);
                    icon.start();
                }
            } else {
                animationQueue.removeLast();
            }
        } else {
            AnimatedVectorDrawableCompat first = animationQueue.peekFirst();
            if (first != null) {
                first.stop();
            }
            animationQueue.clear();
            setImageDrawable(icon);
            icon.start();
            icon.stop();
        }
    }
}