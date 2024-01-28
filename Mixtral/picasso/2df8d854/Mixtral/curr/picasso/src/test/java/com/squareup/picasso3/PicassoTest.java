package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule. temporaryFolder;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.Builder;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.TestUtils;
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
import com.squareup.picasso3.TestUtils.mockRequestCreator;
import com.squareup.picasso3.TestUtils.mockResponseHandler;
import com.squareup.picasso3.Utils;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PicassoTest {

  @Rule public temporaryFolder temporaryFolder = new temporaryFolder();

  @Mock private Context context;
  @Mock private Dispatcher dispatcher;
  @Mock private RequestHandler requestHandler;
  @Mock private Listener listener;

  private PlatformLruCache cache;
  private EventRecorder eventRecorder;
  private Bitmap bitmap;
  private Picasso picasso;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    cache = new PlatformLruCache(2048);
    eventRecorder = new EventRecorder();
    bitmap = makeBitmap();

    picasso = new Picasso.Builder(ApplicationProvider.getApplicationContext(), UNUSED_CALL_FACTORY)
        .dispatcher(dispatcher)
        .listener(listener)
        .requestTransformer(NO_TRANSFORMERS)
        .downloader(new OkHttpDownloader(new OkHttpClient()))
        .indicatorsEnabled(false)
        .loggingEnabled(false)
        .build();
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
    FakeAction action1 = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1);
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
    Exception exception = Mockito.mock(Exception.class);
    Hunter hunter =
        mockHunter(
            picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1, exception);
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
    FakeAction action =
        mockAction(
            picasso,
            URI_KEY_1,
            null,
            Utils.createDefaultBitmapConfig(Bitmap.Config.ARGB_8888),
            mockImageViewTarget());
    Exception exception = Mockito.mock(Exception.class);
    Hunter hunter = mockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action, exception);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    Mockito.verify(listener).onImageLoadFailed(picasso, null, action.errorException);
  }

  @Test
  public void completeDeliversToSingle() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
  }

  @Test
  public void completeWithReplayDoesNotRemove() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = mockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
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
    Hunter hunter = mockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
    verifyActionComplete(action2);
  }

  @Test
  public void completeSkipsIfNoActions() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = mockHunter(picasso, new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).isNull();
  }

  @Test
  public void resumeActionTriggersSubmitOnPausedAction() {
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    Action action =
        new Action(mockPicasso(ApplicationProvider.getApplicationContext()), request) {
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
    String uriKey = "URI_KEY_1";
    cache.put(uriKey, bitmap);
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    Action action = new Action(mockPicasso(ApplicationProvider.getApplicationContext()), request) {
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
    Target target = mockImageViewTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
    Truth.assertThat(action.cancelled).isFalse();
    picasso.cancelRequest(target);
    Truth.assertThat(action.cancelled).isFalse();
    Mockito.verifyZeroInteractions(dispatcher);
  }

  @Test
  public void cancelExistingRequestWithImageViewTarget() {
    Target target = mockImageViewTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
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
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator =
        mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelRequest(target);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
  }

  @Test
  public void enqueueingDeferredRequestCancelsThePreviousOne() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator firstRequestCreator =
        mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, firstRequestCreator);
    RequestCreator secondRequestCreator = mockRequestCreator(picasso);
    DeferredRequestCreator secondDeferredRequestCreator =
        mockDeferredRequestCreator(secondRequestCreator, target);
    picasso.defer(target, secondDeferredRequestCreator);
    Mockito.verify(target).removeOnAttachStateChangeListener(firstRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).containsKey(target);
  }

  @Test
  public void cancelExistingRequestWithBitmapTarget() {
    Target target = mockBitmapTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
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
    Target target = mockDrawableTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
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
    RemoteViews remoteViews = new RemoteViews("com.squareup.picasso3.test", layoutId);
    Target target = new RemoteViewsTarget(remoteViews, viewId);
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target);
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
    Target target = mockImageViewTarget();
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, target, tag);
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
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso).tag(tag);
    DeferredRequestCreator deferredRequestCreator =
        mockDeferredRequestCreator(creator, target);
    picasso.targetToDeferredRequestCreator.put(target, deferredRequestCreator);
    picasso.cancelTag(tag);
    Mockito.verify(target).removeOnAttachStateChangeListener(deferredRequestCreator);
  }

  @Test
  public void deferAddsToMap() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator =
        mockDeferredRequestCreator(creator, target);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).isEmpty();
    picasso.defer(target, deferredRequestCreator);
    Truth.assertThat(picasso.targetToDeferredRequestCreator).hasSize(1);
  }

  @Test
  public void shutdown() {
    cache.put("key", makeBitmap(1, 1));
    Truth.assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    Truth.assertThat(cache.size()).isEqualTo(0);
    Truth.assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher).shutdown();
    Truth.assertThat(picasso.shutdown).isTrue();
  }

  @Test
  public void shutdownClosesUnsharedCache() {
    Cache cache = new Cache(temporaryFolder.newFolder(), 100);
    Picasso picasso =
        new Picasso.Builder(ApplicationProvider.getApplicationContext(), UNUSED_CALL_FACTORY)
            .dispatcher(dispatcher)
            .listener(listener)
            .requestTransformer(NO_TRANSFORMERS)
            .downloader(new OkHttpDownloader(new OkHttpClient()))
            .indicatorsEnabled(false)
            .loggingEnabled(false)
            .cache(cache)
            .build();
    picasso.shutdown();
    Truth.assertThat(cache.isClosed()).isTrue();
  }

  @Test
  public void shutdownTwice() {
    cache.put("key", makeBitmap(1, 1));
    Truth.assertThat(cache.size()).isEqualTo(1);
    picasso.shutdown();
    picasso.shutdown();
    Truth.assertThat(cache.size()).isEqualTo(0);
    Truth.assertThat(eventRecorder.closed).isTrue();
    Mockito.verify(dispatcher, Mockito.times(2)).shutdown();
    Truth.assertThat(picasso.shutdown).isTrue();
  }

  @Test
  public void shutdownClearsDeferredRequests() {
    Target target = mockImageViewTarget();
    RequestCreator creator = mockRequestCreator(picasso);
    DeferredRequestCreator deferredRequestCreator =
        mockDeferredRequestCreator(creator, target);
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
      new Builder(ApplicationProvider.getApplicationContext()).withCacheSize(-1);
      fail();
    } catch (IllegalArgumentException expected) {
      Truth.assertThat(expected)
          .hasMessageThat()
          .isEqualTo("maxByteCount < 0: -1");
    }
  }

  @Test
  public void builderWithoutRequestHandler() {
    Picasso picasso = new Builder(ApplicationProvider.getApplicationContext()).build();
    Truth.assertThat(picasso.requestHandlers).isNotEmpty();
    Truth.assertThat(picasso.requestHandlers).doesNotContain(requestHandler);
  }

  @Test
  public void builderWithRequestHandler() {
    Picasso picasso =
        new Builder(ApplicationProvider.getApplicationContext())
            .addRequestHandler(requestHandler)
            .build();
    Truth.assertThat(picasso.requestHandlers).isNotNull();
    Truth.assertThat(picasso.requestHandlers).isNotEmpty();
    Truth.assertThat(picasso.requestHandlers).contains(requestHandler);
  }

  @Test
  public void builderWithDebugIndicators() {
    Picasso picasso = new Builder(ApplicationProvider.getApplicationContext()).indicatorsEnabled(true).build();
    Truth.assertThat(picasso.indicatorsEnabled).isTrue();
  }

  @Test
  public void evictAll() {
    Picasso picasso = new Builder(ApplicationProvider.getApplicationContext()).indicatorsEnabled(true).build();
    picasso.cache.put("key", makeBitmap(1, 1));
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.evictAll();
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateString() {
    Request request = Request.Builder(Uri.parse("https://example.com")).build();
    picasso.cache.put(request.key, makeBitmap(1, 1));
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate("https://example.com");
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateFile() {
    Request request = Request.Builder(Uri.fromFile(new File("/foo/bar/baz"))).build();
    picasso.cache.put(request.key, makeBitmap(1, 1));
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate(new File("/foo/bar/baz"));
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void invalidateUri() {
    Request request = Request.Builder(URI_1).build();
    picasso.cache.put(request.key, makeBitmap(1, 1));
    Truth.assertThat(picasso.cache.size()).isEqualTo(1);
    picasso.invalidate(URI_1);
    Truth.assertThat(picasso.cache.size()).isEqualTo(0);
  }

  @Test
  public void clonedRequestHandlersAreIndependent() {
    Picasso original = defaultPicasso(ApplicationProvider.getApplicationContext(), false, false);

    Builder builder = original.newBuilder();
    builder.addRequestTransformer(TestUtils.NOOP_TRANSFORMER);
    builder.addRequestHandler(TestUtils.NOOP_REQUEST_HANDLER);
    Picasso copy = builder.build();

    Truth.assertThat(copy.requestTransformers).hasSize(NUM_BUILTIN_TRANSFORMERS + 1);
    Truth.assertThat(copy.requestHandlers).hasSize(NUM_BUILTIN_HANDLERS + 1);
  }

  @Test
  public void cloneSharesStatefulInstances() {
    Picasso parent = defaultPicasso(ApplicationProvider.getApplicationContext(), true, true);

    Picasso child = parent.newBuilder().build();

    Truth.assertThat(child.context).isEqualTo(parent.context);
    Truth.assertThat(child.callFactory).isEqualTo(parent.callFactory);
    Truth.assertThat(child.dispatcher.service).isEqualTo(parent.dispatcher.service);
    Truth.assertThat(child.cache).isEqualTo(parent.cache);
    Truth.assertThat(child.listener).isEqualTo(parent.listener);
    Truth.assertThat(child.requestTransformers).isEqualTo(parent.requestTransformers);

    Truth.assertThat(child.requestHandlers).hasSize(parent.requestHandlers.size);
    child.requestHandlers.forEachIndexed(
        (index, it) -> Truth.assertThat(it).isInstanceOf(parent.requestHandlers.get(index).getClass()));

    Truth.assertThat(child.defaultBitmapConfig).isEqualTo(parent.defaultBitmapConfig);
    Truth.assertThat(child.indicatorsEnabled).isEqualTo(parent.indicatorsEnabled);
    Truth.assertThat(child.isLoggingEnabled).isEqualTo(parent.isLoggingEnabled);

    Truth.assertThat(child.targetToAction).isEqualTo(parent.targetToAction);
    Truth.assertThat(child.targetToDeferredRequestCreator).isEqualTo(
        parent.targetToDeferredRequestCreator);
  }

  private void verifyActionComplete(FakeAction action) {
    Result result = action.completedResult;
    Truth.assertThat(result).isNotNull();
    Truth.assertThat(result).isInstanceOf(Result.Bitmap.class);
    Result.Bitmap bitmapResult = (Result.Bitmap) result;
    Truth.assertThat(bitmapResult.bitmap).isEqualTo(bitmap);
    Truth.assertThat(bitmapResult.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
  }
}