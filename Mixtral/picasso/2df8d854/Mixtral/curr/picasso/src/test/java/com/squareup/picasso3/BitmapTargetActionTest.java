package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;

import static com.squareup.picasso3.TestUtils.makeBitmap;
import static com.squareup.picasso3.TestUtils.mockBitmapTarget;
import static com.squareup.picasso3.TestUtils.mockPicasso;

@RunWith(AndroidJUnit4.class)
public class BitmapTargetActionTest {

  @Mock private Picasso picasso;
  @Mock private Dispatcher dispatcher;
  @Mock private Context context;
  @Mock private CallbackFactory callbackFactory;
  @Mock private PlatformLruCache cache;
  private BitmapTargetAction tr;
  private BitmapTarget target;
  private Bitmap bitmap;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    bitmap = makeBitmap();
    target = mockBitmapTarget();

    Mockito.when(picasso.dispatcher).thenReturn(dispatcher);
    Mockito.when(picasso.context).thenReturn(ApplicationProvider.getApplicationContext());
    Mockito.when(picasso.callbackFactory).thenReturn(callbackFactory);

    tr = new BitmapTargetAction(
      picasso,
      target,
      SIMPLE_REQUEST,
      null,
      0);
  }

  @Test
  public void invokesSuccessIfTargetIsNotNull() {
    tr.complete(new RequestHandler.Result.Bitmap(bitmap, MEMORY));
    Mockito.verify(target).onBitmapLoaded(bitmap, MEMORY);
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorDrawable() {
    Drawable errorDrawable = mock(Drawable.class);
    tr = new BitmapTargetAction(
      picasso,
      target,
      SIMPLE_REQUEST,
      errorDrawable,
      0);

    RuntimeException e = new RuntimeException();
    tr.error(e);

    Mockito.verify(target).onBitmapFailed(e, errorDrawable);
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorResourceId() {
    Drawable errorDrawable = mock(Drawable.class);
    Mockito.when(context.getDrawable(RESOURCE_ID_1)).thenReturn(errorDrawable);

    tr = new BitmapTargetAction(
      picasso,
      target,
      SIMPLE_REQUEST,
      null,
      RESOURCE_ID_1);

    RuntimeException e = new RuntimeException();
    tr.error(e);
    Mockito.verify(target).onBitmapFailed(e, errorDrawable);
  }

  @Test
  public void recyclingInSuccessThrowsException() {
    tr = new BitmapTargetAction(
      picasso,
      new BitmapTarget() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
          bitmap.recycle();
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {}

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {}
      },
      SIMPLE_REQUEST,
      null,
      0);

    try {
      tr.complete(new RequestHandler.Result.Bitmap(bitmap, MEMORY));
      Assert.fail();
    } catch (IllegalStateException ignored) {}
  }
}