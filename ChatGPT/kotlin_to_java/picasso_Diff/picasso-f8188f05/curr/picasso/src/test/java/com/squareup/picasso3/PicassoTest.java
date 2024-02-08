package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.widget.RemoteViews;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Picasso.Listener;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;
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
import com.squareup.picasso3.TestUtils.mockBitmapTarget;
import com.squareup.picasso3.TestUtils.mockDeferredRequestCreator;
import com.squareup.picasso3.TestUtils.mockDrawableTarget;
import com.squareup.picasso3.TestUtils.mockHunter;
import com.squareup.picasso3.TestUtils.mockImageViewTarget;
import com.squareup.picasso3.TestUtils.mockPicasso;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class PicassoTest {

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock
  internal Context context;

  @Mock
  internal Dispatcher dispatcher;

  @Mock
  internal RequestHandler requestHandler;

  @Mock
  internal Listener listener;

  private final PlatformLruCache cache = new PlatformLruCache(2048);
  private final EventRecorder eventRecorder = new EventRecorder();
  private final Bitmap bitmap = makeBitmap();

  private Picasso picasso;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    picasso = new Picasso(
      context,
      dispatcher,
      UNUSED_CALL_FACTORY,
      null,
      cache,
      listener,
      NO_TRANSFORMERS,
      NO_HANDLERS,
      ImmutableList.of(eventRecorder),
      Config.ARGB_8888,
      false,
      false
    );
  }

  @Test
  public void submitWithTargetInvokesDispatcher() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Truth.assertThat(picasso.targetToAction).isEmpty();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Mockito.verify(dispatcher).dispatchSubmit(action);
  }

  @Test
  public void submitWithSameActionDoesNotCancel() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    picasso.enqueueAndSubmit(action);
    Mockito.verify(dispatcher).dispatchSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    Truth.assertThat(picasso.targetToAction.containsValue(action)).isTrue();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(action.cancelled).isFalse();
    Mockito.verify(dispatcher, Mockito.never()).dispatchCancel(action);
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
    FakeAction action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action1);
    FakeAction action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;

    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action1);
    Truth.assertThat(action2.completedResult).isNull();
  }

  @Test
  public void completeInvokesErrorOnAllFailedRequests() {
    FakeAction action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Exception exception = mock(Exception.class);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action1, exception);
    FakeAction action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
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
    FakeAction action = mockAction(
      picasso,
      URI_KEY_1,
      null,
      123,
      mockImageViewTarget()
    );
    Exception exception = mock(Exception.class);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action, exception);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    Mockito.verify(listener).onImageLoadFailed(picasso, null, action.errorException);
  }

  @Test
  public void completeDeliversToSingle() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
  }

  @Test
  public void completeWithReplayDoesNotRemove() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.run();
    picasso.enqueueAndSubmit(action);
    Truth.assertThat(picasso.targetToAction).hasSize(1);
    picasso.complete(hunter);
    Truth.assertThat(picasso.targetToAction).hasSize(1);

    verifyActionComplete(action);
  }

  @Test
  public void completeDeliversToSingleAndMultiple() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    FakeAction action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
    verifyActionComplete(action2);
  }

  @Test
  public void completeSkipsIfNoActions() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).isNull();
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
            assertThat(result).isInstanceOf(Bitmap.class);
            Bitmap bitmapResult = (Bitmap) result;
            assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
            assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.MEMORY);
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
    RemoteViewsTarget target = mockImageViewTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(action.cancelled).isFalse();
    Mockito.verifyNoInteractions(dispatcher);
}

@Test
public void cancelExistingRequestWithImageViewTarget() {
    RemoteViewsTarget target = mockImageViewTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelExistingRequestWithDeferredImageViewTarget() {
    RemoteViewsTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
}

@Test
public void enqueueingDeferredRequestCancelsThePreviousOne() {
    RemoteViewsTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator firstRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);

    DeferredRequestCreator secondRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, secondRequestCreator);
    Mockito.verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);
}

@Test
public void cancelExistingRequestWithBitmapTarget() {
    BitmapTarget target = mockBitmapTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelExistingRequestWithDrawableTarget() {
    DrawableTarget target = mockDrawableTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelExistingRequestWithRemoteViewTarget() {
    int layoutId = 0;
    int viewId = 1;
    RemoteViews remoteViews = new RemoteViews("com.squareup.picasso3.test", layoutId);
    RemoteViewsTarget target = new RemoteViewsTarget(remoteViews, viewId);
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(remoteViews, viewId);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    Mockito.verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelTagAllActions() {
    ImageViewTarget target = mockImageViewTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target, "TAG");
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelTag("TAG");
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
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
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
    picasso.defer(target, deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
}

@Test
public void shutdown() {
    cache.put("key", makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
}

@Test
public void shutdownClosesUnsharedCache() {
    okhttp3.Cache cache = new okhttp3.Cache(temporaryFolder.getRoot(), 100);
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application)
        .withDispatcher(dispatcher)
        .withCache(cache)
        .withListener(listener)
        .build();
    picasso.shutdown();
    assertThat(cache.isClosed()).isTrue();
}

@Test
public void shutdownTwice() {
    cache.put("key", makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    picasso.shutdown();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
}

@Test
public void shutdownClearsTargetsToActions() {
    picasso.targetToAction.put(mockImageViewTarget(), mock(ImageViewAction.class));
    picasso.shutdown();
    assertThat(picasso.targetToAction).isEmpty();
}

@Test
public void shutdownClearsDeferredRequests() {
    ImageViewTarget target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.shutdown();
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
}

@Test
public void loadThrowsWithInvalidInput() {
    try {
        picasso.load("");
        fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        picasso.load("      ");
        fail("Empty URL should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
    try {
        picasso.load(0);
        fail("Zero resourceId should throw exception.");
    } catch (IllegalArgumentException ignored) {
    }
}

@Test
public void builderInvalidCache() {
    try {
        new Picasso.Builder(RuntimeEnvironment.application).withCacheSize(-1);
        fail();
    } catch (IllegalArgumentException e) {
        assertThat(e).hasMessageThat().isEqualTo("maxByteCount < 0: -1");
    }
}

@Test
public void builderWithoutRequestHandler() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
    assertThat(picasso.requestHandlers).isNotEmpty();
    assertThat(picasso.requestHandlers).doesNotContain(requestHandler);
}

@Test
public void builderWithRequestHandler() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application)
        .addRequestHandler(requestHandler)
        .build();
    assertThat(picasso.requestHandlers).isNotNull();
    assertThat(picasso.requestHandlers).isNotEmpty();
    assertThat(picasso.requestHandlers).contains(requestHandler);
}

@Test
public void builderWithDebugIndicators() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    assertThat(picasso.indicatorsEnabled).isTrue();
}

@Test
public void evictAll() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    picasso.cache.put("key", Bitmap.createBitmap(1, 1, ALPHA_8));
    assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.evictAll();
    assertThat(picasso.cache.size()).isEqualTo(0);
}

@Test
public void invalidateString() {
    Request request = new Request.Builder(Uri.parse("https://example.com")).build();
    cache.put(request.key, makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.invalidate("https://example.com");
    assertThat(cache.size()).isEqualTo(0);
}

@Test
public void invalidateFile() {
    Request request = new Request.Builder(Uri.fromFile(new File("/foo/bar/baz"))).build();
    cache.put(request.key, makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.invalidate(new File("/foo/bar/baz"));
    assertThat(cache.size()).isEqualTo(0);
}

@Test
public void invalidateUri() {
    Request request = new Request.Builder(URI_1).build();
    cache.put(request.key, makeBitmap(1, 1));
    assertThat(cache.size()).isEqualTo(1);
    picasso.invalidate(URI_1);
    assertThat(cache.size()).isEqualTo(0);
}

@Test
public void clonedRequestHandlersAreIndependent() {
    Picasso original = defaultPicasso(RuntimeEnvironment.application, false, false);

    original.newBuilder()
        .addRequestTransformer(TestUtils.NOOP_TRANSFORMER)
        .addRequestHandler(TestUtils.NOOP_REQUEST_HANDLER)
        .build();

    assertThat(original.requestTransformers).hasSize(NUM_BUILTIN_TRANSFORMERS);
    assertThat(original.requestHandlers).hasSize(NUM_BUILTIN_HANDLERS);
}

@Test
public void cloneSharesStatefulInstances() {
    Picasso parent = defaultPicasso(RuntimeEnvironment.application, true, true);

    Picasso child = parent.newBuilder().build();

    assertThat(child.context).isEqualTo(parent.context);
    assertThat(child.callFactory).isEqualTo(parent.callFactory);
    assertThat(((HandlerDispatcher) child.dispatcher).service)
            .isEqualTo(((HandlerDispatcher) parent.dispatcher).service);
    assertThat(child.cache).isEqualTo(parent.cache);
    assertThat(child.listener).isEqualTo(parent.listener);
    assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers);

    assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size());
    for (int index = 0; index < child.requestHandlers.size(); index++) {
        assertThat(child.requestHandlers.get(index))
                .isInstanceOf(parent.requestHandlers.get(index).getClass());
    }

    assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig);
    assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled);
    assertThat(child.isLoggingEnabled).isEqualTo(parent.isLoggingEnabled);

    assertThat(child.targetToAction).isEqualTo(parent.targetToAction);
    assertThat(child.targetToDeferredRequestCreator)
            .isEqualTo(parent.targetToDeferredRequestCreator);
}

@Test
public void cloneSharesCoroutineDispatchers() {
    Picasso parent =
        defaultPicasso(RuntimeEnvironment.application, true, true)
            .newBuilder()
            .dispatchers()
            .build();
    Picasso child = parent.newBuilder().build();

    InternalCoroutineDispatcher parentDispatcher = (InternalCoroutineDispatcher) parent.dispatcher;
    InternalCoroutineDispatcher childDispatcher = (InternalCoroutineDispatcher) child.dispatcher;
    assertThat(childDispatcher.mainDispatcher).isEqualTo(parentDispatcher.mainDispatcher);
    assertThat(childDispatcher.backgroundDispatcher).isEqualTo(parentDispatcher.backgroundDispatcher);
}

private void verifyActionComplete(FakeAction action) {
    Result result = action.completedResult;
    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(RequestHandler.Result.Bitmap.class);
    RequestHandler.Result.Bitmap bitmapResult = (RequestHandler.Result.Bitmap) result;
    assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
    assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
}

    private static final int NUM_BUILTIN_HANDLERS = 8;
    private static final int NUM_BUILTIN_TRANSFORMERS = 0;
}