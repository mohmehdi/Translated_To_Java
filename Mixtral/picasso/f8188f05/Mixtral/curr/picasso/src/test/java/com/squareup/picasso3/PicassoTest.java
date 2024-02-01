package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.RequestTransformer;
import com.squareup.picasso3.Picasso.Transform;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.TestUtils;
import com.squareup.picasso3.TestUtils.Action;
import com.squareup.picasso3.TestUtils.BitmapTarget;
import com.squareup.picasso3.TestUtils.DeferredRequestCreator;
import com.squareup.picasso3.TestUtils.DrawableTarget;
import com.squareup.picasso3.TestUtils.EventRecorder;
import com.squareup.picasso3.TestUtils.Hunter;
import com.squareup.picasso3.TestUtils.ImageViewTarget;
import com.squareup.picasso3.TestUtils.MockAction;
import com.squareup.picasso3.TestUtils.MockBitmapTarget;
import com.squareup.picasso3.TestUtils.MockDeferredRequestCreator;
import com.squareup.picasso3.TestUtils.MockDrawableTarget;
import com.squareup.picasso3.TestUtils.MockHunter;
import com.squareup.picasso3.TestUtils.MockImageViewTarget;
import com.squareup.picasso3.TestUtils.MockPicasso;
import com.squareup.picasso3.TestUtils.MockRequestCreator;
import com.squareup.picasso3.TestUtils.NoHandlers;
import com.squareup.picasso3.TestUtils.NoTransformers;
import com.squareup.picasso3.TestUtils.PicassoRule;
import com.squareup.picasso3.TestUtils.UriKey;
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import com.squareup.picasso3.TestUtils.URI_1;
import com.squareup.picasso3.TestUtils.URI_KEY_1;
import com.squareup.picasso3.Utils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@LargeTest
public class PicassoTest {
  @Rule public PicassoRule picassoRule = new PicassoRule.Builder(ApplicationProvider.getApplicationContext()).build();

  @Mock private Context context;
  @Mock private Dispatcher dispatcher;
  @Mock private RequestHandler requestHandler;
  @Mock private Listener listener;

  private final PlatformLruCache cache = new PlatformLruCache(2048);
  private final EventRecorder eventRecorder = new EventRecorder();
  private final Bitmap bitmap = BitmapFactory.decodeStream(getClass().getResourceAsStream("/icon.png"));

  private Picasso picasso;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    picasso = new Picasso.Builder(context)
        .dispatcher(dispatcher)
        .callFactory(UNUSED_CALL_FACTORY)
        .closeableCache(null)
        .cache(cache)
        .listener(listener)
        .requestTransformers(new NoTransformers())
        .extraRequestHandlers(new NoHandlers())
        .eventListeners(Collections.singletonList(eventRecorder))
        .defaultBitmapConfig(Config.ARGB_8888)
        .indicatorsEnabled(false)
        .isLoggingEnabled(false)
        .build();
  }

  @Test
  public void submitWithTargetInvokesDispatcher() {
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Truth.assertThat(picasso.targetToAction).isEmpty();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Mockito.verify(dispatcher).dispatchSubmit(action);
  }

  @Test
  public void submitWithSameActionDoesNotCancel() {
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    picasso.enqueueAndSubmit(action);
    Mockito.verify(dispatcher).dispatchSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(picasso.targetToAction.containsValue(action)).isTrue();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(action.cancelled).isFalse();
    Mockito.verify(dispatcher, never()).dispatchCancel(action);
  }

  @Test
  public void quickMemoryCheckReturnsBitmapIfInCache() {
    cache.put(URI_KEY_1, bitmap);
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    Truth.assertThat(cached).isEqualTo(bitmap);
    Truth.assertThat(eventRecorder.cacheHits).isGreaterThan(0);
  }

  @Test
  public void quickMemoryCheckReturnsNullIfNotInCache() {
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    Truth.assertThat(cached).isNull();
    Truth.assertThat(eventRecorder.cacheMisses).isGreaterThan(0);
  }

  @Test
  public void completeInvokesSuccessOnAllSuccessfulRequests() {
    Action action1 = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1);
    Action action2 = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;

    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action1);
    Truth.assertThat(action2.completedResult).isNull();
  }

  @Test
  public void completeInvokesErrorOnAllFailedRequests() {
    Action action1 = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Exception exception = Mockito.mock(Exception.class);
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1, exception);
    Action action2 = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(action1.errorException).hasCauseThat().isEqualTo(exception);
    Truth.assertThat(action2.errorException).isNull();
    Mockito.verify(listener).onImageLoadFailed(picasso, URI_1, action1.errorException);
  }

  @Test
  public void completeInvokesErrorOnFailedResourceRequests() {
    Action action = new MockAction(
        picasso, URI_KEY_1, null, 123, new MockImageViewTarget());
    Exception exception = Mockito.mock(Exception.class);
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action, exception);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    Mockito.verify(listener).onImageLoadFailed(picasso, null, action.errorException);
  }

  @Test
  public void completeDeliversToSingle() {
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
  }

  @Test
  public void completeWithReplayDoesNotRemove() {
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.run();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    picasso.complete(hunter);
    Truth.assertThat(picasso.targetToAction).hasSize(1);

    verifyActionComplete(action);
  }

  @Test
  public void completeDeliversToSingleAndMultiple() {
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Action action2 = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
    verifyActionComplete(action2);
  }

  @Test
  public void completeSkipsIfNoActions() {
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, new MockImageViewTarget());
    Hunter hunter = new MockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).isNull();
  }

  @Test
  public void resumeActionTriggersSubmitOnPausedAction() {
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    Action action = new Action(new MockPicasso(ApplicationProvider.getApplicationContext()), request) {
      @Override
      public void complete(Result result) {
        fail("Test execution should not call this method");
      }

      @Override
      public void error(Exception e) {
        fail("Test execution should not call this method");
      }

      @Override
      public Object getTarget() {
        return this;
      }
    };
    picasso.resumeAction(action);
    Mockito.verify(dispatcher).dispatchSubmit(action);
  }

  @Test
  public void resumeActionImmediatelyCompletesCachedRequest() {
    cache.put(URI_KEY_1, bitmap);
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    Action action = new Action(new MockPicasso(ApplicationProvider.getApplicationContext()), request) {
      @Override
      public void complete(Result result) {
        Truth.assertThat(result).isInstanceOf(Result.Bitmap.class);
        Result.Bitmap bitmapResult = (Result.Bitmap) result;
        Truth.assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
        Truth.assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.MEMORY);
      }

      @Override
      public void error(Exception e) {
        fail("Reading from memory cache should not throw an exception");
      }

      @Override
      public Object getTarget() {
        return this;
      }
    };

    picasso.resumeAction(action);
  }

  @Test
  public void cancelExistingRequestWithUnknownTarget() {
    ImageViewTarget target = new MockImageViewTarget();
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, target);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    Truth.assertThat(action.cancelled).isFalse();
    Mockito.verifyNoInteractions(dispatcher);
  }

  @Test
  public void cancelExistingRequestWithImageViewTarget() {
    ImageViewTarget target = new MockImageViewTarget();
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Truth.assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithDeferredImageViewTarget() {
    ImageViewTarget target = new MockImageViewTarget();
    RequestCreator creator = new MockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator =
        new MockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test
  public void enqueueingDeferredRequestCancelsThePreviousOne() {
    ImageViewTarget target = new MockImageViewTarget();
    RequestCreator creator = new MockRequestCreator(picasso);
    DeferredRequestCreator firstRequestCreator =
        new MockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, firstRequestCreator);
    picasso.defer(target, firstRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);

    DeferredRequestCreator secondRequestCreator =
        new MockDeferredRequestCreator(creator, target);
    picasso.defer(target, secondRequestCreator);
    Mockito.verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test
  public void cancelExistingRequestWithBitmapTarget() {
    BitmapTarget target = new MockBitmapTarget();
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Truth.assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithDrawableTarget() {
    DrawableTarget target = new MockDrawableTarget();
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Truth.assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithRemoteViewTarget() {
    int layoutId = 0;
    int viewId = 1;
    RemoteViews remoteViews = new RemoteViews(context.getPackageName(), layoutId);
    RemoteViewsTarget target = new RemoteViewsTarget(remoteViews, viewId);
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(remoteViews, viewId);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Truth.assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelTagAllActions() {
    String tag = "TAG";
    ImageViewTarget target = new MockImageViewTarget();
    Action action = new MockAction(picasso, URI_KEY_1, URI_1, target, tag);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelTag(tag);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Truth.assertThat(action.cancelled).isTrue();
  }

  @Test
  public void cancelTagAllDeferredRequests() {
    String tag = "TAG";
    ImageViewTarget target = new MockImageViewTarget();
    RequestCreator creator = new MockRequestCreator(picasso).tag(tag);
    DeferredRequestCreator deferredRequestCreator =
        new MockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelTag(tag);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test
  public void deferAddsToMap() {
    ImageViewTarget target = new MockImageViewTarget();
    RequestCreator creator = new MockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator =
        new MockDeferredRequestCreator(creator, target);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
    picasso.defer(target, deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test
  public void shutdown() {
    cache.put("key", bitmap);
    Truth.assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    Truth.assertThat(cache.size()).isEqualTo(0);
    Truth.assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher).shutdown();
    Truth.assertThat(picasso.shutdown).isTrue();
  }

  @Test
  public void shutdownClosesUnsharedCache() {
    okhttp3.Cache cache = new okhttp3.Cache(picassoRule.getFolder(), 100);
    Picasso picasso =
        new Picasso.Builder(ApplicationProvider.getApplicationContext())
            .withCache(cache)
            .build();
    picasso.shutdown();
    Truth.assertThat(cache.isClosed()).isTrue();
  }

  @Test
  public void shutdownTwice() {
    cache.put("key", bitmap);
    Truth.assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    picasso.shutdown();
    Truth.assertThat(cache.size()).isEqualTo(0);
    Truth.assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher, Mockito.times(1)).shutdown();
    Truth.assertThat(picasso.shutdown).isTrue();
  }

  @Test
  public void shutdownClearsTargetsToActions() {
    ImageViewTarget target = new MockImageViewTarget();
    picasso.targetToAction.put(target, new Action(picasso, URI_1));
    picasso.shutdown();
    Truth.assertThat(picasso.targetToAction).isEmpty();
  }

  @Test
  public void shutdownClearsDeferredRequests() {
    ImageViewTarget target = new MockImageViewTarget();
    RequestCreator creator = new MockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator =
        new MockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.shutdown();
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test
  public void loadThrowsWithInvalidInput() {
    try {
      picasso.load("");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load("      ");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
    try {
      picasso.load(0);
      fail("Zero resourceId should throw exception.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void builderInvalidCache() {
    try {
      new Picasso.Builder(ApplicationProvider.getApplicationContext()).withCacheSize(-1);
      fail();
    } catch (IllegalArgumentException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .isEqualTo("maxByteCount < 0: -1");
    }
  }

  @Test
  public void builderWithoutRequestHandler() {
    Picasso picasso =
        new Picasso.Builder(ApplicationProvider.getApplicationContext()).build();
    Truth.assertThat(picasso.requestHandlers).isNotEmpty();
    Truth.assertThat(picasso.requestHandlers).doesNotContain(requestHandler);
  }

  @Test
  public void builderWithRequestHandler() {
    Picasso picasso =
        new Picasso.Builder(ApplicationProvider.getApplicationContext())
            .addRequestHandler(requestHandler)
            .build();
    Truth.assertThat(picasso.requestHandlers).isNotNull();
    Truth.assertThat(picasso.requestHandlers).isNotEmpty();
    Truth.assertThat(picasso.requestHandlers).contains(requestHandler);
  }

  @Test
  public void builderWithDebugIndicators() {
    Picasso picasso =
        new Picasso.Builder(ApplicationProvider.getApplicationContext())
            .indicatorsEnabled(true)
            .build();
    Truth.assertThat(picasso.indicatorsEnabled).isTrue();
  }

  @Test
  public void evictAll() {
    Picasso picasso =
        new Picasso.Builder(ApplicationProvider.getApplicationContext())
            .indicatorsEnabled(true)
            .build();
    picasso.cache.put("key", bitmap);
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.evictAll();
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateString() {
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    picasso.cache.put(request.key, bitmap);
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate("https:");
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateFile() {
    Request request = new Request.Builder(Uri.fromFile(new File("/foo/bar/baz"))).build();
    picasso.cache.put(request.key, bitmap);
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate(new File("/foo/bar/baz"));
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateUri() {
    Request request = new Request.Builder(URI_1).build();
    picasso.cache.put(request.key, bitmap);
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate(URI_1);
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void clonedRequestHandlersAreIndependent() {
    Picasso original =
        defaultPicasso(ApplicationProvider.getApplicationContext(), false, false);

    Picasso cloned = original.newBuilder().build();

    Truth.assertThat(cloned.requestTransformers).hasSize(NUM_BUILTIN_TRANSFORMERS);
    Truth.assertThat(cloned.requestHandlers).hasSize(NUM_BUILTIN_HANDLERS);
  }

  @Test
  public void cloneSharesStatefulInstances() {
    Picasso parent =
        defaultPicasso(ApplicationProvider.getApplicationContext(), true, true);

    Picasso child = parent.newBuilder().build();

    Truth.assertThat(child.context).isEqualTo(parent.context);
    Truth.assertThat(child.callFactory).isEqualTo(parent.callFactory);
    Truth.assertThat(
            ((HandlerDispatcher) child.dispatcher).service)
        .isEqualTo(((HandlerDispatcher) parent.dispatcher).service);
    Truth.assertThat(child.cache).isEqualTo(parent.cache);
    Truth.assertThat(child.listener).isEqualTo(parent.listener);
    Truth.assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers);

    Truth.assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size);
    child.requestHandlers.forEachIndexed(
        (index, it) -> Truth.assertThat(it).isInstanceOf(parent.requestHandlers[index].getClass()));

    Truth.assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig);
    Truth.assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled);
    Truth.assertThat(child.isLoggingEnabled).isEqualTo(parent.isLoggingEnabled);

    Truth.assertThat(child.targetToAction).isEqualTo(parent.targetToAction);
    Truth.assertThat(child.targetToDeferredRequestCreator).isEqualTo(
        parent.targetToDeferredRequestCreator);
  }

  @Test
  public void cloneSharesCoroutineDispatchers() {
    Picasso parent =
        defaultPicasso(ApplicationProvider.getApplicationContext(), true, true)
            .newBuilder()
            .dispatchers()
            .build();
    Picasso child = parent.newBuilder().build();

    InternalCoroutineDispatcher parentDispatcher =
        (InternalCoroutineDispatcher) parent.dispatcher;
    InternalCoroutineDispatcher childDispatcher =
        (InternalCoroutineDispatcher) child.dispatcher;
    Truth.assertThat(childDispatcher.mainDispatcher).isEqualTo(parentDispatcher.mainDispatcher);
    Truth.assertThat(childDispatcher.backgroundDispatcher)
        .isEqualTo(parentDispatcher.backgroundDispatcher);
  }

  private void verifyActionComplete(Action action) {
    Result result = action.completedResult;
    Truth.assertThat(result).isNotNull();
    Truth.assertThat(result).isInstanceOf(Result.Bitmap.class);
    Result.Bitmap bitmapResult = (Result.Bitmap) result;
    Truth.assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
    Truth.assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
  }
    private static final int NUM_BUILTIN_HANDLERS = 8;
    private static final int NUM_BUILTIN_TRANSFORMERS = 0;

}


