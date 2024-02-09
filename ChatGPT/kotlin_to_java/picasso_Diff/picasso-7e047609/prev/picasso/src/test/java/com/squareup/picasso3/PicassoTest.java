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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class PicassoTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    internal Context context;
    @Mock
    internal Dispatcher dispatcher;
    @Mock
    internal RequestHandler requestHandler;
    @Mock
    internal Listener listener;

    private PlatformLruCache cache;
    private EventRecorder eventRecorder;
    private Bitmap bitmap;

    private Picasso picasso;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        cache = new PlatformLruCache(2048);
        eventRecorder = new EventRecorder();
        bitmap = TestUtils.makeBitmap();

        picasso = new Picasso.Builder(context)
                .dispatcher(dispatcher)
                .callFactory(UNUSED_CALL_FACTORY)
                .cache(cache)
                .listener(listener)
                .requestTransformers(NO_TRANSFORMERS)
                .extraRequestHandlers(NO_HANDLERS)
                .eventListeners(List.of(eventRecorder))
                .defaultBitmapConfig(Config.ARGB_8888)
                .indicatorsEnabled(false)
                .loggingEnabled(false)
                .build();
    }

    @Test
    public void submitWithTargetInvokesDispatcher() {
        FakeAction action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockImageViewTarget());
        Truth.assertThat(picasso.targetToAction).isEmpty();
        picasso.enqueueAndSubmit(action);
        Truth.assertThat(picasso.targetToAction).hasSize(1);
        Mockito.verify(dispatcher).dispatchSubmit(action);
    }

    @Test
    public void submitWithSameActionDoesNotCancel() {
        FakeAction action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockImageViewTarget());
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
        cache.put(TestUtils.URI_KEY_1, bitmap);
        Bitmap cached = picasso.quickMemoryCacheCheck(TestUtils.URI_KEY_1);
        Truth.assertThat(cached).isEqualTo(bitmap);
        Truth.assertThat(eventRecorder.cacheHits).isGreaterThan(0);
    }

    @Test
    public void quickMemoryCheckReturnsNullIfNotInCache() {
        Bitmap cached = picasso.quickMemoryCacheCheck(TestUtils.URI_KEY_1);
        Truth.assertThat(cached).isNull();
        Truth.assertThat(eventRecorder.cacheMisses).isGreaterThan(0);
    }

    @Test
    public void completeInvokesSuccessOnAllSuccessfulRequests() {
        FakeAction action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockImageViewTarget());
        Hunter hunter = TestUtils.mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1);
        FakeAction action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockImageViewTarget());
        hunter.attach(action2);
        action2.cancelled = true;

        hunter.run();
        picasso.complete(hunter);

        verifyActionComplete(action1);
        Truth.assertThat(action2.completedResult).isNull();
    }

@Test
public void completeInvokesErrorOnAllFailedRequests() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Exception exception = mock(Exception.class);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action1, exception);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);

    assertThat(action1.errorException).hasCauseThat().isEqualTo(exception);
    assertThat(action2.errorException).isNull();
    verify(listener).onImageLoadFailed(picasso, URI_1, action1.errorException);
}

@Test
public void completeInvokesErrorOnFailedResourceRequests() {
    Action action = mockAction(
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

    assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    verify(listener).onImageLoadFailed(picasso, null, action.errorException);
}

@Test
public void completeDeliversToSingle() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
}

@Test
public void completeWithReplayDoesNotRemove() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.run();
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    picasso.complete(hunter);
    assertThat(picasso.targetToAction).hasSize(1);

    verifyActionComplete(action);
}

@Test
public void completeDeliversToSingleAndMultiple() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
    verifyActionComplete(action2);
}

@Test
public void completeSkipsIfNoActions() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);

    assertThat(hunter.action).isNull();
    assertThat(hunter.actions).isNull();
}

@Test
public void resumeActionTriggersSubmitOnPausedAction() {
    Request request = new Request.Builder(URI_1, 0, ARGB_8888).build();
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
    verify(dispatcher).dispatchSubmit(action);
}

@Test
public void resumeActionImmediatelyCompletesCachedRequest() {
    cache.put(URI_KEY_1, bitmap);
    Request request = new Request.Builder(URI_1, 0, ARGB_8888).build();
    Action action = new Action(mockPicasso(RuntimeEnvironment.application), request) {
        @Override
        public void complete(Result result) {
            assertThat(result).isInstanceOf(Bitmap.class);
            Bitmap bitmapResult = (Bitmap) result;
            assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
            assertThat(bitmapResult.loadedFrom).isEqualTo(MEMORY);
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
    Target target = mockImageViewTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(action.cancelled).isFalse();
    verifyZeroInteractions(dispatcher);
}

@Test
public void cancelExistingRequestWithImageViewTarget() {
    Target target = mockImageViewTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelExistingRequestWithDeferredImageViewTarget() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
}

@Test
public void enqueueingDeferredRequestCancelsThePreviousOne() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator firstRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);

    DeferredRequestCreator secondRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, secondRequestCreator);
    verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);
}

@Test
public void cancelExistingRequestWithTarget() {
    Target target = mockTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelExistingRequestWithRemoteViewTarget() {
    int layoutId = 0;
    int viewId = 1;
    RemoteViews remoteViews = new RemoteViews("packageName", layoutId);
    RemoteViewsTarget target = new RemoteViewsTarget(remoteViews, viewId);
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(remoteViews, viewId);
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
    verify(dispatcher).dispatchCancel(action);
}

@Test
public void cancelTagAllActions() {
    Target target = mockImageViewTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target, "TAG");
    picasso.enqueueAndSubmit(action);
    assertThat(picasso.targetToAction).hasSize(1);
    assertThat(action.cancelled).isFalse();
    picasso.cancelTag("TAG");
    assertThat(picasso.targetToAction).isEmpty();
    assertThat(action.cancelled).isTrue();
}

@Test
public void cancelTagAllDeferredRequests() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso).tag("TAG");
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.defer(target, deferredRequestCreator);
    picasso.cancelTag("TAG");
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
}

@Test
public void deferAddsToMap() {
    Target target = mockImageViewTarget();
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
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
}

@Test
public void shutdownClosesUnsharedCache() {
    okhttp3.Cache cache = new okhttp3.Cache(temporaryFolder.getRoot(), 100);
    Picasso picasso = new Picasso(
            context, dispatcher, UNUSED_CALL_FACTORY, cache, this.cache, listener,
            NO_TRANSFORMERS, NO_HANDLERS, List.of(eventRecorder),
            ARGB_8888, false, false
    );
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
    verify(dispatcher).shutdown();
    assertThat(picasso.shutdown).isTrue();
}

@Test
public void shutdownClearsDeferredRequests() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.shutdown();
    verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
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
        new Picasso.Builder(RuntimeEnvironment.application).withCacheSize(-1);
        fail();
    } catch (IllegalArgumentException expected) {
        assertThat(expected).hasMessageThat().isEqualTo("maxByteCount < 0: -1");
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
    assertThat(child.dispatcher.service).isEqualTo(parent.dispatcher.service);
    assertThat(child.cache).isEqualTo(parent.cache);
    assertThat(child.listener).isEqualTo(parent.listener);
    assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers);

    assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size());
    for (int index = 0; index < child.requestHandlers.size(); index++) {
        assertThat(child.requestHandlers.get(index)).isInstanceOf(parent.requestHandlers.get(index).getClass());
    }

    assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig);
    assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled);
    assertThat(child.isLoggingEnabled).isEqualTo(parent.isLoggingEnabled);

    assertThat(child.targetToAction).isEqualTo(parent.targetToAction);
    assertThat(child.targetToDeferredRequestCreator).isEqualTo(
            parent.targetToDeferredRequestCreator
    );
}
    private void verifyActionComplete(FakeAction action) {
        Result result = action.completedResult;
        Truth.assertThat(result).isNotNull();
        Truth.assertThat(result).isInstanceOf(RequestHandler.Result.Bitmap.class);
        RequestHandler.Result.Bitmap bitmapResult = (RequestHandler.Result.Bitmap) result;
        Truth.assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
        Truth.assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
    }

    private static class PlatformLruCache extends LruCache {
        public PlatformLruCache(int maxSize) {
            super(maxSize);
        }
    }
}