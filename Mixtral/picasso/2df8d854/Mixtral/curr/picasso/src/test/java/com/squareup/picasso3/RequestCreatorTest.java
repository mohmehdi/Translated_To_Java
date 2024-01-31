package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.truth.Truth;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.Priority;
import com.squareup.picasso3.RequestCreator;
import com.squareup.picasso3.TestUtils;
import com.squareup.picasso3.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class RequestCreatorTest {
  @Mock Picasso picasso;
  @Mock Utils utils;
  @Captor ArgumentCaptor<RequestCreator.RequestHandler> actionCaptor;
  private Bitmap bitmap;

  @Test
  public void getOnMainCrashes() {
    try {
      new RequestCreator(picasso, TestUtils.URI_1, 0).get();
      Assert.fail("Calling get() on main thread should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }











@Test
public void loadWithShutdownCrashes() {
  Picasso picasso = mock(Picasso.class);
  picasso.shutdown = true;
  try {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.fetch();
    fail("Should have crashed with a shutdown picasso.");
  } catch (IllegalStateException ignored) {
  }
}

@Test
public void getReturnsNullIfNullUriAndResourceId() throws IOException, InterruptedException {
  CountDownLatch latch = new CountDownLatch(1);
  Bitmap[] result = new Bitmap[1];
  Thread thread = new Thread(() -> {
    try {
      result[0] = new RequestCreator(picasso, null, 0).get();
    } catch (IOException e) {
      fail(e.getMessage());
    } finally {
      latch.countDown();
    }
  });
  thread.start();
  latch.await();

  assertNull(result[0]);
  verify(picasso).defaultBitmapConfig();
  verify(picasso).shutdown();
  verifyNoMoreInteractions(picasso);
}

@Test
public void fetchSubmitsFetchRequest() {
  RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
  requestCreator.fetch();
  verify(picasso).submit(actionCaptor.capture());
  assertThat(actionCaptor.getValue()).isInstanceOf(FetchAction.class);
}

@Test
public void fetchWithFitThrows() {
  try {
    new RequestCreator(picasso, URI_1, 0).fit().fetch();
    fail("Calling fetch() with fit() should throw an exception");
  } catch (IllegalStateException ignored) {
  }
}

@Test
public void fetchWithDefaultPriority() {
  new RequestCreator(picasso, URI_1, 0).fetch();
  verify(picasso).submit(actionCaptor.capture());
  assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.LOW);
}

@Test
public void fetchWithCustomPriority() {
  new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH).fetch();
  verify(picasso).submit(actionCaptor.capture());
  assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
}

@Test
public void fetchWithCache() {
  when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
  new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).fetch();
  verify(picasso, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void fetchWithMemoryPolicyNoCache() {
  new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).fetch();
  verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  verify(picasso).submit(actionCaptor.capture());
}

@Test
public void intoTargetWithFitThrows() {
  try {
    new RequestCreator(picasso, URI_1, 0).fit().into(mockBitmapTarget());
    fail("Calling into() target with fit() should throw exception");
  } catch (IllegalStateException ignored) {
  }
}

@Test
public void intoTargetNoPlaceholderCallsWithNull() {
  BitmapTarget target = mock(BitmapTarget.class);
  new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
  verify(target).onPrepareLoad(null);
}

@Test
public void intoTargetWithNullUriAndResourceIdSkipsAndCancels() {
  BitmapTarget target = mock(BitmapTarget.class);
  Drawable placeHolderDrawable = mock(Drawable.class);
  new RequestCreator(picasso, null, 0).placeholder(placeHolderDrawable).into(target);
  verify(picasso).defaultBitmapConfig();
  verify(picasso).shutdown();
  verify(picasso).cancelRequest(target);
  verify(target).onPrepareLoad(placeHolderDrawable);
  verifyNoMoreInteractions(picasso);
}

@Test
public void intoTargetWithQuickMemoryCacheCheckDoesNotSubmit() {
  when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
  BitmapTarget target = mock(BitmapTarget.class);
  new RequestCreator(picasso, URI_1, 0).into(target);
  verify(target).onBitmapLoaded(bitmap, MemoryPolicy.MEMORY);
  verify(picasso).cancelRequest(target);
  verify(picasso, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void intoTargetWithSkipMemoryPolicy() {
  BitmapTarget target = mock(BitmapTarget.class);
  new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).into(target);
  verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
}

@Test
public void intoTargetAndNotInCacheSubmitsTargetRequest() {
  BitmapTarget target = mock(BitmapTarget.class);
  Drawable placeHolderDrawable = mock(Drawable.class);
  new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
  verify(target).onPrepareLoad(placeHolderDrawable);
  verify(picasso).enqueueAndSubmit(actionCaptor.capture());
  assertThat(actionCaptor.getValue()).isInstanceOf(BitmapTargetAction.class);
}

@Test
public void targetActionWithDefaultPriority() {
  new RequestCreator(picasso, URI_1, 0).into(mockBitmapTarget());
  verify(picasso).enqueueAndSubmit(actionCaptor.capture());
  assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.NORMAL);
}

@Test
public void targetActionWithCustomPriority() {
  new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH).into(mockBitmapTarget());
  verify(picasso).enqueueAndSubmit(actionCaptor.capture());
  assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
}

@Test
public void targetActionWithDefaultTag() {
  new RequestCreator(picasso, URI_1, 0).into(mockBitmapTarget());
  verify(picasso).enqueueAndSubmit(actionCaptor.capture());
  assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void targetActionWithCustomTag() {
  new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockBitmapTarget());
  verify(picasso).enqueueAndSubmit(actionCaptor.capture());
  assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void intoDrawableTargetWithFitThrows() {
  try {
    new RequestCreator(picasso, URI_1, 0).fit().into(mockDrawableTarget());
    fail("Calling into() drawable target with fit() should throw exception");
  } catch (IllegalStateException ignored) {
  }
}


@Test
public void intoDrawableTargetNoPlaceholderCallsWithNull() {
    DrawableTarget target = mockDrawableTarget();
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.noPlaceholder().into(target);
    verify(target).onPrepareLoad(null);
}

@Test
public void intoDrawableTargetWithNullUriAndResourceIdSkipsAndCancels() {
    DrawableTarget target = mockDrawableTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    RequestCreator requestCreator = new RequestCreator(picasso, null, 0);
    requestCreator.placeholder(placeHolderDrawable).into(target);
    verify(picasso).defaultBitmapConfig();
    verify(picasso).shutdown();
    verify(picasso).cancelRequest(target);
    verify(target).onPrepareLoad(placeHolderDrawable);
    verifyNoMoreInteractions(picasso);
}

@Test
public void intoDrawableTargetWithQuickMemoryCacheCheckDoesNotSubmit() {
    when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
    DrawableTarget target = mockDrawableTarget();
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(target);
    verify(target).onDrawableLoaded(any(PicassoDrawable.class), eq(MEMORY));
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void intoDrawableTargetWithSkipMemoryPolicy() {
    DrawableTarget target = mockDrawableTarget();
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.memoryPolicy(NO_CACHE).into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
}

@Test
public void intoDrawableTargetAndNotInCacheSubmitsTargetRequest() {
    DrawableTarget target = mockDrawableTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.placeholder(placeHolderDrawable).into(target);
    verify(target).onPrepareLoad(placeHolderDrawable);
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(DrawableTargetAction.class);
}

@Test
public void intoImageViewWithNullUriAndResourceIdSkipsAndCancels() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator requestCreator = new RequestCreator(picasso, null, 0);
    requestCreator.into(target);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).quickMemoryCacheCheck(anyString());
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() {
    PlatformLruCache cache = PlatformLruCache.create(0);
    Picasso picassoSpy = spy(new Picasso(
            RuntimeEnvironment.application,
            mock(Dispatcher.class),
            UNUSED_CALL_FACTORY,
            null,
            cache,
            null,
            NO_TRANSFORMERS,
            NO_HANDLERS,
            NO_EVENT_LISTENERS,
            ARGB_8888,
            false,
            false
    ));
    doReturn(bitmap).when(picassoSpy).quickMemoryCacheCheck(URI_KEY_1);
    ImageViewTarget target = mockImageViewTarget();
    Callback callback = mockCallback();
    RequestCreator requestCreator = new RequestCreator(picassoSpy, URI_1, 0);
    requestCreator.into(target, callback);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(callback).onSuccess();
    verify(picassoSpy).cancelRequest(target);
    verify(picassoSpy, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void intoImageViewSetsPlaceholderDrawable() {
    PlatformLruCache cache = PlatformLruCache.create(0);
    Picasso picassoSpy = spy(new Picasso(
            RuntimeEnvironment.application,
            mock(Dispatcher.class),
            UNUSED_CALL_FACTORY,
            null,
            cache,
            null,
            NO_TRANSFORMERS,
            NO_HANDLERS,
            NO_EVENT_LISTENERS,
            ARGB_8888,
            false,
            false
    ));
    ImageViewTarget target = mockImageViewTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    RequestCreator requestCreator = new RequestCreator(picassoSpy, URI_1, 0);
    requestCreator.placeholder(placeHolderDrawable).into(target);
    ArgumentCaptor<Drawable> drawableCaptor = ArgumentCaptor.forClass(Drawable.class);
    verify(target).setImageDrawable(drawableCaptor.capture());
    assertThat(shadowOf(drawableCaptor.getValue()).getCreatedFromResId())
            .isEqualTo(android.R.drawable.picture_frame);
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picassoSpy).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewNoPlaceholderDrawable() {
    PlatformLruCache cache = PlatformLruCache.create(0);
    Picasso picassoSpy = spy(new Picasso(
            RuntimeEnvironment.application,
            mock(Dispatcher.class),
            UNUSED_CALL_FACTORY,
            null,
            cache,
            null,
            NO_TRANSFORMERS,
            NO_HANDLERS,
            NO_EVENT_LISTENERS,
            ARGB_8888,
            false,
            false
    ));
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator requestCreator = new RequestCreator(picassoSpy, URI_1, 0);
    requestCreator.noPlaceholder().into(target);
    verifyNoMoreInteractions(target);
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picassoSpy).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewSetsPlaceholderWithResourceId() {
    PlatformLruCache cache = PlatformLruCache.create(0);
    Picasso picassoSpy = spy(new Picasso(
            RuntimeEnvironment.application,
            mock(Dispatcher.class),
            UNUSED_CALL_FACTORY,
            null,
            cache,
            null,
            NO_TRANSFORMERS,
            NO_HANDLERS,
            NO_EVENT_LISTENERS,
            ARGB_8888,
            false,
            false
    ));
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator requestCreator = new RequestCreator(picassoSpy, URI_1, 0);
    requestCreator.placeholder(android.R.drawable.picture_frame).into(target);
    ArgumentCaptor<Drawable> drawableCaptor = ArgumentCaptor.forClass(Drawable.class);
    verify(target).setImageDrawable(drawableCaptor.capture());
    assertThat(shadowOf(drawableCaptor.getValue()).getCreatedFromResId())
            .isEqualTo(android.R.drawable.picture_frame);
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picassoSpy).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void cancelNotOnMainThreadCrashes() {
    doCallRealMethod().when(picasso).cancelRequest(any(BitmapTarget.class));
    CountDownLatch latch = new CountDownLatch(1);
    Thread thread = new Thread(() -> {
        try {
            RequestCreator requestCreator = new RequestCreator(picasso, null, 0);
            requestCreator.into(mockBitmapTarget());
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ignored) {
        } finally {
            latch.countDown();
        }
    });
    thread.start();
    latch.await();
}

@Test
public void intoNotOnMainThreadCrashes() {
    doCallRealMethod().when(picasso).enqueueAndSubmit(any(Action.class));
    CountDownLatch latch = new CountDownLatch(1);
    Thread thread = new Thread(() -> {
        try {
            RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
            requestCreator.into(mockImageViewTarget());
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ignored) {
        } finally {
            latch.countDown();
        }
    });
    thread.start();
    latch.await();
}

@Test
public void intoImageViewAndNotInCacheSubmitsImageViewRequest() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(target);
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewWithFitAndNoDimensionsQueuesDeferredImageViewRequest() {
    FitImageViewTarget target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(0);
    when(target.getHeight()).thenReturn(0);
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.fit().into(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
    verify(picasso).defer(eq(target), any(DeferredRequestCreator.class));
}

@Test
public void intoImageViewWithFitAndDimensionsQueuesImageViewRequest() {
    FitImageViewTarget target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(100);
    when(target.getHeight()).thenReturn(100);
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.fit().into(target);
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewWithSkipMemoryCachePolicy() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.memoryPolicy(NO_CACHE).into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
}

@Test
public void intoImageViewWithFitAndResizeThrows() {
    try {
        ImageViewTarget target = mockImageViewTarget();
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.fit().resize(10, 10).into(target);
        fail("Calling into() ImageView with fit() and resize() should throw exception");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void imageViewActionWithDefaultPriority() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockImageViewTarget());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void imageViewActionWithCustomPriority() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.priority(HIGH).into(mockImageViewTarget());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void imageViewActionWithDefaultTag() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockImageViewTarget());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void imageViewActionWithCustomTag() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.tag("tag").into(mockImageViewTarget());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void intoRemoteViewsWidgetQueuesAppWidgetAction() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(AppWidgetAction.class);
}

@Test
public void intoRemoteViewsNotificationQueuesNotificationAction() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockRemoteViews(), 0, 0, mockNotification());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(NotificationAction.class);
}

@Test
public void intoRemoteViewsWidgetWithPlaceholderDrawableThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(new ColorDrawable(0)).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
        fail("Calling into() with placeholder drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsWidgetWithErrorDrawableThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.error(new ColorDrawable(0)).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
        fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsNotificationWithPlaceholderDrawableThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(new ColorDrawable(0)).into(mockRemoteViews(), 0, 0, mockNotification());
        fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsNotificationWithErrorDrawableThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.error(new ColorDrawable(0)).into(mockRemoteViews(), 0, 0, mockNotification());
        fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsWidgetWithFitThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.fit().into(mockRemoteViews(), 1, new int[]{1, 2, 3});
        fail("Calling fit() into remote views should throw exception");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void intoRemoteViewsNotificationWithFitThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.fit().into(mockRemoteViews(), 1, 1, mockNotification());
        fail("Calling fit() into remote views should throw exception");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void intoTargetNoResizeWithCenterInsideOrCenterCropThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.centerInside().into(mockBitmapTarget());
        fail("Center inside with unknown width should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.centerCrop().into(mockBitmapTarget());
        fail("Center inside with unknown height should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void appWidgetActionWithDefaultPriority() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void appWidgetActionWithCustomPriority() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.priority(HIGH).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void notificationActionWithDefaultPriority() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockRemoteViews(), 0, 0, mockNotification());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void notificationActionWithCustomPriority() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.priority(HIGH).into(mockRemoteViews(), 0, 0, mockNotification());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void appWidgetActionWithDefaultTag() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void appWidgetActionWithCustomTag() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.tag("tag").into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void notificationActionWithDefaultTag() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.into(mockRemoteViews(), 0, 0, mockNotification());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void notificationActionWithCustomTag() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.tag("tag").into(mockRemoteViews(), 0, 0, mockNotification());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void invalidResize() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.resize(-1, 10);
        fail("Negative width should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.resize(10, -1);
        fail("Negative height should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.resize(0, 0);
        fail("Zero dimensions should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void invalidCenterCrop() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.resize(10, 10).centerInside().centerCrop();
        fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidCenterInside() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.resize(10, 10).centerCrop().centerInside();
        fail("Calling center inside after center crop should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidPlaceholderImage() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(0);
        fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(1).placeholder(new ColorDrawable(0));
        fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(new ColorDrawable(0)).placeholder(1);
        fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidNoPlaceholder() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.noPlaceholder().placeholder(new ColorDrawable(0));
        fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.noPlaceholder().placeholder(1);
        fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(1).noPlaceholder();
        fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.placeholder(new ColorDrawable(0)).noPlaceholder();
        fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidErrorImage() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.error(0);
        fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.error(1).error(new ColorDrawable(0));
        fail("Two error placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.error(new ColorDrawable(0)).error(1);
        fail("Two error placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidPriority() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.priority(LOW).priority(HIGH);
        fail("Two priorities should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void alreadySetTagThrows() {
    try {
        RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
        requestCreator.tag("tag1").tag("tag2");
        fail("Two tags should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void transformationListImplementationValid() {
    List<Transformation> transformations = Arrays.asList(new TestTransformation("test"));
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.transform(transformations);
    // TODO verify something!
}

@Test
public void imageViewActionWithStableKey() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.stableKey(STABLE_1).into(mockImageViewTarget());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.key).isEqualTo(STABLE_URI_KEY_1);
}

@Test
public void imageViewActionWithCustomHeaders() {
    RequestCreator requestCreator = new RequestCreator(picasso, URI_1, 0);
    requestCreator.addHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE).into(mockImageViewTarget());
    ArgumentCaptor<Action> actionCaptor = ArgumentCaptor.forClass(Action.class);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.headers.get(CUSTOM_HEADER_NAME))
            .isEqualTo(CUSTOM_HEADER_VALUE);
}








}