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
import com.squareup.picasso3.RemoteViewsAction.AppWidgetAction;
import com.squareup.picasso3.RemoteViewsAction.NotificationAction;
import com.squareup.picasso3.TestUtils;
import com.squareup.picasso3.TestUtils.CUSTOM_HEADER_NAME;
import com.squareup.picasso3.TestUtils.CUSTOM_HEADER_VALUE;
import com.squareup.picasso3.TestUtils.NO_EVENT_LISTENERS;
import com.squareup.picasso3.TestUtils.NO_HANDLERS;
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS;
import com.squareup.picasso3.TestUtils.STABLE_1;
import com.squareup.picasso3.TestUtils.STABLE_URI_KEY_1;
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import com.squareup.picasso3.TestUtils.URI_1;
import com.squareup.picasso3.TestUtils.URI_KEY_1;
import com.squareup.picasso3.TestUtils.any;
import com.squareup.picasso3.TestUtils.argumentCaptor;
import com.squareup.picasso3.TestUtils.eq;
import com.squareup.picasso3.TestUtils.makeBitmap;
import com.squareup.picasso3.TestUtils.mockCallback;
import com.squareup.picasso3.TestUtils.mockFitImageViewTarget;
import com.squareup.picasso3.TestUtils.mockImageViewTarget;
import com.squareup.picasso3.TestUtils.mockNotification;
import com.squareup.picasso3.TestUtils.mockPicasso;
import com.squareup.picasso3.TestUtils.mockRemoteViews;
import com.squareup.picasso3.TestUtils.mockRequestCreator;
import com.squareup.picasso3.TestUtils.mockTarget;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class RequestCreatorTest {
  @Mock private Picasso mockPicasso;
  private Picasso picasso;
  private Bitmap bitmap;
  private ArgumentCaptor<Picasso.RequestCreator.Action> actionCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    bitmap = makeBitmap();
    picasso = spy(mockPicasso);
    actionCaptor = ArgumentCaptor.forClass(Picasso.RequestCreator.Action.class);
  }

  @Test
  public void getOnMainCrashes() {
    try {
      new RequestCreator(picasso, URI_1, 0).get();
      Assert.fail("Calling get() on main thread should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void loadWithShutdownCrashes() {
    picasso.shutdown = true;
    try {
      new RequestCreator(picasso, URI_1, 0).fetch();
      Assert.fail("Should have crashed with a shutdown picasso.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void getReturnsNullIfNullUriAndResourceId() throws InterruptedException, IOException {
    final Bitmap[] result = new Bitmap[1];
    final CountDownLatch latch = new CountDownLatch(1);
    ShadowLooper.runInteractionThread();
    new Thread(
            () -> {
              try {
                result[0] =
                    new RequestCreator(picasso, null, 0).get();
              } catch (IOException e) {
                Assert.fail(e.getMessage());
              } finally {
                latch.countDown();
              }
            })
        .start();
    latch.await();

    Truth.assertThat(result[0]).isNull();
    Mockito.verify(picasso).defaultBitmapConfig();
    Mockito.verify(picasso).shutdown();
    Mockito.verifyNoMoreInteractions(picasso);
  }

  @Test
  public void fetchSubmitsFetchRequest() {
    new RequestCreator(picasso, URI_1, 0).fetch();
    Mockito.verify(picasso).submit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(FetchAction.class);
  }

  @Test
  public void fetchWithFitThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).fit().fetch();
      Assert.fail("Calling fetch() with fit() should throw an exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void fetchWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).fetch();
    Mockito.verify(picasso).submit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.LOW);
  }

  @Test
  public void fetchWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH).fetch();
    Mockito.verify(picasso).submit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
  }

  @Test
  public void fetchWithCache() {
    Mockito.when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).fetch();
    Mockito.verify(picasso, never()).enqueueAndSubmit(any(Picasso.RequestCreator.Action.class));
  }

  @Test
  public void fetchWithMemoryPolicyNoCache() {
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).fetch();
    Mockito.verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
    Mockito.verify(picasso).submit(actionCaptor.capture());
  }

  @Test
  public void intoTargetWithFitThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).fit().into(mockTarget());
      Assert.fail("Calling into() target with fit() should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void intoTargetNoPlaceholderCallsWithNull() {
    final Target target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
    Mockito.verify(target).onPrepareLoad(null);
  }

  @Test
  public void intoTargetWithNullUriAndResourceIdSkipsAndCancels() {
    final Target target = mockTarget();
    final Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, null, 0).placeholder(placeHolderDrawable).into(target);
    Mockito.verify(picasso).defaultBitmapConfig();
    Mockito.verify(picasso).shutdown();
    Mockito.verify(picasso).cancelRequest(target);
    Mockito.verify(target).onPrepareLoad(placeHolderDrawable);
    Mockito.verifyNoMoreInteractions(picasso);
  }

  @Test
  public void intoTargetWithQuickMemoryCacheCheckDoesNotSubmit() {
    Mockito.when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
    final Target target = mockTarget();
    final Callback callback = mockCallback();
    new RequestCreator(picasso, URI_1, 0).into(target, callback);
    Mockito.verify(target).setImageDrawable(any(PicassoDrawable.class));
    Mockito.verify(callback).onSuccess();
    Mockito.verify(picasso).cancelRequest(target);
    Mockito.verify(picasso, never()).enqueueAndSubmit(any(Picasso.RequestCreator.Action.class));
  }

  @Test
  public void intoTargetWithSkipMemoryPolicy() {
    final Target target = mockTarget();
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).into(target);
    Mockito.verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  }

  @Test
  public void intoTargetAndNotInCacheSubmitsTargetRequest() {
    final Target target = mockTarget();
    final Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    Mockito.verify(target).onPrepareLoad(placeHolderDrawable);
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(BitmapTargetAction.class);
  }

  @Test
  public void targetActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.NORMAL);
  }

  @Test
  public void targetActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH).into(mockTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
  }

  @Test
  public void targetActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
  }

  @Test
  public void targetActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
  }

  @Test
  public void intoImageViewWithNullUriAndResourceIdSkipsAndCancels() {
    final ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, null, 0).into(target);
    Mockito.verify(picasso).cancelRequest(target);
    Mockito.verify(picasso, never()).quickMemoryCacheCheck(anyString());
    Mockito.verify(picasso, never()).enqueueAndSubmit(any(Picasso.RequestCreator.Action.class));
  }

  @Test
  public void intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() {
    final PlatformLruCache cache = new PlatformLruCache(0);
    final Picasso picasso =
        spy(
            new Picasso(
                RuntimeEnvironment.application,
                mock(Dispatcher.class),
                UNUSED_CALL_FACTORY,
                null,
                cache,
                null,
                NO_TRANSFORMERS,
                NO_HANDLERS,
                NO_EVENT_LISTENERS,
                Config.ARGB_8888,
                false,
                false));
    Mockito.doReturn(bitmap).when(picasso).quickMemoryCacheCheck(URI_KEY_1);
    final ImageViewTarget target = mockImageViewTarget();
    final Callback callback = mockCallback();
    new RequestCreator(picasso, URI_1, 0).into(target, callback);
    Mockito.verify(target).setImageDrawable(any(PicassoDrawable.class));
    Mockito.verify(callback).onSuccess();
    Mockito.verify(picasso).cancelRequest(target);
    Mockito.verify(picasso, never()).enqueueAndSubmit(any(Picasso.RequestCreator.Action.class));
  }

  @Test
  public void intoImageViewSetsPlaceholderDrawable() {
    final PlatformLruCache cache = new PlatformLruCache(0);
    final Picasso picasso =
        spy(
            new Picasso(
                RuntimeEnvironment.application,
                mock(Dispatcher.class),
                UNUSED_CALL_FACTORY,
                null,
                cache,
                null,
                NO_TRANSFORMERS,
                NO_HANDLERS,
                NO_EVENT_LISTENERS,
                Config.ARGB_8888,
                false,
                false));
    final ImageViewTarget target = mockImageViewTarget();
    final Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    final Drawable drawableCaptor =
        ArgumentCaptor.forClass(Drawable.class).capture();
    Mockito.verify(target).setImageDrawable(drawableCaptor.getValue());
    Truth.assertThat(TestUtils.shadowOf(drawableCaptor.getValue()).createdFromResId)
        .isEqualTo(0);
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewNoPlaceholderDrawable() {
    final PlatformLruCache cache = new PlatformLruCache(0);
    final Picasso picasso =
        spy(
            new Picasso(
                RuntimeEnvironment.application,
                mock(Dispatcher.class),
                UNUSED_CALL_FACTORY,
                null,
                cache,
                null,
                NO_TRANSFORMERS,
                NO_HANDLERS,
                NO_EVENT_LISTENERS,
                Config.ARGB_8888,
                false,
                false));
    final ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
    Mockito.verifyNoMoreInteractions(target);
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewSetsPlaceholderWithResourceId() {
    final PlatformLruCache cache = new PlatformLruCache(0);
    final Picasso picasso =
        spy(
            new Picasso(
                RuntimeEnvironment.application,
                mock(Dispatcher.class),
                UNUSED_CALL_FACTORY,
                null,
                cache,
                null,
                NO_TRANSFORMERS,
                NO_HANDLERS,
                NO_EVENT_LISTENERS,
                Config.ARGB_8888,
                false,
                false));
    final ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).placeholder(android.R.drawable.picture_frame).into(target);
    final DrawableCaptor drawableCaptor = ArgumentCaptor.forClass(Drawable.class);
    Mockito.verify(target).setImageDrawable(drawableCaptor.capture());
    Truth.assertThat(TestUtils.shadowOf(drawableCaptor.getValue()).createdFromResId)
        .isEqualTo(android.R.drawable.picture_frame);
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void cancelNotOnMainThreadCrashes() {
    doCallRealMethod().when(picasso).cancelRequest(any(BitmapTarget.class));
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(
            () -> {
              try {
                new RequestCreator(picasso, null, 0).into(mockTarget());
                Assert.fail("Should have thrown IllegalStateException");
              } catch (IllegalStateException ignored) {
              } finally {
                latch.countDown();
              }
            })
        .start();
    latch.await();
  }

  @Test
  public void intoNotOnMainThreadCrashes() {
    doCallRealMethod().when(picasso).enqueueAndSubmit(any(Picasso.RequestCreator.Action.class));
    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(
            () -> {
              try {
                new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
                Assert.fail("Should have thrown IllegalStateException");
              } catch (IllegalStateException ignored) {
              } finally {
                latch.countDown();
              }
            })
        .start();
    latch.await();
  }

  @Test
  public void intoImageViewAndNotInCacheSubmitsImageViewRequest() {
    final ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).into(target);
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewWithFitAndNoDimensionsQueuesDeferredImageViewRequest() {
    final ImageViewTarget target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(0);
    when(target.getHeight()).thenReturn(0);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    Mockito.verify(picasso, never()).enqueueAndSubmit(any(Picasso.RequestCreator.Action.class));
    Mockito.verify(picasso).defer(eq(target), any(DeferredRequestCreator.class));
  }

  @Test
  public void intoImageViewWithFitAndDimensionsQueuesImageViewRequest() {
    final ImageViewTarget target = mockFitImageViewTarget(true);
    when(target.getWidth()).thenReturn(100);
    when(target.getHeight()).thenReturn(100);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
  }

  @Test
  public void intoImageViewWithSkipMemoryCachePolicy() {
    final ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(MemoryPolicy.NO_CACHE).into(target);
    Mockito.verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
  }

  @Test
  public void intoImageViewWithFitAndResizeThrows() {
    try {
      final ImageViewTarget target = mockImageViewTarget();
      new RequestCreator(picasso, URI_1, 0).fit().resize(10, 10).into(target);
      Assert.fail("Calling into() ImageView with fit() and resize() should throw exception");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void imageViewActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.NORMAL);
  }

  @Test
  public void imageViewActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH).into(mockImageViewTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
  }

  @Test
  public void imageViewActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
  }

  @Test
  public void imageViewActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockImageViewTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
  }

  @Test
  public void intoRemoteViewsWidgetQueuesAppWidgetAction() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(AppWidgetAction.class);
  }

  @Test
  public void intoRemoteViewsNotificationQueuesNotificationAction() {
    new RequestCreator(picasso, URI_1, 0)
        .into(mockRemoteViews(), 0, 0, mockNotification());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue()).isInstanceOf(NotificationAction.class);
  }

  @Test
  public void intoRemoteViewsWidgetWithPlaceholderDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0)
          .placeholder(new ColorDrawable(0))
          .into(mockRemoteViews(), 0, new int[]{1, 2, 3});
      Assert.fail("Calling into() with placeholder drawable should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithErrorDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).error(new ColorDrawable(0)).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
      Assert.fail("Calling into() with error drawable should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithPlaceholderDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).placeholder(new ColorDrawable(0)).into(mockRemoteViews(), 0, 0, mockNotification());
      Assert.fail("Calling into() with error drawable should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithErrorDrawableThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).error(new ColorDrawable(0)).into(mockRemoteViews(), 0, 0, mockNotification());
      Assert.fail("Calling into() with error drawable should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsWidgetWithFitThrows() {
    try {
      final RemoteViews remoteViews = mockRemoteViews();
      new RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, new int[]{1, 2, 3});
      Assert.fail("Calling fit() into remote views should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void intoRemoteViewsNotificationWithFitThrows() {
    try {
      final RemoteViews remoteViews = mockRemoteViews();
      new RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, 1, mockNotification());
      Assert.fail("Calling fit() into remote views should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void intoTargetNoResizeWithCenterInsideOrCenterCropThrows() {
    try {
      new RequestCreator(picasso, URI_1, 0).centerInside().into(mockTarget());
      Assert.fail("Center inside with unknown width should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      new RequestCreator(picasso, URI_1, 0).centerCrop().into(mockTarget());
      Assert.fail("Center inside with unknown height should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void appWidgetActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.NORMAL);
  }

  @Test
  public void appWidgetActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
  }

  @Test
  public void notificationActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0)
        .into(mockRemoteViews(), 0, 0, mockNotification());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.NORMAL);
  }

  @Test
  public void notificationActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(Priority.HIGH)
        .into(mockRemoteViews(), 0, 0, mockNotification());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(Priority.HIGH);
  }

  @Test
  public void appWidgetActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
  }

  @Test
  public void appWidgetActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag")
        .into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
  }

  @Test
  public void notificationActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0)
        .into(mockRemoteViews(), 0, 0, mockNotification());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
  }

  @Test
  public void notificationActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag")
        .into(mockRemoteViews(), 0, 0, mockNotification());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
  }

  @Test
  public void invalidResize() {
    try {
      mockRequestCreator(picasso).resize(-1, 10);
      Assert.fail("Negative width should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      mockRequestCreator(picasso).resize(10, -1);
      Assert.fail("Negative height should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      mockRequestCreator(picasso).resize(0, 0);
      Assert.fail("Zero dimensions should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void invalidCenterCrop() {
    try {
      mockRequestCreator(picasso).resize(10, 10).centerInside().centerCrop();
      Assert.fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void invalidCenterInside() {
    try {
      mockRequestCreator(picasso).resize(10, 10).centerCrop().centerInside();
      Assert.fail("Calling center inside after center crop should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void invalidPlaceholderImage() {
    try {
      mockRequestCreator(picasso).placeholder(0);
      Assert.fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      mockRequestCreator(picasso).placeholder(1).placeholder(new ColorDrawable(0));
      Assert.fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      mockRequestCreator(picasso).placeholder(new ColorDrawable(0)).placeholder(1);
      Assert.fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void invalidNoPlaceholder() {
    try {
      mockRequestCreator(picasso).noPlaceholder().placeholder(new ColorDrawable(0));
      Assert.fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      mockRequestCreator(picasso).noPlaceholder().placeholder(1);
      Assert.fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      mockRequestCreator(picasso).placeholder(1).noPlaceholder();
      Assert.fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      mockRequestCreator(picasso).placeholder(new ColorDrawable(0)).noPlaceholder();
      Assert.fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void invalidErrorImage() {
    try {
      mockRequestCreator(picasso).error(0);
      Assert.fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
      mockRequestCreator(picasso).error(1).error(new ColorDrawable(0));
      Assert.fail("Two error placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
      mockRequestCreator(picasso).error(new ColorDrawable(0)).error(1);
      Assert.fail("Two error placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void invalidPriority() {
    try {
      mockRequestCreator(picasso).priority(Priority.LOW).priority(Priority.HIGH);
      Assert.fail("Two priorities should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void alreadySetTagThrows() {
    try {
      mockRequestCreator(picasso).tag("tag1").tag("tag2");
      Assert.fail("Two tags should throw exception.");
    } catch (IllegalStateException ignored) {
    }
  }

  @Test
  public void transformationListImplementationValid() {
    final List<Transformation> transformations =
        Collections.singletonList(new TestTransformation("test"));
    mockRequestCreator(picasso).transform(transformations);
  }

  @Test
  public void imageViewActionWithStableKey() {
    new RequestCreator(picasso, URI_1, 0).stableKey(STABLE_1).into(mockImageViewTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.key).isEqualTo(STABLE_URI_KEY_1);
  }

  @Test
  public void imageViewActionWithCustomHeaders() {
    new RequestCreator(picasso, URI_1, 0)
        .addHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
        .into(mockImageViewTarget());
    Mockito.verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    Truth.assertThat(actionCaptor.getValue().request.headers.get(CUSTOM_HEADER_NAME))
        .isEqualTo(CUSTOM_HEADER_VALUE);
  }
}
