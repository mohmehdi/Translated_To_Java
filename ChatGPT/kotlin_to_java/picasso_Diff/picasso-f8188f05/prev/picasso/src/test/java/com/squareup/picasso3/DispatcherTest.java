package com.squareup.picasso3;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Handler;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver.Companion.EXTRA_AIRPLANE_STATE;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

@RunWith(RobolectricTestRunner.class)
public class HandlerDispatcherTest extends DispatcherTest {

  @Override
  protected Dispatcher createDispatcher(Context context, ExecutorService service, PlatformLruCache cache) {
    return new HandlerDispatcher(context, service, new Handler(getMainLooper()), cache);
  }
}

@RunWith(RobolectricTestRunner.class)
public class CoroutineDispatcherTest extends DispatcherTest {

  @Override
  protected Dispatcher createDispatcher(Context context, ExecutorService service, PlatformLruCache cache) {
    return new InternalCoroutineDispatcher(context, service, new Handler(getMainLooper()), cache, Dispatchers.Main);
  }
}

abstract class DispatcherTest {

  @Mock
  Context context;

  @Mock
  ConnectivityManager connectivityManager;

  @Mock
  ExecutorService serviceMock;

  private Picasso picasso;
  private Dispatcher dispatcher;

  private final PicassoExecutorService executorService = spy(new PicassoExecutorService());
  private final PlatformLruCache cache = new PlatformLruCache(2048);
  private final TestDelegatingService service = new TestDelegatingService(executorService);
  private final TestUtils.TestBitmap bitmap1 = TestUtils.makeBitmap();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(context.getApplicationContext()).thenReturn(context);
    doReturn(mock(Future.class)).when(executorService).submit(any(Runnable.class));
    picasso = TestUtils.mockPicasso(context);
    dispatcher = createDispatcher(service);
  }

  @Test
  public void shutdownStopsService() {
    PicassoExecutorService service = new PicassoExecutorService();
    dispatcher = createDispatcher(service);
    dispatcher.shutdown();
    assertThat(service.isShutdown()).isEqualTo(true);
  }

  @Test
  public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    Shadows.shadowOf(getMainLooper()).idle();
    verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test
  public void performSubmitWithNewRequestQueuesHunter() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(2);
    assertThat(service.submissions).isEqualTo(2);
  }

  @Test
  public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    dispatcher.performSubmit(action1);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(service.submissions).isEqualTo(1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performSubmitWithShutdownServiceIgnoresRequest() {
    service.shutdown();
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions).isEmpty();

    FetchAction fetchAction1 = new FetchAction(picasso, new Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), null);
    FetchAction fetchAction2 = new FetchAction(picasso, new Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    assertThat(dispatcher.pausedActions).hasSize(2);
  }

  @Test
  public void performCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions).isEmpty();
    Callback callback = TestUtils.mockCallback();

    FetchAction fetchAction1 = new FetchAction(picasso, new Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void performCancelDetachesRequestAndCleansUp() {
    Target target = TestUtils.mockBitmapTarget();
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, target);
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    assertThat(hunter.action).isNull();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performCancelMultipleRequestsDetachesOnly() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performCancel(action1);
    assertThat(hunter.action).isNull();
    assertThat(hunter.actions).containsExactly(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test
  public void performCancelUnqueuesAndDetachesPausedRequest() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.pausedActions.put(action.getTarget(), action);
    dispatcher.performCancel(action);
    assertThat(hunter.action).isNull();
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void performCompleteSetsResultInCache() {
    Request data = new Request.Builder(TestUtils.URI_1).build();
    Action action = noopAction(data);
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    hunter.run();
    assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    assertThat(hunter.result).isInstanceOf(RequestHandler.Result.Bitmap.class);
    RequestHandler.Result.Bitmap result = (RequestHandler.Result.Bitmap) hunter.result;
    assertThat(result.bitmap).isEqualTo(bitmap1);
    assertThat(result.loadedFrom).isEqualTo(Picasso.LoadedFrom.NETWORK);
    assertThat(cache.get(hunter.key)).isSameInstanceAs(bitmap1);
  }

  @Test
  public void performCompleteWithNoStoreMemoryPolicy() {
    Request data = new Request.Builder(TestUtils.URI_1).memoryPolicy(MemoryPolicy.NO_STORE).build();
    Action action = noopAction(data);
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    hunter.run();
    assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  public void performCompleteCleansUpAndPostsToMain() {
    Request data = new Request.Builder(TestUtils.URI_1).build();
    boolean[] completed = {false};
    Action action = noopAction(data, () -> completed[0] = true);
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    hunter.run();

    dispatcher.performComplete(hunter);
    ShadowLooper.idleMainLooper();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(completed[0]).isTrue();
  }

  @Test
  public void performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    Request data = new Request.Builder(TestUtils.URI_1).build();
    boolean[] completed = {false};
    Action action = noopAction(data, () -> completed[0] = true);
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    hunter.run();
    hunter.future = new FutureTask<>(mock(Runnable.class), null);
    hunter.future.cancel(false);

    dispatcher.performComplete(hunter);
    ShadowLooper.idleMainLooper();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(completed[0]).isFalse();
  }

  @Test
  public void performErrorCleansUpAndPostsToMain() {
    RuntimeException exception = new RuntimeException();
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action, exception);
    dispatcher.hunterMap.put(hunter.key, hunter);
    hunter.run();

    dispatcher.performError(hunter);
    ShadowLooper.idleMainLooper();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(action.errorException).isSameInstanceAs(exception);
  }

  @Test
  public void performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    RuntimeException exception = new RuntimeException();
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action, exception);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.hunterMap.put(hunter.key, hunter);
    hunter.run();

    dispatcher.performError(hunter);
    ShadowLooper.idleMainLooper();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(action.errorException).isNull();
    assertThat(action.completedResult).isNull();
  }

  @Test
  public void performRetrySkipsIfHunterIsCancelled() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.performRetry(hunter);
    assertThat(hunter.isCancelled).isTrue();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2);
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action, e, true);
    hunter.run();
    dispatcher.performRetry(hunter);
    assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse();
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY),
      TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    );
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY),
      TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1),
      null,
      false,
      true
    );
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget());
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY),
      action,
      null,
      false,
      true
    );
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryRetriesIfNoNetworkScanning() {
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY),
      TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1),
      null,
      true
    );
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(1);
  }


  @Test
public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget());
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action, null, false, true);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    assertThat(service.submissions).isEqualTo(0);
}

@Test
public void performRetryRetriesIfShouldRetry() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget());
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action, null, true);
    dispatcher.performRetry(hunter);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(1);
}

@Test
public void performRetrySkipIfServiceShutdown() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget());
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    service.shutdown();
    dispatcher.performRetry(hunter);
    assertThat(service.submissions).isEqualTo(0);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
}

@Test
public void performAirplaneModeChange() {
    assertThat(dispatcher.airplaneMode).isFalse();
    dispatcher.performAirplaneModeChange(true);
    assertThat(dispatcher.airplaneMode).isTrue();
    dispatcher.performAirplaneModeChange(false);
    assertThat(dispatcher.airplaneMode).isFalse();
}

@Test
public void performNetworkStateChangeWithNullInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    assertThat(dispatcher.failedActions).isEmpty();
}

@Test
public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = TestUtils.mockNetworkInfo();
    when(info.isConnectedOrConnecting()).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    assertThat(dispatcher.failedActions).isEmpty();
}

@Test
public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = TestUtils.mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    assertThat(dispatcher.failedActions).isEmpty();
}

@Test
public void performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    dispatcher.performResumeTag("tag");
    assertThat(dispatcher.pausedTags).isEmpty();
}

@Test
public void performPauseTagIsIdempotent() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    assertThat(dispatcher.pausedActions).isEmpty();
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
}

@Test
public void performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag");
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, "tag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    assertThat(service.submissions).isEqualTo(0);
}

@Test
public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, "anothertag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.pausedActions).isEmpty();
    assertThat(service.submissions).isEqualTo(1);
}

@Test
public void performPauseDetachesRequestAndCancelsHunter() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, "tag");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action, dispatcher);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    assertThat(hunter.action).isNull();
}

@Test
public void performPauseOnlyDetachesPausedRequest() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag1");
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, TestUtils.mockBitmapTarget(), "tag2");
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag1");
    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue();
    assertThat(hunter.action).isNull();
    assertThat(hunter.actions).containsExactly(action2);
}

@Test
public void performResumeTagResumesPausedActions() {
    Action action = noopAction(new Request.Builder(TestUtils.URI_1).tag("tag").build());
    Hunter hunter = TestUtils.mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MemoryPolicy.MEMORY), action);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    assertThat(dispatcher.pausedActions).isEmpty();
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);

    dispatcher.performResumeTag("tag");

    assertThat(dispatcher.pausedActions).isEmpty();
}

@Test
public void performNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = TestUtils.mockNetworkInfo(true);
    Action failedAction1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action failedAction2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2);
    dispatcher.failedActions.put(TestUtils.URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(TestUtils.URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    assertThat(service.submissions).isEqualTo(2);
    assertThat(dispatcher.failedActions).isEmpty();
}

@Test
public void nullIntentOnReceiveDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, null);
    verifyNoInteractions(dispatcher);
}

@Test
public void nullExtrasOnReceiveConnectivityAreOk() {
    ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo();
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
    verify(dispatcher).dispatchNetworkStateChange(networkInfo);
}

@Test
public void nullExtrasOnReceiveAirplaneDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
    verifyNoInteractions(dispatcher);
}

@Test
public void correctExtrasOnReceiveAirplaneDispatches() {
    setAndVerifyAirplaneMode(false);
    setAndVerifyAirplaneMode(true);
}

private void setAndVerifyAirplaneMode(boolean airplaneOn) {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra(EXTRA_AIRPLANE_STATE, airplaneOn);
    receiver.onReceive(context, intent);
    verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
}

private Dispatcher createDispatcher(boolean scansNetworkChanges) {
    return createDispatcher(service, scansNetworkChanges);
}

private Dispatcher createDispatcher(ExecutorService service, boolean scansNetworkChanges) {
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(scansNetworkChanges ? mock(NetworkInfo.class) : null);
    when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    when(context.checkCallingOrSelfPermission(anyString())).thenReturn(scansNetworkChanges ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
    return createDispatcher(context, service, cache);
}

internal abstract Dispatcher createDispatcher(Context context, ExecutorService service, PlatformLruCache cache);

private Action noopAction(Request data, Runnable onComplete) {
    return new Action(picasso, data) {
        @Override
        public void complete(RequestHandler.Result result) {
            onComplete.run();
        }

        @Override
        public void error(Exception e) {
            // Do nothing
        }

        @Override
        public Object getTarget() {
            return this;
        }
    };
}
}