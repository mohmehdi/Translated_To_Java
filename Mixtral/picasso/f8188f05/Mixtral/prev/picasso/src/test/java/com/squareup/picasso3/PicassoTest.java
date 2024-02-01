package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.test.rule.TemporaryFolder;
import android.support.test.runner.AndroidJUnit4;
import android.widget.ImageView;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.Builder;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.TestUtils;
import com.squareup.picasso3.TestUtils.FakeAction;
import com.squareup.picasso3.TestUtils.NOOP_REQUEST_HANDLER;
import com.squareup.picasso3.TestUtils.NOOP_TRANSFORMER;
import com.squareup.picasso3.Utils;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

@RunWith(AndroidJUnit4.class)
public class PicassoTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock
  public Context context;

  @Mock
  public Dispatcher dispatcher;

  @Mock
  public RequestHandler requestHandler;

  @Mock
  public Listener listener;

  private PlatformLruCache cache;
  private TestUtils.EventRecorder eventRecorder;
  private Bitmap bitmap;

  private Picasso picasso;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cache = new PlatformLruCache(2048);
    eventRecorder = new TestUtils.EventRecorder();
    bitmap = TestUtils.makeBitmap();
    picasso = new Picasso.Builder(context, dispatcher)
      .callFactory(TestUtils.UNUSED_CALL_FACTORY)
      .closeableCache(null)
      .cache(cache)
      .listener(listener)
      .requestTransformers(Collections.emptyList())
      .extraRequestHandlers(Collections.singletonList(requestHandler))
      .eventListeners(Collections.singletonList(eventRecorder))
      .defaultBitmapConfig(Config.ARGB_8888)
      .indicatorsEnabled(false)
      .isLoggingEnabled(false)
      .build();
  }

  @Test
  public void submitWithTargetInvokesDispatcher() {
    FakeAction action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    Assert.assertEquals(0, picasso.targetToAction.size());
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Mockito.verify(dispatcher).dispatchSubmit(action);
  }

  @Test
  public void submitWithSameActionDoesNotCancel() {
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    picasso.enqueueAndSubmit(action);
    Mockito.verify(dispatcher).dispatchSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Assert.assertTrue(picasso.targetToAction.containsValue(action));
    picasso.enqueueAndSubmit(action);
    Assert.assertFalse(action.cancelled);
    Mockito.verify(dispatcher, Mockito.never()).dispatchCancel(action);
  }

  @Test
  public void quickMemoryCheckReturnsBitmapIfInCache() {
    cache.put(URI_KEY_1, bitmap);
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    Assert.assertEquals(bitmap, cached);
    Assert.assertTrue(eventRecorder.cacheHits > 0);
  }

  @Test
  public void quickMemoryCheckReturnsNullIfNotInCache() {
    Bitmap cached = picasso.quickMemoryCacheCheck(URI_KEY_1);
    Assert.assertNull(cached);
    Assert.assertTrue(eventRecorder.cacheMisses > 0);
  }

  @Test
  public void completeInvokesSuccessOnAllSuccessfulRequests() {
    FakeAction action1 = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    RequestHandler.Result result = new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action1);
    FakeAction action2 = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);
    TestUtils.verifyActionComplete(action1);
    Assert.assertNull(action2.completedResult);
  }

  @Test
  public void completeInvokesErrorOnAllFailedRequests() {
    FakeAction action1 = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    Exception exception = Mockito.mock(Exception.class);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action1, exception);
    FakeAction action2 = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    hunter.attach(action2);
    action2.cancelled = true;
    hunter.run();
    picasso.complete(hunter);
    Assert.assertThat(action1.errorException).hasCauseThat().isEqualTo(exception);
    Assert.assertNull(action2.errorException);
    Mockito.verify(listener).onImageLoadFailed(picasso, URI_1, action1.errorException);
  }

  @Test
  public void completeInvokesErrorOnFailedResourceRequests() {
    FakeAction action = TestUtils.mock(
      picasso,
      URI_KEY_1,
      null,
      R.drawable.ic_launcher,
      new TestUtils.MockImageViewTarget()
    );
    Exception exception = Mockito.mock(Exception.class);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action, exception);
    hunter.run();
    picasso.complete(hunter);
    Assert.assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    Mockito.verify(listener).onImageLoadFailed(picasso, null, action.errorException);
  }

  @Test
  public void completeDeliversToSingle() {
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    RequestHandler.Result result = new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action);
    hunter.run();
    picasso.complete(hunter);
    TestUtils.verifyActionComplete(action);
  }

  @Test
  public void completeWithReplayDoesNotRemove() {
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    action.willReplay = true;
    RequestHandler.Result result = new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action);
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    picasso.complete(hunter);
    Assert.assertEquals(1, picasso.targetToAction.size());
    TestUtils.verifyActionComplete(action);
  }

  @Test
  public void completeDeliversToSingleAndMultiple() {
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    FakeAction action2 = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    RequestHandler.Result result = new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);
    TestUtils.verifyActionComplete(action);
    TestUtils.verifyActionComplete(action2);
  }

  @Test
  public void completeSkipsIfNoActions() {
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, new TestUtils.MockImageViewTarget());
    RequestHandler.Result result = new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
    TestUtils.MockHunter hunter = new TestUtils.MockHunter(picasso, result, action);
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
        Assert.fail("Test execution should not call this method");
      }

      @Override
      public void error(Exception e) {
        Assert.fail("Test execution should not call this method");
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
        Assert.assertThat(result).isInstanceOf(Bitmap.class);
        Bitmap bitmapResult = (Bitmap) result;
        Assert.assertThat(bitmapResult.getBitmap()).isEqualTo(bitmap);
        Assert.assertThat(bitmapResult.getLoadedFrom()).isEqualTo(LoadedFrom.MEMORY);
      }

      @Override
      public void error(Exception e) {
        Assert.fail("Reading from memory cache should not throw an exception");
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
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, target);
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Assert.assertFalse(action.cancelled);
    Mockito.verifyNoInteractions(dispatcher);
  }

  @Test
  public void cancelExistingRequestWithImageViewTarget() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Assert.assertEquals(0, picasso.targetToAction.size());
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithDeferredImageViewTarget() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    RequestCreator creator = TestUtils.mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = TestUtils.mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Assert.assertEquals(0, picasso.targetToDeferredRequestCreator.size());
  }

  @Test
  public void enqueueingDeferredRequestCancelsThePreviousOne() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    RequestCreator creator = TestUtils.mockRequestCreator(picasso);
    DeferredRequestCreator firstRequestCreator = TestUtils.mockDeferredRequestCreator(creator, target);
    picasso.defer(target, firstRequestCreator);
    Assert.assertEquals(1, picasso.targetToDeferredRequestCreator.size());
    DeferredRequestCreator secondRequestCreator = TestUtils.mockDeferredRequestCreator(creator, target);
    picasso.defer(target, secondRequestCreator);
    Mockito.verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    Assert.assertEquals(1, picasso.targetToDeferredRequestCreator.size());
  }

  @Test
  public void cancelExistingRequestWithBitmapTarget() {
    BitmapTarget target = new TestUtils.MockBitmapTarget();
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Assert.assertEquals(0, picasso.targetToAction.size());
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithDrawableTarget() {
    DrawableTarget target = new TestUtils.MockDrawableTarget();
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(target);
    Assert.assertEquals(0, picasso.targetToAction.size());
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelExistingRequestWithRemoteViewTarget() {
    int layoutId = 0;
    int viewId = 1;
    RemoteViews remoteViews = new RemoteViews("com.squareup.picasso3.test", layoutId);
    RemoteViewsTarget target = new RemoteViewsTarget(remoteViews, viewId);
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, target);
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Assert.assertFalse(action.cancelled);
    picasso.cancelRequest(remoteViews, viewId);
    Assert.assertEquals(0, picasso.targetToAction.size());
    Assert.assertTrue(action.cancelled);
    Mockito.verify(dispatcher).dispatchCancel(action);
  }

  @Test
  public void cancelTagAllActions() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    FakeAction action = TestUtils.mock(picasso, URI_KEY_1, URI_1, target, "TAG");
    picasso.enqueueAndSubmit(action);
    Assert.assertEquals(1, picasso.targetToAction.size());
    Assert.assertFalse(action.cancelled);
    picasso.cancelTag("TAG");
    Assert.assertEquals(0, picasso.targetToAction.size());
    Assert.assertTrue(action.cancelled);
  }

  @Test
  public void cancelTagAllDeferredRequests() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    RequestCreator creator = TestUtils.mockRequestCreator(picasso).tag("TAG");
    DeferredRequestCreator deferredRequestCreator = TestUtils.mockDeferredRequestCreator(creator, target);
    picasso.defer(target, deferredRequestCreator);
    picasso.cancelTag("TAG");
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Assert.assertEquals(0, picasso.targetToDeferredRequestCreator.size());
  }

  @Test
  public void deferAddsToMap() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    RequestCreator creator = TestUtils.mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = TestUtils.mockDeferredRequestCreator(creator, target);
    Assert.assertEquals(0, picasso.targetToDeferredRequestCreator.size());
    picasso.defer(target, deferredRequestCreator);
    Assert.assertEquals(1, picasso.targetToDeferredRequestCreator.size());
  }

  @Test
  public void shutdown() {
    cache.put("key", BitmapFactory.decodeResource(RuntimeEnvironment.application.getResources(), R.drawable.ic_launcher));
    Assert.assertEquals(1, cache.size());
    picasso.shutdown();
    Assert.assertEquals(0, cache.size());
    Assert.assertTrue(eventRecorder.closed);
    Mockito.verify(dispatcher).shutdown();
    Assert.assertTrue(picasso.shutdown);
  }

  @Test
  public void shutdownClosesUnsharedCache() throws IOException {
    okhttp3.Cache cache = new okhttp3.Cache(temporaryFolder.newFolder(), 100);
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application)
      .indicatorsEnabled(true)
      .build();
    picasso.setCache(cache);
    picasso.shutdown();
    Assert.assertTrue(cache.isClosed());
  }

  @Test
  public void shutdownTwice() {
    cache.put("key", BitmapFactory.decodeResource(RuntimeEnvironment.application.getResources(), R.drawable.ic_launcher));
    Assert.assertEquals(1, cache.size());
    picasso.shutdown();
    picasso.shutdown();
    Assert.assertEquals(0, cache.size());
    Assert.assertTrue(eventRecorder.closed);
    Mockito.verify(dispatcher).shutdown();
    Assert.assertTrue(picasso.shutdown);
  }

  @Test
  public void shutdownClearsTargetsToActions() {
    picasso.targetToAction.put(new TestUtils.MockImageViewTarget(), new FakeAction());
    picasso.shutdown();
    Assert.assertEquals(0, picasso.targetToAction.size());
  }

  @Test
  public void shutdownClearsDeferredRequests() {
    ImageViewTarget target = new TestUtils.MockImageViewTarget();
    RequestCreator creator = TestUtils.mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator = TestUtils.mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.shutdown();
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Assert.assertEquals(0, picasso.targetToDeferredRequestCreator.size());
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
      new Picasso.Builder(RuntimeEnvironment.application).withCacheSize(-1);
      fail();
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("maxByteCount < 0: -1", expected.getMessage());
    }
  }

  @Test
  public void builderWithoutRequestHandler() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).build();
    Assert.assertFalse(picasso.requestHandlers.contains(requestHandler));
  }

  @Test
  public void builderWithRequestHandler() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).addRequestHandler(requestHandler).build();
    Assert.assertTrue(picasso.requestHandlers.contains(requestHandler));
  }

  @Test
  public void builderWithDebugIndicators() {
    Picasso picasso = new Picasso.Builder(RuntimeEnvironment.application).indicatorsEnabled(true).build();
    Assert.assertTrue(picasso.indicatorsEnabled);
  }

  @Test
  public void evictAll() {
    picasso.cache.put("key", BitmapFactory.decodeResource(RuntimeEnvironment.application.getResources(), R.drawable.ic_launcher));
    Assert.assertEquals(1, picasso.cache.size());
    picasso.evictAll();
    Assert.assertEquals(0, picasso.cache.size());
  }

  @Test
  public void invalidateString() {
    Request request = new Request.Builder(Uri.parse("https://example.com")).build();
    picasso.cache.put(request.key, BitmapFactory.decodeResource(RuntimeEnvironment.application.getResources(), R.drawable.ic_launcher));
    Assert.assertEquals(1, picasso.cache.size());
    picasso.invalidate("https://example.com");
    Assert.assertEquals(0, picasso.cache.size());
  }

  @Test
  public void invalidateFile() {
    Request request = new Request.Builder(Uri.fromFile(new File("/foo/bar/baz"))).build();
    picasso.cache.put(request.key, BitmapFactory.decodeResource(RuntimeEnvironment.application.getResources(), R.drawable.ic_launcher));
    Assert.assertEquals(1, picasso.cache.size());
    picasso.invalidate(new File("/foo/bar/baz"));
    Assert.assertEquals(0, picasso.cache.size());
  }

  @Test
  public void invalidateUri() {
    Request request = new Request.Builder(URI_1).build();
    picasso.cache.put(request.key, BitmapFactory.decodeResource(RuntimeEnvironment.application.getResources(), R.drawable.ic_launcher));
    Assert.assertEquals(1, picasso.cache.size());
    picasso.invalidate(URI_1);
    Assert.assertEquals(0, picasso.cache.size());
  }

  @Test
  public void clonedRequestHandlersAreIndependent() {
    Picasso original = defaultPicasso(RuntimeEnvironment.application, false, false);
    Map < Class < ? extends RequestHandler > , RequestHandler > originalHandlers = new HashMap < > (original.requestHandlers);
    Map < Class < ? extends RequestTransformer > , RequestTransformer > originalTransformers = new HashMap < > (original.requestTransformers);
    Picasso cloned = original.newBuilder().build();
    Assert.assertEquals(originalHandlers, cloned.requestHandlers);
    Assert.assertEquals(originalTransformers, cloned.requestTransformers);
  }

  @Test
  public void cloneSharesStatefulInstances() {
    Picasso parent = defaultPicasso(RuntimeEnvironment.application, true, true);
    Picasso child = parent.newBuilder().build();
    Assert.assertEquals(parent.context, child.context);
    Assert.assertEquals(parent.callFactory, child.callFactory);
    Assert.assertEquals(parent.dispatcher.service, child.dispatcher.service);
    Assert.assertEquals(parent.cache, child.cache);
    Assert.assertEquals(parent.listener, child.listener);
    Assert.assertEquals(parent.requestTransformers, child.requestTransformers);
    Assert.assertEquals(parent.requestHandlers, child.requestHandlers);
    Assert.assertEquals(parent.defaultBitmapConfig, child.defaultBitmapConfig);
    Assert.assertEquals(parent.indicatorsEnabled, child.indicatorsEnabled);
    Assert.assertEquals(parent.isLoggingEnabled, child.isLoggingEnabled);
    Assert.assertEquals(parent.targetToAction, child.targetToAction);
    Assert.assertEquals(parent.targetToDeferredRequestCreator, child.targetToDeferredRequestCreator);
  }

private void verifyActionComplete(FakeAction action) {
    Object result = action.getCompletedResult();
    assertNotNull(result);
    assertTrue(result instanceof RequestHandler.Result.Bitmap);
    RequestHandler.Result.Bitmap bitmapResult = (RequestHandler.Result.Bitmap) result;
    assertEquals(bitmap, bitmapResult.getBitmap());
    assertEquals(NETWORK, bitmapResult.getLoadedFrom());
}

public static final int NUM_BUILTIN_HANDLERS = 8;
public static final int NUM_BUILTIN_TRANSFORMERS = 0;
}