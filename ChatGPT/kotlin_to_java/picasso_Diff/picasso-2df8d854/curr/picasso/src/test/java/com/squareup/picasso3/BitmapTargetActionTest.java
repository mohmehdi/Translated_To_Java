package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS;
import com.squareup.picasso3.TestUtils.NO_HANDLERS;
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS;
import com.squareup.picasso3.TestUtils.RESOURCE_ID_1;
import com.squareup.picasso3.TestUtils.SIMPLE_REQUEST;
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import com.squareup.picasso3.TestUtils.makeBitmap;
import com.squareup.picasso3.TestUtils.mockBitmapTarget;
import com.squareup.picasso3.TestUtils.mockPicasso;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class BitmapTargetActionTest {

  @Test
  public void invokesSuccessIfTargetIsNotNull() {
    Bitmap bitmap = makeBitmap();
    BitmapTarget target = mockBitmapTarget();
    BitmapTargetAction request = new BitmapTargetAction(
      mockPicasso(RuntimeEnvironment.application),
      target,
      SIMPLE_REQUEST,
      null,
      0
    );
    request.complete(new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY));
    Mockito.verify(target).onBitmapLoaded(bitmap, LoadedFrom.MEMORY);
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorDrawable() {
    Drawable errorDrawable = Mockito.mock(Drawable.class);
    BitmapTarget target = mockBitmapTarget();
    BitmapTargetAction request = new BitmapTargetAction(
      mockPicasso(RuntimeEnvironment.application),
      target,
      SIMPLE_REQUEST,
      errorDrawable,
      0
    );
    RuntimeException e = new RuntimeException();

    request.error(e);

    Mockito.verify(target).onBitmapFailed(e, errorDrawable);
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorResourceId() {
    Drawable errorDrawable = Mockito.mock(Drawable.class);
    BitmapTarget target = mockBitmapTarget();
    Context context = Mockito.mock(Context.class);
    Dispatcher dispatcher = Mockito.mock(Dispatcher.class);
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = new Picasso(
      context, dispatcher, UNUSED_CALL_FACTORY, null, cache, null,
      NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, Config.ARGB_8888, false, false
    );
    BitmapTargetAction request = new BitmapTargetAction(
      picasso,
      target,
      SIMPLE_REQUEST,
      null,
      RESOURCE_ID_1
    );

    Mockito.when(context.getDrawable(RESOURCE_ID_1)).thenReturn(errorDrawable);
    RuntimeException e = new RuntimeException();

    request.error(e);
    Mockito.verify(target).onBitmapFailed(e, errorDrawable);
  }

  @Test
  public void recyclingInSuccessThrowsException() {
    Picasso picasso = mockPicasso(RuntimeEnvironment.application);
    Bitmap bitmap = makeBitmap();
    BitmapTargetAction tr = new BitmapTargetAction(
      picasso,
      new BitmapTarget() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
          bitmap.recycle();
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
          Assert.fail();
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
          Assert.fail();
        }
      },
      SIMPLE_REQUEST,
      null,
      0
    );
    try {
      tr.complete(new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY));
      Assert.fail();
    } catch (IllegalStateException ignored) {
    }
  }
}