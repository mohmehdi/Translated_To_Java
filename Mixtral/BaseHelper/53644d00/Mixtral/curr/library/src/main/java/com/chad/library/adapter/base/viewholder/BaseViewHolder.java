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
import java.util.Objects;

public class BaseViewHolder extends RecyclerView.ViewHolder {

  private final SparseArray<View> views = new SparseArray<>();

  public <B extends ViewDataBinding> BaseViewHolder(View view) {
    super(view);
  }

  @SuppressWarnings("unchecked")
  public <T extends View> T getView(@IdRes int viewId) {
    View view = getViewOrNull(viewId);
    if (view == null) {
      throw new NullPointerException("No view found with id " + viewId);
    }
    return (T) view;
  }

  @SuppressWarnings("unchecked")
  public <T extends View> T getViewOrNull(@IdRes int viewId) {
    View view = views.get(viewId);
    if (view == null) {
      view = itemView.findViewById(viewId);
      if (view != null) {
        views.put(viewId, view);
      }
    }
    return (T) view;
  }

  @SuppressWarnings("unchecked")
  public <T extends View> T findView(@IdRes int viewId) {
    return (T) itemView.findViewById(viewId);
  }

  public <B extends ViewDataBinding> B getBinding() {
    return DataBindingUtil.getBinding(itemView);
  }

  public BaseViewHolder setText(@IdRes int viewId, CharSequence value) {
    TextView textView = getView(viewId);
    textView.setText(value);
    return this;
  }

  public BaseViewHolder setText(@IdRes int viewId, @StringRes int strId) {
    TextView textView = getView(viewId);
    textView.setText(strId);
    return this;
  }

  public BaseViewHolder setTextColor(@IdRes int viewId, @ColorInt int color) {
    TextView textView = getView(viewId);
    textView.setTextColor(color);
    return this;
  }

  public BaseViewHolder setTextColorRes(
    @IdRes int viewId,
    @ColorRes int colorRes
  ) {
    TextView textView = getView(viewId);
    textView.setTextColor(
      Objects.requireNonNull(itemView.getResources()).getColor(colorRes)
    );
    return this;
  }

  public BaseViewHolder setImageResource(
    @IdRes int viewId,
    @DrawableRes int imageResId
  ) {
    ImageView imageView = getView(viewId);
    imageView.setImageResource(imageResId);
    return this;
  }

  public BaseViewHolder setImageDrawable(@IdRes int viewId, Drawable drawable) {
    ImageView imageView = getView(viewId);
    imageView.setImageDrawable(drawable);
    return this;
  }

  public BaseViewHolder setImageBitmap(@IdRes int viewId, Bitmap bitmap) {
    ImageView imageView = getView(viewId);
    imageView.setImageBitmap(bitmap);
    return this;
  }

  public BaseViewHolder setBackgroundColor(
    @IdRes int viewId,
    @ColorInt int color
  ) {
    View view = getView(viewId);
    view.setBackgroundColor(color);
    return this;
  }

  public BaseViewHolder setBackgroundResource(
    @IdRes int viewId,
    @DrawableRes int backgroundRes
  ) {
    View view = getView(viewId);
    view.setBackgroundResource(backgroundRes);
    return this;
  }

  public BaseViewHolder setVisible(@IdRes int viewId, boolean isVisible) {
    View view = getView(viewId);
    view.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    return this;
  }

  public BaseViewHolder setGone(@IdRes int viewId, boolean isGone) {
    View view = getView(viewId);
    view.setVisibility(isGone ? View.GONE : View.VISIBLE);
    return this;
  }

  public BaseViewHolder setEnabled(@IdRes int viewId, boolean isEnabled) {
    View view = getView(viewId);
    view.setEnabled(isEnabled);
    return this;
  }
}
