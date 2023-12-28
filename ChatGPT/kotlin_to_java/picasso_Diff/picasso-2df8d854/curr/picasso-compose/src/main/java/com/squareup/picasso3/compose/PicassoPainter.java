
package com.squareup.picasso3.compose;

import android.graphics.drawable.Drawable;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.RememberObserver;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.remember;
import androidx.compose.runtime.setValue;
import androidx.compose.ui.geometry.Size;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.DefaultAlpha;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import androidx.compose.ui.graphics.painter.Painter;
import com.google.accompanist.drawablepainter.DrawablePainter;
import com.squareup.picasso3.DrawableTarget;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestCreator;

@Composable
public class PicassoPainter {
  private final Picasso picasso;
  private final RequestCreator request;
  private final ((Exception) -> Unit) onError;
  private Painter painter;
  private float alpha;
  private ColorFilter colorFilter;

  public PicassoPainter(Picasso picasso, RequestCreator request, ((Exception) -> Unit) onError) {
    this.picasso = picasso;
    this.request = request;
    this.onError = onError;
    this.painter = EmptyPainter.INSTANCE;
    this.alpha = DefaultAlpha.INSTANCE.getValue();
    this.colorFilter = null;
  }

  public Size getIntrinsicSize() {
    return painter.getIntrinsicSize();
  }

  public boolean applyAlpha(float alpha) {
    this.alpha = alpha;
    return true;
  }

  public boolean applyColorFilter(ColorFilter colorFilter) {
    this.colorFilter = colorFilter;
    return true;
  }

  public void onDraw(DrawScope drawScope) {
    painter.draw(drawScope.getSize(), alpha, colorFilter);
  }

  public void onRemembered() {
    request.invoke(picasso).into(this);
  }

  public void onAbandoned() {
    ((RememberObserver) painter).onAbandoned();
    painter = EmptyPainter.INSTANCE;
    picasso.cancelRequest(this);
  }

  public void onForgotten() {
    ((RememberObserver) painter).onForgotten();
    painter = EmptyPainter.INSTANCE;
    picasso.cancelRequest(this);
  }

  public void onPrepareLoad(Drawable placeHolderDrawable) {
    if (placeHolderDrawable != null) {
      setPainter(placeHolderDrawable);
    }
  }

  public void onDrawableLoaded(Drawable drawable, LoadedFrom from) {
    setPainter(drawable);
  }

  public void onDrawableFailed(Exception e, Drawable errorDrawable) {
    if (onError != null) {
      onError.invoke(e);
    }
    if (errorDrawable != null) {
      setPainter(errorDrawable);
    }
  }

  private void setPainter(Drawable drawable) {
    ((RememberObserver) painter).onForgotten();
    painter = new DrawablePainter(drawable);
    ((RememberObserver) painter).onRemembered();
  }
}

private class EmptyPainter extends Painter {
  public static final EmptyPainter INSTANCE = new EmptyPainter();

  @Override
  public Size getIntrinsicSize() {
    return Size.Zero;
  }

  @Override
  public void onDraw(DrawScope drawScope) {
    // Do nothing
  }
}
