package com.squareup.picasso3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DrawableTargetActionTest {

  @Test
  public void invokesSuccessIfTargetIsNotNull() {
    Bitmap bitmap = TestUtils.makeBitmap();
    DrawableTarget target = TestUtils.mockDrawableTarget();
    ArgumentCaptor<PicassoDrawable> drawableCaptor = TestUtils.argumentCaptor(PicassoDrawable.class);
    Drawable placeholder = Mockito.mock(Drawable.class);
    DrawableTargetAction action = new DrawableTargetAction(
      TestUtils.mockPicasso(RuntimeEnvironment.application),
      target,
      TestUtils.SIMPLE_REQUEST,
      false,
      placeholder,
      null,
      0
    );

    action.complete(new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.NETWORK));

    Mockito.verify(target).onDrawableLoaded(drawableCaptor.capture(), TestUtils.eq(LoadedFrom.NETWORK));
    PicassoDrawable capturedDrawable = drawableCaptor.getValue();
    Truth.assertThat(capturedDrawable.bitmap).isEqualTo(bitmap);
    Truth.assertThat(capturedDrawable.placeholder).isEqualTo(placeholder);
    Truth.assertThat(capturedDrawable.animating).isTrue();
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorDrawable() {
    Drawable errorDrawable = Mockito.mock(Drawable.class);
    DrawableTarget target = TestUtils.mockDrawableTarget();
    DrawableTargetAction action = new DrawableTargetAction(
      TestUtils.mockPicasso(RuntimeEnvironment.application),
      target,
      TestUtils.SIMPLE_REQUEST,
      true,
      null,
      errorDrawable,
      0
    );
    RuntimeException e = new RuntimeException();

    action.error(e);

    Mockito.verify(target).onDrawableFailed(e, errorDrawable);
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorResourceId() {
    Drawable errorDrawable = Mockito.mock(Drawable.class);
    Context context = Mockito.mock(Context.class);
    Dispatcher dispatcher = Mockito.mock(Dispatcher.class);
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = new Picasso(
      context, dispatcher,
      TestUtils.UNUSED_CALL_FACTORY, null, cache, null,
      TestUtils.NO_TRANSFORMERS,
      TestUtils.NO_HANDLERS,
      TestUtils.NO_EVENT_LISTENERS, Bitmap.Config.ARGB_8888, false, false
    );
    Resources res = Mockito.mock(Resources.class);

    DrawableTarget target = TestUtils.mockDrawableTarget();
    DrawableTargetAction action = new DrawableTargetAction(
      picasso,
      target,
      TestUtils.SIMPLE_REQUEST,
      true,
      null,
      null,
      TestUtils.RESOURCE_ID_1
    );

    Mockito.when(context.getDrawable(TestUtils.RESOURCE_ID_1)).thenReturn(errorDrawable);
    RuntimeException e = new RuntimeException();

    action.error(e);

    Mockito.verify(target).onDrawableFailed(e, errorDrawable);
  }

  @Test
  public void recyclingInSuccessThrowsException() {
    Picasso picasso = TestUtils.mockPicasso(RuntimeEnvironment.application);
    Bitmap bitmap = TestUtils.makeBitmap();
    DrawableTargetAction action = new DrawableTargetAction(
      picasso,
      new DrawableTarget() {
        @Override
        public void onDrawableLoaded(Drawable drawable, LoadedFrom from) {
          ((PicassoDrawable) drawable).bitmap.recycle();
        }

        @Override
        public void onDrawableFailed(Exception e, Drawable errorDrawable) {
          throw new AssertionError();
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
          throw new AssertionError();
        }
      },
      TestUtils.SIMPLE_REQUEST,
      true,
      null,
      null,
      0
    );

    try {
      action.complete(new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY));
      Assert.fail();
    } catch (IllegalStateException ignored) {
    }
  }
}