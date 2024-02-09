package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static com.squareup.picasso3.MemoryPolicy.NO_CACHE;
import static com.squareup.picasso3.Picasso.Priority.HIGH;
import static com.squareup.picasso3.Picasso.Priority.LOW;
import static com.squareup.picasso3.Picasso.Priority.NORMAL;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

@RunWith(RobolectricTestRunner.class)
public class RequestCreatorTest {
    private final ArgumentCaptor<Action> actionCaptor = argumentCaptor(Action.class);
    private final Picasso picasso = spy(mockPicasso(RuntimeEnvironment.application));
    private final Bitmap bitmap = makeBitmap();

    @Test
    public void getOnMainCrashes() {
        try {
            new RequestCreator(picasso, URI_1, 0).get();
            fail("Calling get() on main thread should throw exception");
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void loadWithShutdownCrashes() {
        picasso.shutdown = true;
        try {
            new RequestCreator(picasso, URI_1, 0).fetch();
            fail("Should have crashed with a shutdown picasso.");
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void getReturnsNullIfNullUriAndResourceId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Bitmap[] result = new Bitmap[1];
        new Thread(() -> {
            try {
                result[0] = new RequestCreator(picasso, null, 0).get();
            } catch (IOException e) {
                fail(e.getMessage());
            } finally {
                latch.countDown();
            }
        }).start();
        latch.await();

        Truth.assertThat(result[0]).isNull();
        verify(picasso).defaultBitmapConfig();
        verify(picasso).shutdown();
        verifyNoMoreInteractions(picasso);
    }

    @Test
    public void fetchSubmitsFetchRequest() {
        new RequestCreator(picasso, URI_1, 0).fetch();
        verify(picasso).submit(actionCaptor.capture());
        Truth.assertThat(actionCaptor.getValue()).isInstanceOf(FetchAction.class);
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
        Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(LOW);
    }

    @Test
    public void fetchWithCustomPriority() {
        new RequestCreator(picasso, URI_1, 0).priority(HIGH).fetch();
        verify(picasso).submit(actionCaptor.capture());
        Truth.assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
    }

    @Test
    public void fetchWithCache() {
        when(picasso.quickMemoryCacheCheck(URI_KEY_1)).thenReturn(bitmap);
        new RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).fetch();
        verify(picasso, never()).enqueueAndSubmit(any(Action.class));
    }

    @Test
    public void fetchWithMemoryPolicyNoCache() {
        new RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).fetch();
        verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
        verify(picasso).submit(actionCaptor.capture());
    }

    @Test
    public void intoTargetWithFitThrows() {
        try {
            new RequestCreator(picasso, URI_1, 0).fit().into(mockTarget());
            fail("Calling into() target with fit() should throw exception");
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void intoTargetNoPlaceholderCallsWithNull() {
        Target target = mockTarget();
        new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
        verify(target).onPrepareLoad(null);
    }

    @Test
    public void intoTargetWithNullUriAndResourceIdSkipsAndCancels() {
        Target target = mockTarget();
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
        Target target = mockTarget();
        new RequestCreator(picasso, URI_1, 0).into(target);
        verify(target).onBitmapLoaded(bitmap, MEMORY);
        verify(picasso).cancelRequest(target);
        verify(picasso, never()).enqueueAndSubmit(any(Action.class));
    }

    @Test
    public void intoTargetWithSkipMemoryPolicy() {
        Target target = mockTarget();
        new RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).into(target);
        verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
    }

    @Test
    public void intoTargetAndNotInCacheSubmitsTargetRequest() {
        Target target = mockTarget();
        Drawable placeHolderDrawable = mock(Drawable.class);
        new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
        verify(target).onPrepareLoad(placeHolderDrawable);
        verify(picasso).enqueueAndSubmit(actionCaptor.capture());
        Truth.assertThat(actionCaptor.getValue()).isInstanceOf(BitmapTargetAction.class);
    }




    @Test
public void targetActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void targetActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void targetActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void targetActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void intoImageViewWithNullUriAndResourceIdSkipsAndCancels() {
    ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, null, 0).into(target);
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).quickMemoryCacheCheck(anyString());
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void intoImageViewWithQuickMemoryCacheCheckDoesNotSubmit() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = spy(
            new Picasso(
                    RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
                    null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
                    false, false
            )
    );
    doReturn(bitmap).when(picasso).quickMemoryCacheCheck(URI_KEY_1);
    ImageViewTarget target = mockImageViewTarget();
    Callback callback = mockCallback();
    new RequestCreator(picasso, URI_1, 0).into(target, callback);
    verify(target).setImageDrawable(any(PicassoDrawable.class));
    verify(callback).onSuccess();
    verify(picasso).cancelRequest(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
}

@Test
public void intoImageViewSetsPlaceholderDrawable() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = spy(
            new Picasso(
                    RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
                    null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
                    false, false
            )
    );
    ImageViewTarget target = mockImageViewTarget();
    Drawable placeHolderDrawable = mock(Drawable.class);
    new RequestCreator(picasso, URI_1, 0).placeholder(placeHolderDrawable).into(target);
    verify(target).setImageDrawable(placeHolderDrawable);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewNoPlaceholderDrawable() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = spy(
            new Picasso(
                    RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
                    null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
                    false, false
            )
    );
    ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).noPlaceholder().into(target);
    verifyNoMoreInteractions(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewSetsPlaceholderWithResourceId() {
    PlatformLruCache cache = new PlatformLruCache(0);
    Picasso picasso = spy(
            new Picasso(
                    RuntimeEnvironment.application, mock(Dispatcher.class), UNUSED_CALL_FACTORY,
                    null, cache, null, NO_TRANSFORMERS, NO_HANDLERS, NO_EVENT_LISTENERS, ARGB_8888,
                    false, false
            )
    );
    ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).placeholder(android.R.drawable.picture_frame).into(target);
    ArgumentCaptor<Drawable> drawableCaptor = ArgumentCaptor.forClass(Drawable.class);
    verify(target).setImageDrawable(drawableCaptor.capture());
    assertThat(shadowOf(drawableCaptor.getValue()).createdFromResId)
            .isEqualTo(android.R.drawable.picture_frame);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void cancelNotOnMainThreadCrashes() {
    doCallRealMethod().when(picasso).cancelRequest(any(BitmapTarget.class));
    CountDownLatch latch = new CountDownLatch(1);
    new Thread(() -> {
        try {
            new RequestCreator(picasso, null, 0).into(mockTarget());
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ignored) {
        } finally {
            latch.countDown();
        }
    }).start();
    latch.await();
}

@Test
public void intoNotOnMainThreadCrashes() {
    doCallRealMethod().when(picasso).enqueueAndSubmit(any(Action.class));
    CountDownLatch latch = new CountDownLatch(1);
    new Thread(() -> {
        try {
            new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
            fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ignored) {
        } finally {
            latch.countDown();
        }
    }).start();
    latch.await();
}




@Test
public void intoImageViewAndNotInCacheSubmitsImageViewRequest() {
    ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).into(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewWithFitAndNoDimensionsQueuesDeferredImageViewRequest() {
    FitImageViewTarget target = mockFitImageViewTarget(true);
    when(target.width).thenReturn(0);
    when(target.height).thenReturn(0);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    verify(picasso, never()).enqueueAndSubmit(any(Action.class));
    verify(picasso).defer(eq(target), any(DeferredRequestCreator.class));
}

@Test
public void intoImageViewWithFitAndDimensionsQueuesImageViewRequest() {
    FitImageViewTarget target = mockFitImageViewTarget(true);
    when(target.width).thenReturn(100);
    when(target.height).thenReturn(100);
    new RequestCreator(picasso, URI_1, 0).fit().into(target);
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(ImageViewAction.class);
}

@Test
public void intoImageViewWithSkipMemoryCachePolicy() {
    ImageViewTarget target = mockImageViewTarget();
    new RequestCreator(picasso, URI_1, 0).memoryPolicy(NO_CACHE).into(target);
    verify(picasso, never()).quickMemoryCacheCheck(URI_KEY_1);
}

@Test
public void intoImageViewWithFitAndResizeThrows() {
    try {
        ImageViewTarget target = mockImageViewTarget();
        new RequestCreator(picasso, URI_1, 0).fit().resize(10, 10).into(target);
        fail("Calling into() ImageView with fit() and resize() should throw exception");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void imageViewActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void imageViewActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void imageViewActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void imageViewActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag").into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void intoRemoteViewsWidgetQueuesAppWidgetAction() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(AppWidgetAction.class);
}

@Test
public void intoRemoteViewsNotificationQueuesNotificationAction() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue()).isInstanceOf(NotificationAction.class);
}

@Test
public void intoRemoteViewsWidgetWithPlaceholderDrawableThrows() {
    try {
        new RequestCreator(picasso, URI_1, 0).placeholder(new ColorDrawable(0))
                .into(mockRemoteViews(), 0, new int[]{1, 2, 3});
        fail("Calling into() with placeholder drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsWidgetWithErrorDrawableThrows() {
    try {
        new RequestCreator(picasso, URI_1, 0).error(new ColorDrawable(0))
                .into(mockRemoteViews(), 0, new int[]{1, 2, 3});
        fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsNotificationWithPlaceholderDrawableThrows() {
    try {
        new RequestCreator(picasso, URI_1, 0).placeholder(new ColorDrawable(0))
                .into(mockRemoteViews(), 0, 0, mockNotification());
        fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsNotificationWithErrorDrawableThrows() {
    try {
        new RequestCreator(picasso, URI_1, 0).error(new ColorDrawable(0))
                .into(mockRemoteViews(), 0, 0, mockNotification());
        fail("Calling into() with error drawable should throw exception");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void intoRemoteViewsWidgetWithFitThrows() {
    try {
        RemoteViews remoteViews = mockRemoteViews();
        new RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, new int[]{1, 2, 3});
        fail("Calling fit() into remote views should throw exception");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void intoRemoteViewsNotificationWithFitThrows() {
    try {
        RemoteViews remoteViews = mockRemoteViews();
        new RequestCreator(picasso, URI_1, 0).fit().into(remoteViews, 1, 1, mockNotification());
        fail("Calling fit() into remote views should throw exception");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void intoTargetNoResizeWithCenterInsideOrCenterCropThrows() {
    try {
        new RequestCreator(picasso, URI_1, 0).centerInside().into(mockTarget());
        fail("Center inside with unknown width should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        new RequestCreator(picasso, URI_1, 0).centerCrop().into(mockTarget());
        fail("Center inside with unknown height should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void appWidgetActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void appWidgetActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void notificationActionWithDefaultPriority() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(NORMAL);
}

@Test
public void notificationActionWithCustomPriority() {
    new RequestCreator(picasso, URI_1, 0).priority(HIGH)
            .into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.priority).isEqualTo(HIGH);
}

@Test
public void appWidgetActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0).into(mockRemoteViews(), 0, new int[]{1, 2, 3});
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void appWidgetActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag")
            .into(remoteViews = mockRemoteViews(), viewId = 0, appWidgetIds = new int[]{1, 2, 3});
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void notificationActionWithDefaultTag() {
    new RequestCreator(picasso, URI_1, 0)
            .into(
                    remoteViews = mockRemoteViews(),
                    viewId = 0,
                    notificationId = 0,
                    notification = mockNotification()
            );
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo(actionCaptor.getValue());
}

@Test
public void notificationActionWithCustomTag() {
    new RequestCreator(picasso, URI_1, 0).tag("tag")
            .into(mockRemoteViews(), 0, 0, mockNotification());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().tag).isEqualTo("tag");
}

@Test
public void invalidResize() {
    try {
        mockRequestCreator(picasso).resize(-1, 10);
        fail("Negative width should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        mockRequestCreator(picasso).resize(10, -1);
        fail("Negative height should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        mockRequestCreator(picasso).resize(0, 0);
        fail("Zero dimensions should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void invalidCenterCrop() {
    try {
        mockRequestCreator(picasso).resize(10, 10).centerInside().centerCrop();
        fail("Calling center crop after center inside should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidCenterInside() {
    try {
        mockRequestCreator(picasso).resize(10, 10).centerCrop().centerInside();
        fail("Calling center inside after center crop should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidPlaceholderImage() {
    try {
        mockRequestCreator(picasso).placeholder(0);
        fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        mockRequestCreator(picasso).placeholder(1).placeholder(new ColorDrawable(0));
        fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        mockRequestCreator(picasso).placeholder(new ColorDrawable(0)).placeholder(1);
        fail("Two placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidNoPlaceholder() {
    try {
        mockRequestCreator(picasso).noPlaceholder().placeholder(new ColorDrawable(0));
        fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        mockRequestCreator(picasso).noPlaceholder().placeholder(1);
        fail("Placeholder after no placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        mockRequestCreator(picasso).placeholder(1).noPlaceholder();
        fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        mockRequestCreator(picasso).placeholder(new ColorDrawable(0)).noPlaceholder();
        fail("No placeholder after placeholder should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidErrorImage() {
    try {
        mockRequestCreator(picasso).error(0);
        fail("Resource ID of zero should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        mockRequestCreator(picasso).error(1).error(new ColorDrawable(0));
        fail("Two error placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
    try {
        mockRequestCreator(picasso).error(new ColorDrawable(0)).error(1);
        fail("Two error placeholders should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void invalidPriority() {
    try {
        mockRequestCreator(picasso).priority(LOW).priority(HIGH);
        fail("Two priorities should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void alreadySetTagThrows() {
    try {
        mockRequestCreator(picasso).tag("tag1").tag("tag2");
        fail("Two tags should throw exception.");
    } catch (IllegalStateException ignored) {
    }
}

@Test
public void transformationListImplementationValid() {
    List<Transformation> transformations = Collections.singletonList(new TestTransformation("test"));
    mockRequestCreator(picasso).transform(transformations);
    // TODO verify something!
}

@Test
public void imageViewActionWithStableKey() {
    new RequestCreator(picasso, URI_1, 0).stableKey(STABLE_1).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.key).isEqualTo(STABLE_URI_KEY_1);
}

@Test
public void notPurgeable() {
    new RequestCreator(picasso, URI_1, 0).into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.purgeable).isFalse();
}

@Test
public void purgeable() {
    new RequestCreator(picasso, URI_1, 0).purgeable().into(mockImageViewTarget());
    verify(picasso).enqueueAndSubmit(actionCaptor.capture());
    assertThat(actionCaptor.getValue().request.purgeable).isTrue();
}
}