package com.github.shadowsocks.widget;

import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.Animatable2Compat;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.design.widget.FloatingActionButton;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.v7.widget.TooltipCompat;
import android.util.AttributeSet;
import android.view.View;
import com.github.shadowsocks.App;
import com.github.shadowsocks.R;
import com.github.shadowsocks.bg.BaseService;
import java.util.ArrayDeque;
import java.util.Deque;

public class ServiceButton extends FloatingActionButton {
private final Animatable2.AnimationCallback callback = new Animatable2Compat.AnimationCallback() {
@Override
public void onAnimationEnd(Drawable drawable) {
super.onAnimationEnd(drawable);
AnimatedVectorDrawableCompat next = animationQueue.peek();
if (next != null && next.getCurrent() == drawable) {
animationQueue.poll();
next = animationQueue.peek();
}
setImageDrawable(next);
if (next != null) next.start();
}
};


private AnimatedVectorDrawableCompat createIcon(@DrawableRes int resId) {
    AnimatedVectorDrawableCompat result = AnimatedVectorDrawableCompat.create(getContext(), resId);
    result.registerAnimationCallback(callback);
    return result;
}

private final AnimatedVectorDrawableCompat iconStopped = createIcon(R.drawable.ic_service_stopped);
private final AnimatedVectorDrawableCompat iconConnecting = createIcon(R.drawable.ic_service_connecting);
private final AnimatedVectorDrawableCompat iconConnected = createIcon(R.drawable.ic_service_connected);
private final AnimatedVectorDrawableCompat iconStopping = createIcon(R.drawable.ic_service_stopping);
private final Deque<AnimatedVectorDrawableCompat> animationQueue = new ArrayDeque<>();

private boolean checked = false;

public ServiceButton(Context context) {
    super(context);
    init(null);
}

public ServiceButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
}

public ServiceButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(attrs);
}

private void init(AttributeSet attrs) {
    // Initialize any attributes here if needed
}

@Override
protected int[] onCreateDrawableState(int extraSpace) {
    int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
    if (checked) View.mergeDrawableStates(drawableState, new int[]{android.R.attr.state_checked});
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
    }
    checked = state == BaseService.CONNECTED;
    TooltipCompat.setTooltipText(this, getContext().getString(state == BaseService.CONNECTED ? R.string.stop : R.string.connect));
    refreshDrawableState();
    setEnabled(false);
    if (state == BaseService.CONNECTED || state == BaseService.STOPPED) App.getApp().handler.postDelayed(
            () -> setEnabled(state == BaseService.CONNECTED || state == BaseService.STOPPED), 1000);
}

private boolean counters(AnimatedVectorDrawableCompat a, AnimatedVectorDrawableCompat b) {
    return (a == iconStopped && b == iconConnecting)
            || (a == iconConnecting && b == iconStopped)
            || (a == iconConnected && b == iconStopping)
            || (a == iconStopping && b == iconConnected);
}

private void changeState(AnimatedVectorDrawableCompat icon, boolean animate) {
    if (animate) {
        if (animationQueue.size() < 2 || !counters(animationQueue.peekLast(), icon)) {
            animationQueue.add(icon);
            if (animationQueue.size() == 1) {
                setImageDrawable(icon);
                icon.start();
            }
        } else animationQueue.pollLast();
    } else {
        animationQueue.peekFirst().stop();
        animationQueue.clear();
        setImageDrawable(icon);
        icon.start(); // force ensureAnimatorSet to be called so that stop() will work
        icon.stop();
    }
}
}