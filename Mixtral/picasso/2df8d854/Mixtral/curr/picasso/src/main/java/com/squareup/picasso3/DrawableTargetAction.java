package com.squareup.picasso3;

import android.graphics.drawable.Drawable;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;

import java.lang.ref.WeakReference;

public class DrawableTargetAction extends Action {
  private final WeakReference picassoRef;
  private final DrawableTarget target;
  private final Request data;
  private final boolean noFade;
  private final Drawable placeholderDrawable;
  private final Drawable errorDrawable;
  @DrawableRes private final int errorResId;

  public DrawableTargetAction(Picasso picasso, DrawableTarget target, Request data, boolean noFade,
    Drawable placeholderDrawable, Drawable errorDrawable, @DrawableRes int errorResId) {
    super(picasso, data);
    this.picassoRef = new WeakReference < > (picasso);
    this.target = target;
    this.data = data;
    this.noFade = noFade;
    this.placeholderDrawable = placeholderDrawable;
    this.errorDrawable = errorDrawable;
    this.errorResId = errorResId;
  }

  @Override
  public void complete(Result result) {
    if (result instanceof Bitmap) {
      Bitmap bitmapResult = (Bitmap) result;
      Drawable finalDrawable = new PicassoDrawable(picassoRef.get().getContext(), bitmapResult.getBitmap(),
        placeholderDrawable, result.getLoadedFrom(), noFade, picassoRef.get().indicatorsEnabled);
      target.onDrawableLoaded(finalDrawable, result.getLoadedFrom());
      check(!bitmapResult.getBitmap().isRecycled(), "Target callback must not recycle bitmap!");
    }
  }

  @Override
  public void error(Exception e) {
    Drawable drawable;
    if (errorResId != 0) {
      drawable = ContextCompat.getDrawable(picassoRef.get().getContext(), errorResId);
    } else {
      drawable = errorDrawable;
    }
    target.onDrawableFailed(e, drawable);
  }

  @Override
  public Any getTarget() {
    return target;
  }
}