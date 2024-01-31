package com.squareup.picasso3.compose;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.MutableState;
import androidx.compose.runtime.RememberObserver;
import androidx.compose.runtime.State;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.remember;
import androidx.compose.ui.geometry.Size;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.DefaultAlpha;
import androidx.compose.ui.graphics.DrawScope;
import androidx.compose.ui.graphics.painter.Painter;
import com.google.accompanist.drawablepainter.DrawablePainter;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestCreator;


private class PicassoPainter implements Painter, RememberObserver, Drawable.Callback, DrawableTarget {

    private final Picasso picasso;
    private final Function<Picasso, RequestCreator> request;
    private final Consumer<Exception> onError;

    private MutableState<Painter> painter;
    private MutableState<Float> alpha;
    private MutableState<ColorFilter> colorFilter;

    public PicassoPainter(
            Picasso picasso,
            Function<Picasso, RequestCreator> request,
            Consumer<Exception> onError) {
        this.picasso = picasso;
        this.request = request;
        this.onError = onError;
        this.painter = mutableStateOf(new EmptyPainter());
        this.alpha = mutableStateOf(DefaultAlpha);
        this.colorFilter = mutableStateOf(null);
    }

    @Override
    public Size getIntrinsicSize() {
        return painter.getValue().getIntrinsicSize();
    }

    @Override
    public boolean setAlpha(float alpha) {
        this.alpha.setValue(alpha);
        return true;
    }

    @Override
    public boolean setColorFilter(ColorFilter colorFilter) {
        this.colorFilter.setValue(colorFilter);
        return true;
    }

    @Override
    public void onDraw(@NonNull DrawScope drawScope) {
        painter.getValue().draw(drawScope, getIntrinsicSize(), alpha.getValue(), colorFilter.getValue());
    }

    @Override
    public void onRemembered() {
        request.apply(picasso).into(this);
    }

    @Override
    public void onAbandoned() {
        if (painter.getValue() instanceof RememberObserver) {
            ((RememberObserver) painter.getValue()).onAbandoned();
        }
        painter.setValue(new EmptyPainter());
        picasso.cancelRequest(this);
    }

    @Override
    public void onForgotten() {
        if (painter.getValue() instanceof RememberObserver) {
            ((RememberObserver) painter.getValue()).onForgotten();
        }
        painter.setValue(new EmptyPainter());
        picasso.cancelRequest(this);
    }

    @Override
    public void onPrepareLoad(@Nullable Drawable placeHolderDrawable) {
        if (placeHolderDrawable != null) {
            setPainter(placeHolderDrawable);
        }
    }

    @Override
    public void onDrawableLoaded(@NonNull Drawable drawable, @NonNull LoadedFrom from) {
        setPainter(drawable);
    }

    @Override
    public void onDrawableFailed(@NonNull Exception e, @Nullable Drawable errorDrawable) {
        if (onError != null) {
            onError.accept(e);
        }
        if (errorDrawable != null) {
            setPainter(errorDrawable);
        }
    }

    private void setPainter(@NonNull Drawable drawable) {
        if (painter.getValue() instanceof RememberObserver) {
            ((RememberObserver) painter.getValue()).onForgotten();
        }
        painter.setValue(new DrawablePainter(drawable));
    }
}

private class EmptyPainter implements Painter {

    @Override
    public Size getIntrinsicSize() {
        return new Size(0, 0);
    }

    @Override
    public void draw(@NonNull DrawScope drawScope) {
        // Do nothing
    }

    @Override
    public void draw(@NonNull DrawScope drawScope, @NonNull Size size, float alpha,
            @Nullable ColorFilter colorFilter) {
        // Do nothing
    }
}