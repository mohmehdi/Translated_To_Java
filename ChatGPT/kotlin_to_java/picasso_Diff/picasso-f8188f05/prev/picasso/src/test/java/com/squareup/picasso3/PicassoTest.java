package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
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
import com.squareup.picasso3.TestUtils.mockRequestCreator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class PicassoTest {

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock
  private Context context;

  @Mock
  private Dispatcher dispatcher;

  @Mock
  private RequestHandler requestHandler;

  @Mock
  private Listener listener;

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
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1);
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
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action1, exception);
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
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action, exception);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(action.errorException).hasCauseThat().isEqualTo(exception);
    Mockito.verify(listener).onImageLoadFailed(picasso, null, action.errorException);
  }

  @Test
  public void completeDeliversToSingle() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
  }

  @Test
  public void completeWithReplayDoesNotRemove() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    action.willReplay = true;
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
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
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.attach(action2);
    hunter.run();
    picasso.complete(hunter);

    verifyActionComplete(action);
    verifyActionComplete(action2);
  }

  @Test
  public void completeSkipsIfNoActions() {
    FakeAction action = mockAction(picasso, URI_KEY_1, URI_1, mockImageViewTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY), action);
    hunter.detach(action);
    hunter.run();
    picasso.complete(hunter);

    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).isNull();
  }

  @Test
  public void resumeActionTriggersSubmitOnPausedAction() {
    Request request = new Request.Builder(URI_1, 0, Config.ARGB_8888).build();
    FakeAction action = new FakeAction(mockPicasso(RuntimeEnvironment.application), request) {
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
    FakeAction action = new FakeAction(mockPicasso(RuntimeEnvironment.application), request) {
      @Override
      public void complete(Result result) {
        Truth.assertThat(result).isInstanceOf(Bitmap.class);
        Bitmap bitmapResult = (Bitmap) result;
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






    private static final int NUM_BUILTIN_HANDLERS = 8;
    private static final int NUM_BUILTIN_TRANSFORMERS = 0;
}