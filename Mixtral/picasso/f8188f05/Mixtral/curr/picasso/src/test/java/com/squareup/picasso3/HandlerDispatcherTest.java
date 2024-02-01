package com.squareup.picasso3;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.truth.Truth;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Request.Builder;
import com.squareup.picasso3.TestUtils.TestDelegatingService;
import com.squareup.picasso3.TestUtils.URI_1;
import com.squareup.picasso3.TestUtils.URI_2;
import com.squareup.picasso3.TestUtils.URI_KEY_1;
import com.squareup.picasso3.TestUtils.URI_KEY_2;
import com.squareup.picasso3.TestUtils.any;
import com.squareup.picasso3.TestUtils.makeBitmap;
import com.squareup.picasso3.TestUtils.mockAction;
import com.squareup.picasso3.TestUtils.mockBitmapTarget;
import com.squareup.picasso3.TestUtils.mockCallback;
import com.squareup.picasso3.TestUtils.mockHunter;
import com.squareup.picasso3.TestUtils.mockNetworkInfo;
import com.squareup.picasso3.TestUtils.mockPicasso;
import java.lang.Exception;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class HandlerDispatcherTest {
  @Mock lateinit Context context;
  @Mock lateinit ConnectivityManager connectivityManager;
  @Mock lateinit ExecutorService serviceMock;
  private Picasso picasso;
  private HandlerDispatcher dispatcher;
  private ExecutorService executorService = Mockito.spy(new PicassoExecutorService());
  private PlatformLruCache cache = new PlatformLruCache(2048);
  private TestDelegatingService service = new TestDelegatingService(executorService);
  private Bitmap bitmap1 = makeBitmap();

  @Before public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(context.getApplicationContext()).thenReturn(context);
    Mockito.doReturn(Mockito.mock(Future.class)).when(executorService).submit(Mockito.any(Runnable.class));
    picasso = mockPicasso(context);
    dispatcher = createDispatcher(service);
  }

  @Test public void shutdownStopsService() {
    ExecutorService service = new PicassoExecutorService();
    dispatcher = createDispatcher(service);
    dispatcher.shutdown();
    Truth.assertThat(service.isShutdown()).isEqualTo(true);
  }

  @Test public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    ShadowLooper.idleMainLooper();
    Mockito.verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test public void performSubmitWithNewRequestQueuesHunter() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(2);
    Truth.assertThat(service.submissions).isEqualTo(2);
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action1);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
    dispatcher.performSubmit(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performSubmitWithShutdownServiceIgnoresRequest() {
    service.shutdown();
    Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();

    FetchAction fetchAction1 = new FetchAction(picasso, new Builder(URI_1).tag(pausedTag).build(), null);
    FetchAction fetchAction2 = new FetchAction(picasso, new Builder(URI_1).tag(pausedTag).build(), null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    Truth.assertThat(dispatcher.pausedActions).hasSize(2);
  }

  @Test public void performCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    Callback callback = mockCallback();

    FetchAction fetchAction1 = new FetchAction(picasso, new Builder(URI_1).tag(pausedTag).build(), callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void performCancelDetachesRequestAndCleansUp() {
    Target target = mockBitmapTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.future = new FutureTask<Void>(Mockito.mock(Runnable.class), Mockito.mock(Void.class));
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performCancelMultipleRequestsDetachesOnly() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performCancel(action1);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).containsExactly(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test public void performCancelUnqueuesAndDetachesPausedRequest() {
    Target target = mockBitmapTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target, "tag");
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.pausedActions.put(target, action);
    dispatcher.performCancel(action);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(dispatcher.pausedTags).containsExactly("tag");
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void performCompleteSetsResultInCache() {
    Request data = new Builder(URI_1).build();
    Action action = noopAction(data);
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    Truth.assertThat(hunter.result).isInstanceOf(RequestHandler.Result.Bitmap.class);
    RequestHandler.Result.Bitmap result = (RequestHandler.Result.Bitmap) hunter.result;
    Truth.assertThat(result.bitmap).isEqualTo(bitmap1);
    Truth.assertThat(result.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
    Truth.assertThat(cache.get(hunter.key)).isSameInstanceAs(bitmap1);
  }

  @Test public void performCompleteWithNoStoreMemoryPolicy() {
    Request data = new Builder(URI_1).memoryPolicy(MemoryPolicy.NO_STORE).build();
    Action action = noopAction(data);
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(cache.size()).isEqualTo(0);
  }

  @Test public void performCompleteCleansUpAndPostsToMain() {
    Request data = new Builder(URI_1).build();
    boolean completed = false;
    Action action = noopAction(data, () -> completed = true);
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();

    dispatcher.performComplete(hunter);
    ShadowLooper.idleMainLooper();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(completed).isTrue();
  }

  @Test public void performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    Request data = new Builder(URI_1).build();
    boolean completed = false;
    Action action = noopAction(data, () -> completed = true);
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.future = new FutureTask<Void>(Mockito.mock(Runnable.class), Mockito.mock(Void.class));
    hunter.future.cancel(false);
    hunter.run();

    dispatcher.performComplete(hunter);
    ShadowLooper.idleMainLooper();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(completed).isFalse();
  }

  @Test public void performErrorCleansUpAndPostsToMain() {
    Exception exception = new RuntimeException();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action, exception);
    dispatcher.hunterMap.put(hunter.key, hunter);
    hunter.run();

    dispatcher.performError(hunter);
    ShadowLooper.idleMainLooper();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(action.errorException).isSameInstanceAs(exception);
  }

  @Test public void performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    Exception exception = new RuntimeException();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action, exception);
    hunter.future = new FutureTask<Void>(Mockito.mock(Runnable.class), Mockito.mock(Void.class));
    hunter.future.cancel(false);
    dispatcher.hunterMap.put(hunter.key, hunter);
    hunter.run();

    dispatcher.performError(hunter);
    ShadowLooper.idleMainLooper();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(action.errorException).isNull();
    Truth.assertThat(action.completedResult).isNull();
  }

  @Test public void performRetrySkipsIfHunterIsCancelled() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.future = new FutureTask<Void>(Mockito.mock(Runnable.class), Mockito.mock(Void.class));
    hunter.future.cancel(false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(hunter.isCancelled).isTrue();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Action action = mockAction(picasso, URI_KEY_2, URI_2);
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action, e, true);
    hunter.run();
    dispatcher.performRetry(hunter);
    Truth.assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse();
  }

  @Test public void performRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1)
    );
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1),
      null,
      false,
      true
    );
    ExecutorService executorService = Mockito.mock(ExecutorService.class);
    HandlerDispatcher dispatcher = createDispatcher(executorService, false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(executorService.getSubmittedTasks()).isEmpty();
  }

  @Test public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = null;
    Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action,
      e,
      false,
      true
    );
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryRetriesIfNoNetworkScanning() {
    Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1),
      null,
      true
    );
    ExecutorService executorService = Mockito.mock(ExecutorService.class);
    HandlerDispatcher dispatcher = createDispatcher(executorService, false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(executorService.getSubmittedTasks()).hasSize(1);
  }

  @Test public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = null;
    Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action,
      e,
      false,
      true
    );
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performRetryRetriesIfShouldRetry() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = null;
    Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action,
      e,
      true
    );
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performRetrySkipIfServiceShutdown() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = null;
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    service.shutdown();
    dispatcher.performRetry(hunter);
    Truth.assertThat(service.submissions).isEqualTo(0);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performAirplaneModeChange() {
    Truth.assertThat(dispatcher.airplaneMode).isFalse();
    dispatcher.performAirplaneModeChange(true);
    Truth.assertThat(dispatcher.airplaneMode).isTrue();
    dispatcher.performAirplaneModeChange(false);
    Truth.assertThat(dispatcher.airplaneMode).isFalse();
  }

  @Test public void performNetworkStateChangeWithNullInfoIgnores() {
    HandlerDispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    HandlerDispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo();
    Mockito.when(info.isConnectedOrConnecting).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    HandlerDispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedTags).containsExactly("tag");
    dispatcher.performResumeTag("tag");
    Truth.assertThat(dispatcher.pausedTags).isEmpty();
  }

  @Test public void performPauseTagIsIdempotent() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
  }

  @Test public void performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, tag = "tag");
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "anothertag");
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test public void performPauseDetachesRequestAndCancelsHunter() {
    Action action = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      tag = "tag"
    );
    Hunter hunter = mockHunter(
      picasso = picasso,
      result = new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action = action,
      dispatcher = dispatcher
    );
    hunter.future = new FutureTask<Void>(Mockito.mock(Runnable.class), Mockito.mock(Void.class));
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(hunter.action).isNull();
  }

  @Test public void performPauseOnlyDetachesPausedRequest() {
    Action action1 = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      target = mockBitmapTarget(),
      tag = "tag1"
    );
    Action action2 = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      target = mockBitmapTarget(),
      tag = "tag2"
    );
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag1");
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue();
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).containsExactly(action2);
  }

  @Test public void performResumeTagResumesPausedActions() {
    Action action = noopAction(Builder.create(URI_1).tag("tag").build());
    Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);

    dispatcher.performResumeTag("tag");

    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void performNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = mockNetworkInfo(true);
    Action failedAction1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action failedAction2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.failedActions.put(URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(service.submissions).isEqualTo(2);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

private HandlerDispatcher createDispatcher(boolean scansNetworkChanges) {
    return createDispatcher(service, scansNetworkChanges);
}
  private HandlerDispatcher createDispatcher(ExecutorService service, boolean scansNetworkChanges) {
    if (scansNetworkChanges) {
      Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(mockNetworkInfo(true));
    } else {
      Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(null);
    }
    Context context = ApplicationProvider.getApplicationContext();
    ContextWrapper contextWrapper = Mockito.spy(context);
    Mockito.when(contextWrapper.checkCallingOrSelfPermission(ArgumentMatchers.anyString()))
        .thenReturn(
            scansNetworkChanges
                ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED);
    return new HandlerDispatcher(contextWrapper, service, new Handler(Looper.getMainLooper()), cache);
  }

  private Action noopAction(Request data, Runnable onComplete) {
    return new Action(picasso, data) {
      @Override
      public void complete(RequestHandler.Result result) {
        onComplete.run();
      }

      @Override
      public void error(Exception e) {}

      @Override
      public Object getTarget() {
        return this;
      }
    };
  }
}
