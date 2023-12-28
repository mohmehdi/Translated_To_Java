package com.squareup.picasso3;

import android.graphics.drawable.Drawable;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;

public class DrawableTargetAction extends Action {
  private DrawableTarget target;
  private boolean noFade;
  private Drawable placeholderDrawable;
  private Drawable errorDrawable;
  private int errorResId;

  public DrawableTargetAction(Picasso picasso, DrawableTarget target, Request data, boolean noFade, Drawable placeholderDrawable, Drawable errorDrawable, @DrawableRes int errorResId) {
    super(picasso, data);
    this.target = target;
    this.noFade = noFade;
    this.placeholderDrawable = placeholderDrawable;
    this.errorDrawable = errorDrawable;
    this.errorResId = errorResId;
  }

  @Override
  public void complete(Result result) {
    if (result instanceof Bitmap) {
      Bitmap bitmapResult = (Bitmap) result;
      android.graphics.Bitmap bitmap = bitmapResult.bitmap;
      target.onDrawableLoaded(
        new PicassoDrawable(
          picasso.context,
          bitmap,
          placeholderDrawable,
          result.loadedFrom,
          noFade,
          picasso.indicatorsEnabled
        ),
        result.loadedFrom
      );
      if (!bitmap.isRecycled()) {
        throw new IllegalStateException("Target callback must not recycle bitmap!");
      }
    }
  }

  @Override
  public void error(Exception e) {
    Drawable drawable;
    if (errorResId != 0) {
      drawable = ContextCompat.getDrawable(picasso.context, errorResId);
    } else {
      drawable = errorDrawable;
    }

    target.onDrawableFailed(e, drawable);
  }

  @Override
  public Object getTarget() {
    return target;
  }
}