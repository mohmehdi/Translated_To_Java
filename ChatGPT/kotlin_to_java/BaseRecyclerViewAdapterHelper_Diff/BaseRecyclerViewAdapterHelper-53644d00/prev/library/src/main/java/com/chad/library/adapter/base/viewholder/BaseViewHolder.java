package com.chad.library.adapter.base.viewholder;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.RecyclerView;

public class BaseViewHolder extends RecyclerView.ViewHolder {

    private SparseArray<View> views;

    public BaseViewHolder(View view) {
        super(view);
        views = new SparseArray<>();
    }

    public <B extends ViewDataBinding> B getBinding() {
        return DataBindingUtil.getBinding(itemView);
    }

    public <T extends View> T getView(@IdRes int viewId) {
        T view = getViewOrNull(viewId);
        if (view == null) {
            throw new NullPointerException("No view found with id " + viewId);
        }
        return view;
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T getViewOrNull(@IdRes int viewId) {
        View view = views.get(viewId);
        if (view == null) {
            T foundView = itemView.findViewById(viewId);
            if (foundView != null) {
                views.put(viewId, foundView);
                return foundView;
            }
        }
        return (T) view;
    }

    public <T extends View> T findView(@IdRes int viewId) {
        return itemView.findViewById(viewId);
    }

    public BaseViewHolder setText(@IdRes int viewId, CharSequence value) {
        getView<TextView>(viewId).setText(value);
        return this;
    }

    public BaseViewHolder setText(@IdRes int viewId, @StringRes int strId) {
        getView<TextView>(viewId).setText(strId);
        return this;
    }

    public BaseViewHolder setTextColor(@IdRes int viewId, @ColorInt int color) {
        getView<TextView>(viewId).setTextColor(color);
        return this;
    }

    public BaseViewHolder setTextColorRes(@IdRes int viewId, @ColorRes int colorRes) {
        getView<TextView>(viewId).setTextColor(itemView.getResources().getColor(colorRes));
        return this;
    }

    public BaseViewHolder setImageResource(@IdRes int viewId, @DrawableRes int imageResId) {
        getView<ImageView>(viewId).setImageResource(imageResId);
        return this;
    }

    public BaseViewHolder setImageDrawable(@IdRes int viewId, Drawable drawable) {
        getView<ImageView>(viewId).setImageDrawable(drawable);
        return this;
    }

    public BaseViewHolder setImageBitmap(@IdRes int viewId, Bitmap bitmap) {
        getView<ImageView>(viewId).setImageBitmap(bitmap);
        return this;
    }

    public BaseViewHolder setBackgroundColor(@IdRes int viewId, @ColorInt int color) {
        getView<View>(viewId).setBackgroundColor(color);
        return this;
    }

    public BaseViewHolder setBackgroundResource(@IdRes int viewId, @DrawableRes int backgroundRes) {
        getView<View>(viewId).setBackgroundResource(backgroundRes);
        return this;
    }

    public BaseViewHolder setVisible(@IdRes int viewId, boolean isVisible) {
        View view = getView<View>(viewId);
        view.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        return this;
    }

    public BaseViewHolder setGone(@IdRes int viewId, boolean isGone) {
        View view = getView<View>(viewId);
        view.setVisibility(isGone ? View.GONE : View.VISIBLE);
        return this;
    }

    public BaseViewHolder setEnabled(@IdRes int viewId, boolean isEnabled) {
        getView<View>(viewId).setEnabled(isEnabled);
        return this;
    }
}