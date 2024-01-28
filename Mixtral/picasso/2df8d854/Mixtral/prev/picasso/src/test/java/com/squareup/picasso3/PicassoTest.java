package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.RemoteViews;
import android.widget.RemoteViews.RemoteViewsTarget;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.Listener;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.RequestHandler.Result.BitmapResult;
import com.squareup.picasso3.TestUtils.EventRecorder;
import com.squareup.picasso3.TestUtils.FakeAction;
import com.squareup.picasso3.TestUtils.NO_HANDLERS;
import com.squareup.picasso3.TestUtils.NO_TRANSFORMERS;
import com.squareup.picasso3.TestUtils.UNUSED_CALL_FACTORY;
import com.squareup.picasso3.TestUtils.URI_1;
import com.squareup.picasso3.TestUtils.URI_KEY_1;
import com.squareup.picasso3.TestUtils.defaultPicasso;
import com.squareup.picasso3.TestUtils.makeBitmap;
import com.squareup.picasso3.TestUtils.mockAction;
import com.squareup.picasso3.TestUtils.mockDeferredRequestCreator;
import com.squareup.picasso3.TestUtils.mockHunter;
import com.squareup.picasso3.TestUtils.mockImageViewTarget;
import com.squareup.picasso3.TestUtils.mockPicasso;
import com.squareup.picasso3.TestUtils.mockRequestCreator;
import com.squareup.picasso3.TestUtils.mockTarget;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PicassoTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock
  private Context context;
  @Mock
  private Dispatcher dispatcher;
  @Mock
  private RequestHandler requestHandler;
  @Mock
  private Listener listener;

  private PlatformLruCache cache;
  private EventRecorder eventRecorder;
  private Bitmap bitmap;

  private Picasso picasso;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cache = new PlatformLruCache(2048);
    eventRecorder = new EventRecorder();
    bitmap = makeBitmap();
    picasso = new Picasso.Builder(context, dispatcher, UNUSED_CALL_FACTORY, cache, null, listener,
        NO_TRANSFORMERS, NO_HANDLERS, Collections.singletonList(eventRecorder),
        Config.ARGB_8888, false, false)
      .build();
  }

  @Test
  public void submitWithTargetInvokesDispatcher() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Truth.assertThat(picasso.targetToAction).isEmpty();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Mockito.verify(dispatcher).dispatchSubmit(action);
  }

  @Test
  public void submitWithSameActionDoesNotCancel() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    picasso.enqueueAndSubmit(action);
    Mockito.verify(dispatcher).dispatchSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(picasso.targetToAction.containsValue(action)).isTrue();
    picasso.enqueueAndSubmit(action);
    Assert.assertFalse(action.cancelled);
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
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);
    verifyActionComplete(action1);
    Assert.assertNull(action2.completedResult);
  }

  @Test
  public void completeInvokesErrorOnAllFailedRequests() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Exception exception = Mockito.mock(Exception.class);
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action1, exception);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);
    Assert.assertThat(action1.errorException).hasCauseThat().isEqualTo(exception);
    Assert.assertThat(action2.errorException).isNull();
    Mockito.verify(listener).onImageLoadFailed(picasso, URI_1, action1.errorException);
  }

  @Test
  public void completeInvokesErrorOnFailedResourceRequests() {
    Action action = mockAction(picasso, URI_KEY_1, null, 123, mockImageViewTarget());
    Exception exception = Mockito.mock(Exception.class);
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action, exception);
    hunter.run();
    picasso.complete(hunter);
    Assert.assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    Mockito.verify(listener).onImageLoadFailed(picasso, null, action.errorException);
  }

  @Test
  public void completeDeliversToSingle() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action);
    hunter.run();
    picasso.complete(hunter);
    verifyActionComplete(action);
  }

  @Test
  public void completeWithReplayDoesNotRemove() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action);
    hunter.run();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    picasso.complete(hunter);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    verifyActionComplete(action);
  }

  @Test
  public void completeDeliversToSingleAndMultiple() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);
    verifyActionComplete(action);
    verifyActionComplete(action2);
  }

  @Test
  public void completeSkipsIfNoActions() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, new BitmapResult(bitmap, LoadedFrom.MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);
    Assert.assertNull(hunter.action);
    Assert.assertNull(hunter.actions);
  }

  @Test
  public void resumeActionTriggersSubmitOnPausedAction() {
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    Action action = new Action(mockPicasso(RuntimeEnvironment.application), request) {
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
    Action action = new Action(mockPicasso(RuntimeEnvironment.application), request) {
      @Override
      public void complete(Result result) {
        BitmapResult bitmapResult = (BitmapResult) result;
        Assert.assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
        Assert.assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.MEMORY);
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
    ImageViewTarget target = mockImageViewTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Assert.assertFalse(action.cancelled);
    Mockito.verifyZeroInteractions(dispatcher);
  }

  @Test
  public void cancelExistingRequestWithImageViewTarget() {
    ImageViewTarget target = mockImageViewTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithDeferredImageViewTarget() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test
  public void enqueueingDeferredRequestCancelsThePreviousOne() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator firstRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, firstRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
    DeferredRequestCreator secondRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, secondRequestCreator);
    Mockito.verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test
  public void cancelExistingRequestWithTarget() {
    Target target = mockTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithRemoteViewTarget() {
    int layoutId = 0;
    int viewId = 1;
    RemoteViews remoteViews = new RemoteViews("com.squareup.picasso3.test", layoutId);
    RemoteViewsTarget target = new RemoteViewsTarget(remote, viewId);
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(remote, viewId);
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelTagAllActions() {
    ImageViewTarget target = mockImageViewTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target, "TAG");
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Assert.assertFalse(action.cancelled);
    picasso.cancelTag("TAG");
    Truth.assertThat(picasso.targetToAction).isEmpty();
    Assert.assertTrue(action.cancelled);
  }

  @Test
  public void cancelTagAllDeferredRequests() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso).tag("TAG");
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, deferredRequestCreator);
    picasso.cancelTag("TAG");
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
  }

  @Test
  public void deferAddsToMap() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
    picasso.defer(target, deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test
  public void shutdown() {
    cache.put("key", makeBitmap(1, 1));
    Assert.assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    Assert.assertThat(cache.size()).isEqualTo(0);
    Assert.assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher).shutdown();
    Assert.assertThat(picasso.shutdown).isTrue();
  }

  @Test
  public void shutdownClosesUnsharedCache() {
    okhttp3.Cache cache = new okhttp3.Cache(temporaryFolder.root, 100);
    Picasso picasso = Picasso.Builder(RuntimeEnvironment.application, dispatcher,
        UNUSED_CALL_FACTORY, cache, cache, listener,
        NO_TRANSFORMERS, NO_HANDLERS, Collections.singletonList(eventRecorder),
        Config.ARGB_8888, false, false)
      .build();
    picasso.shutdown();
    Assert.assertThat(cache.isClosed()).isTrue();
  }

  @Test
  public void shutdownTwice() {
    cache.put("key", makeBitmap(1, 1));
    Assert.assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    picasso.shutdown();
    Assert.assertThat(cache.size()).isEqualTo(0);
    Assert.assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher).shutdown();
    Assert.assertThat(picasso.shutdown).isTrue();
  }

  @Test
  public void shutdownClearsDeferredRequests() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
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
    } catch (IllegalArgumentException expected) {}
    try {
      picasso.load(" ");
      fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException expected) {}
    try {
      picasso.load(0);
      fail("Zero resourceId should throw exception.");
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void builderInvalidCache() {
    try {
      new Picasso.Builder(RuntimeEnvironment.application).withCacheSize(-1)
        .build();
      fail();
    } catch (IllegalArgumentException expected) {
      Assert.assertThat(expected).hasMessageThat().isEqualTo("maxByteCount < 0: -1");
    }
  }

  @Test
  public void builderWithoutRequestHandler() {
    Picasso picasso = Picasso.Builder(RuntimeEnvironment.application).build();
    Truth.assertThat(picasso.requestHandlers).isNotEmpty();
    Truth.assertThat(picasso.requestHandlers).doesNotContain(requestHandler);
  }

  @Test
  public void builderWithRequestHandler() {
    Picasso picasso = Picasso.Builder(RuntimeEnvironment.application)
      .addRequestHandler(requestHandler)
      .build();
    Truth.assertThat(picasso.requestHandlers).isNotNull();
    Truth.assertThat(picasso.requestHandlers).isNotEmpty();
    Truth.assertThat(picasso.requestHandlers).contains(requestHandler);
  }

  @Test
  public void builderWithDebugIndicators() {
    Picasso picasso = Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    Assert.assertThat(picasso.indicatorsEnabled).isTrue();
  }

  @Test
  public void evictAll() {
    Picasso picasso = Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    picasso.cache.put("key", makeBitmap(1, 1));
    Assert.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.evictAll();
    Assert.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateString() {
    Request request = new Request.Builder(Uri.parse("https://example.com")).build();
    picasso.cache.put(request.key, makeBitmap(1, 1));
    Assert.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate("https://example.com");
    Assert.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateFile() {
    Request request = new Request.Builder(Uri.fromFile(new File("/foo/bar/baz"))).build();
    picasso.cache.put(request.key, makeBitmap(1, 1));
    Assert.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate(new File("/foo/bar/baz"));
    Assert.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateUri() {
    Request request = new Request.Builder(URI_1).build();
    picasso.cache.put(request.key, makeBitmap(1, 1));
    Assert.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate(URI_1);
    Assert.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void clonedRequestHandlersAreIndependent() {
    Picasso original = defaultPicasso(RuntimeEnvironment.application, false, false);
    original.newBuilder()
      .addRequestTransformer(NOOP_TRANSFORMER)
      .addRequestHandler(NOOP_REQUEST_HANDLER)
      .build();
    Assert.assertThat(original.requestTransformers).hasSize(NUM_BUILTIN_TRANSFORMERS);
    Assert.assertThat(original.requestHandlers).hasSize(NUM_BUILTIN_HANDLERS);
  }

  @Test
  public void cloneSharesStatefulInstances() {
    Picasso parent = defaultPicasso(RuntimeEnvironment.application, true, true);
    Picasso child = parent.newBuilder().build();
    Assert.assertThat(child.context).isEqualTo(parent.context);
    Assert.assertThat(child.callFactory).isEqualTo(parent.callFactory);
    Assert.assertThat(child.dispatcher.service).isEqualTo(parent.dispatcher.service);
    Assert.assertThat(child.cache).isEqualTo(parent.cache);
    Assert.assertThat(child.listener).isEqualTo(parent.listener);
    Assert.assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers);
    Assert.assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size);
    child.requestHandlers.forEachIndexed(index -> {
      Assert.assertThat(child.requestHandlers.get(index))
      .isInstanceOf(parent.requestHandlers.get(index).getClass());
    });
    Assert.assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig);
    Assert.assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled);
    Assert.assertThat(child.isLoggingEnabled).isEqualTo(parent.isLoggingEnabled);
    Assert.assertThat(child.targetToAction).isEqualTo(parent.targetToAction);
    Assert.assertThat(child.targetToDeferredRequestCreator).isEqualTo(
      parent.targetToDeferredRequestCreator);
  }

  private void verifyActionComplete(@NonNull Action action) {
    Result result = action.completedResult;
    Assert.assertThat(result).isNotNull();
    Assert.assertThat(result).isInstanceOf(BitmapResult.class);
    BitmapResult bitmapResult = (BitmapResult) result;
    Assert.assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
    Assert.assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
  }

  private static final int NUM_BUILTIN_HANDLERS = 8;
  private static final int NUM_BUILTIN_TRANSFORMERS = 0;
}