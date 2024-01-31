
package com.squareup.picasso3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.TestUtils;
import com.squareup.picasso3.TestUtils.ArgumentCaptor;
import com.squareup.picasso3.TestUtils.Eq;
import com.squareup.picasso3.TestUtils.PicassoResultBitmap;
import com.squareup.picasso3.TestUtils.mockPicasso;
import com.squareup.picasso3.TestUtils.mockDrawableTarget;
import com.squareup.picasso3.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class DrawableTargetActionTest {

  @Test
  public void invokesSuccessIfTargetIsNotNull() {
    Bitmap bitmap = TestUtils.makeBitmap();
    DrawableTarget target = TestUtils.mockDrawableTarget();
    ArgumentCaptor<PicassoDrawable> drawableCaptor = ArgumentCaptor.forClass(PicassoDrawable.class);
    Drawable placeholder = mock(Drawable.class);
    DrawableTargetAction action = new DrawableTargetAction(
      TestUtils.mockPicasso(RuntimeEnvironment.application),
      target,
      TestUtils.SIMPLE_REQUEST,
      false,
      placeholder,
      null,
      0
    );

    action.complete(new PicassoResultBitmap(bitmap, LoadedFrom.NETWORK));

    Mockito.verify(target).onDrawableLoaded(drawableCaptor.capture(), Eq.equalTo(LoadedFrom.NETWORK));
    PicassoDrawable drawableValue = drawableCaptor.getValue();
    Truth.assertThat(drawableValue.getBitmap()).isEqualTo(bitmap);
    Truth.assertThat(drawableValue.getPlaceholder()).isEqualTo(placeholder);
    Truth.assertThat(drawableValue.isAnimating()).isTrue();
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorDrawable() {
    Drawable errorDrawable = mock(Drawable.class);
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
    Exception e = new RuntimeException();

    action.error(e);

    Mockito.verify(target).onDrawableFailed(e, errorDrawable);
  }

  @Test
  public void invokesOnBitmapFailedIfTargetIsNotNullWithErrorResourceId() {
    Drawable errorDrawable = mock(Drawable.class);
    Context context = mock(Context.class);
    Dispatcher dispatcher = mock(Dispatcher.class);
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = new Picasso(
      context, dispatcher,
      TestUtils.UNUSED_CALL_FACTORY,
      null, cache, null,
      TestUtils.NO_TRANSFORMERS,
      TestUtils.NO_HANDLERS,
      TestUtils.NO_EVENT_LISTENERS,
      Config.ARGB_8888, false, false
    );
    Resources res = mock(Resources.class);

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
    Exception e = new RuntimeException();

    action.error(e);

    Mockito.verify(target).onDrawableFailed(e, errorDrawable);
  }

  @Test
  public void recyclingInSuccessThrowsException() {
    Picasso picasso = TestUtils.mockPicasso(RuntimeEnvironment.application);
    Bitmap bitmap = TestUtils.makeBitmap();
    DrawableTarget target = new DrawableTarget() {
      @Override
      public void onDrawableLoaded(Drawable drawable, Picasso.LoadedFrom from) {
        ((PicassoDrawable) drawable).getBitmap().recycle();
      }

      @Override
      public void onDrawableFailed(Exception e, Drawable errorDrawable) {
        throw new AssertionError();
      }

      @Override
      public void onPrepareLoad(Drawable placeHolderDrawable) {
        throw new AssertionError();
      }
    };
    DrawableTargetAction action = new DrawableTargetAction(
      picasso,
      target,
      TestUtils.SIMPLE_REQUEST,
      true,
      null,
      null,
      0
    );

    try {
      action.complete(new PicassoResultBitmap(bitmap, LoadedFrom.MEMORY));
      Assert.fail();
    } catch (IllegalStateException ignored) {
    }
  }
}


