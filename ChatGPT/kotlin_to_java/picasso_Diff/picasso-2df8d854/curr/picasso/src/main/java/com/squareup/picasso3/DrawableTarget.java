package com.squareup.picasso3;

import android.graphics.drawable.Drawable;
import com.squareup.picasso3.Picasso.LoadedFrom;

public interface DrawableTarget {

  void onDrawableLoaded(
    Drawable drawable,
    LoadedFrom from
  );

  void onDrawableFailed(
    Exception e,
    Drawable errorDrawable
  );

  void onPrepareLoad(Drawable placeHolderDrawable);
}