package com.squareup.picasso3;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver.Companion;
import com.squareup.picasso3.MemoryPolicy.NO_STORE;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK;
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
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import kotlinx.coroutines.Dispatchers;
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
public class HandlerDispatcherTest extends DispatcherTest {
  @Override
  public Dispatcher createDispatcher(Context context, ExecutorService service, PlatformLruCache cache) {
    return new HandlerDispatcher(context, service, new Handler(getMainLooper()), cache);
  }
}


@RunWith(RobolectricTestRunner.class)
public class CoroutineDispatcherTest extends DispatcherTest {
  @Override
  protected InternalCoroutineDispatcher createDispatcher(
      Context context,
      ExecutorService service,
      PlatformLruCache cache) {
    return new InternalCoroutineDispatcher(
        context,
        service,
        new Handler(Looper.getMainLooper()),
        cache,
        Dispatchers.Main
    );
  }
}

@Config(manifest = Config.NONE)
public class DispatcherTest {

  @Mock lateinit var context;
  @Mock lateinit var connectivityManager;
  @Mock lateinit var serviceMock;
  private ExecutorService executorService;
  private Picasso picasso;
  private Dispatcher dispatcher;
  private Future<?> future;
  private Hunter hunter;
  private Action action;
  private RequestData data;
  private RequestHandler.Result result;
  private Bitmap bitmap1;

  @Before
  public void setUp() {
    ShadowLog.stream = System.out;
    initMocks(this);
    Mockito.when(context.getApplicationContext()).thenReturn(context);
    executorService = spy(new PicassoExecutorService());
    doReturn(future).when(executorService).submit(any(Runnable.class));
    picasso = mockPicasso(context);
    dispatcher = createDispatcher(context, executorService, new PlatformLruCache(2048));
  }

  @Test
  public void shutdownStopsService() {
    ExecutorService service = new PicassoExecutorService();
    dispatcher = createDispatcher(context, service, new PlatformLruCache(2048));
    dispatcher.shutdown();
    assertTrue(service.isShutdown());
  }

  @Test
  public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    ShadowLooper.idleMainLooper();
    verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test
  public void performSubmitWithNewRequestQueuesHunter() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap, hasSize(1));
    assertEquals(1, dispatcher.service.submissions);
  }

  @Test
  public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap, hasSize(2));
    assertEquals(2, dispatcher.service.submissions);
  }

  @Test
  public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action1);
    assertThat(dispatcher.hunterMap, hasSize(1));
    assertEquals(1, dispatcher.service.submissions);
    dispatcher.performSubmit(action2);
    assertThat(dispatcher.hunterMap, hasSize(1));
    assertEquals(1, dispatcher.service.submissions);
  }

  @Test
  public void performSubmitWithShutdownServiceIgnoresRequest() {
    dispatcher.service.shutdown();
    Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap, is(empty()));
    assertEquals(0, dispatcher.service.submissions);
  }

  @Test
  public void performSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions, is(empty()));

    FetchAction fetchAction1 = new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null);
    FetchAction fetchAction2 = new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    assertThat(dispatcher.pausedActions, hasSize(2));
  }

  @Test
  public void performCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    assertThat(dispatcher.pausedActions, is(empty()));
    Callback callback = mock(Callback.class);

    FetchAction fetchAction1 = new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    assertThat(dispatcher.pausedActions, is(empty()));
  }

  @Test
  public void performCancelDetachesRequestAndCleansUp() {
    Target target = mock(Target.class);
    action = mockAction(picasso, URI_KEY_1, URI_1, target);
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    assertNull(hunter.action);
    assertThat(dispatcher.hunterMap, is(empty()));
    assertThat(dispatcher.failedActions, is(empty()));
  }

  @Test
  public void performCancelMultipleRequestsDetachesOnly() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performCancel(action1);
    assertNull(hunter.action);
    assertThat(hunter.actions, containsExactly(action2));
    assertThat(dispatcher.hunterMap, hasSize(1));
  }

  @Test
  public void performCancelUnqueuesAndDetachesPausedRequest() {
    action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.pausedActions.put(action.getTarget(), action);
    dispatcher.performCancel(action);
    assertNull(hunter.action);
    assertThat(dispatcher.pausedTags, containsExactly("tag"));
    assertThat(dispatcher.pausedActions, is(empty()));
  }

  @Test
  public void performCompleteSetsResultInCache() {
    data = Request.Builder(URI_1).build();
    action = noopAction(data);
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    assertEquals(0, cache.size());

    dispatcher.performComplete(hunter);

    assertThat(hunter.result, instanceOf(RequestHandler.Result.Bitmap.class));
    result = hunter.result;
    assertEquals(bitmap1, ((RequestHandler.Result.Bitmap) result).bitmap);
    assertEquals(NETWORK, ((RequestHandler.Result.Bitmap) result).loadedFrom);
    assertSame(bitmap1, cache.get(hunter.key));
  }

  @Test
  public void performCompleteWithNoStoreMemoryPolicy() {
    data = Request.Builder(URI_1).memoryPolicy(NO_STORE).build();
    action = noopAction(data);
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    assertEquals(0, cache.size());

    dispatcher.performComplete(hunter);

    assertTrue(dispatcher.hunterMap.isEmpty());
    assertEquals(0, cache.size());
  }

  @Test
  public void performCompleteCleansUpAndPostsToMain() {
    data = Request.Builder(URI_1).build();
    boolean completed = false;
    action = noopAction(data, () -> completed = true);
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();

    dispatcher.performComplete(hunter);
    ShadowLooper.idleMainLooper();

    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(completed);
  }

  @Test
  public void performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    data = Request.Builder(URI_1).build();
    boolean completed = false;
    action = noopAction(data, () -> completed = true);
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    hunter.future = new FutureTask<>(mock(Runnable.class), null);
    hunter.future.cancel(false);

    dispatcher.performComplete(hunter);
    ShadowLooper.idleMainLooper();

    assertTrue(dispatcher.hunterMap.isEmpty());
    assertFalse(completed);
  }

  @Test
  public void performErrorCleansUpAndPostsToMain() {
    Exception exception = new RuntimeException();
    action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, exception);
    dispatcher.hunterMap.put(hunter.key, hunter);
    hunter.run();

    dispatcher.performError(hunter);
    ShadowLooper.idleMainLooper();

    assertTrue(dispatcher.hunterMap.isEmpty());
    assertSame(exception, action.errorException);
  }

  @Test
  public void performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    Exception exception = new RuntimeException();
    action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, exception);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.hunterMap.put(hunter.key, hunter);
    hunter.run();

    dispatcher.performError(hunter);
    ShadowLooper.idleMainLooper();

    assertTrue(dispatcher.hunterMap.isEmpty());
    assertNull(action.errorException);
    assertNull(action.completedResult);
  }

  @Test
  public void performRetrySkipsIfHunterIsCancelled() {
    action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.performRetry(hunter);
    assertTrue(hunter.isCancelled());
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(dispatcher.failedActions.isEmpty());
  }

  @Test
  public void performRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    action = mockAction(picasso, URI_KEY_2, URI_2);
    Exception e = new ContentLengthException("304 error");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, true);
    hunter.run();
    dispatcher.performRetry(hunter);
    assertFalse(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy));
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    hunter = mockHunter(
        picasso,
        new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        mockAction(picasso, URI_KEY_1, URI_1)
    );
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(dispatcher.failedActions.isEmpty());
    assertEquals(0, dispatcher.service.submissions);
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    hunter = mockHunter(
        picasso,
        new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        mockAction(picasso, URI_KEY_1, URI_1),
        null,
        false,
        true
    );
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(dispatcher.failedActions.isEmpty());
    assertEquals(0, dispatcher.service.submissions);
  }

  @Test
  public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    hunter = mockHunter(
        picasso,
        new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        action,
        null,
        false,
        true
    );
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertThat(dispatcher.failedActions, hasSize(1));
    assertEquals(0, dispatcher.service.submissions);
  }

  @Test
  public void performRetryRetriesIfNoNetworkScanning() {
    hunter = mockHunter(
        picasso,
        new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        mockAction(picasso, URI_KEY_1, URI_1),
        null,
        true
    );
    Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(dispatcher.failedActions.isEmpty());
    assertEquals(1, dispatcher.service.submissions);
  }

  @Test
  public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    hunter = mockHunter(
        picasso,
        new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        action,
        null,
        false,
        true
    );
    dispatcher.performRetry(hunter);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertThat(dispatcher.failedActions, hasSize(1));
    assertEquals(0, dispatcher.service.submissions);
  }

  @Test
  public void performRetryRetriesIfShouldRetry() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    hunter = mockHunter(
        picasso,
        new RequestHandler.Result.Bitmap(bitmap1, MEMORY),
        action,
        null,
        true
    );
    dispatcher.performRetry(hunter);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(dispatcher.failedActions.isEmpty());
    assertEquals(1, dispatcher.service.submissions);
  }

  @Test
  public void performRetrySkipIfServiceShutdown() {
    action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.service.shutdown();
    dispatcher.performRetry(hunter);
    assertEquals(0, dispatcher.service.submissions);
    assertTrue(dispatcher.hunterMap.isEmpty());
    assertTrue(dispatcher.failedActions.isEmpty());
  }

  @Test
  public void performAirplaneModeChange() {
    assertFalse(dispatcher.airplaneMode);
    dispatcher.performAirplaneModeChange(true);
    assertTrue(dispatcher.airplaneMode);
    dispatcher.performAirplaneModeChange(false);
    assertFalse(dispatcher.airplaneMode);
  }

  @Test
  public void performNetworkStateChangeWithNullInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    assertTrue(dispatcher.failedActions.isEmpty());
  }

  @Test
  public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo();
    when(info.isConnectedOrConnecting).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    assertTrue(dispatcher.failedActions.isEmpty());
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    assertTrue(dispatcher.failedActions.isEmpty());
  }

  @Test
  public void performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedTags, containsExactly("tag"));
    dispatcher.performResumeTag("tag");
    assertThat(dispatcher.pausedTags, is(empty()));
  }

  @Test
  public void performPauseTagIsIdempotent() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    assertThat(dispatcher.pausedActions, is(empty()));
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions, containsEntry(action.getTarget(), action));
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions, containsEntry(action.getTarget(), action));
  }

  @Test
  public void performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "tag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap, is(empty()));
    assertThat(dispatcher.pausedActions, hasSize(1));
    assertThat(dispatcher.pausedActions.containsValue(action), is(true));
    assertEquals(0, dispatcher.service.submissions);
  }

  @Test
  public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "anothertag");
    dispatcher.performSubmit(action);
    assertThat(dispatcher.hunterMap, hasSize(1));
    assertThat(dispatcher.pausedActions, is(empty()));
    assertEquals(1, dispatcher.service.submissions);
  }

  @Test
  public void performPauseDetachesRequestAndCancelsHunter() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "tag");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.hunterMap, is(empty()));
    assertThat(dispatcher.pausedActions, hasSize(1));
    assertThat(dispatcher.pausedActions.containsValue(action), is(true));
    assertNull(hunter.action);
  }

  @Test
  public void performPauseOnlyDetachesPausedRequest() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag1");
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag2");
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag1");
    assertThat(dispatcher.hunterMap, hasSize(1));
    assertThat(dispatcher.hunterMap.containsValue(hunter), is(true));
    assertThat(dispatcher.pausedActions, hasSize(1));
    assertThat(dispatcher.pausedActions.containsValue(action1), is(true));
    assertNull(hunter.action);
    assertThat(hunter.actions, containsExactly(action2));
  }

  @Test
  public void performResumeTagResumesPausedActions() {
    Action action = noopAction(Builder.create().tag("tag").build());
    hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    assertThat(dispatcher.pausedActions, is(empty()));
    dispatcher.performPauseTag("tag");
    assertThat(dispatcher.pausedActions, containsEntry(action.getTarget(), action));

    dispatcher.performResumeTag("tag");

    assertThat(dispatcher.pausedActions, is(empty()));
  }

  @Test
  public void performNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = mockNetworkInfo(true);
    Action failedAction1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action failedAction2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.failedActions.put(URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    assertEquals(2, dispatcher.service.submissions);
    assertTrue(dispatcher.failedActions.isEmpty());
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
    NetworkInfo networkInfo = mockNetworkInfo();
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    when(context.getSystemService(CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(CONNECTIVITY_ACTION));
    verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test
  public void nullExtrasOnReceiveAirplaneDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(ACTION_AIRPLANE_MODE_CHANGED));
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
    Intent intent = new Intent(ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra(EXTRA_AIRPLANE_STATE, airplaneOn);
    receiver.onReceive(context, intent);
    verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
  }

  private Dispatcher createDispatcher(Context context, ExecutorService service, PlatformLruCache cache) {
    return createDispatcher(context, service, cache, true);
  }

  private Dispatcher createDispatcher(
      Context context, ExecutorService service, PlatformLruCache cache, boolean scansNetworkChanges) {
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(
        scansNetworkChanges ? mock(NetworkInfo.class) : null
    );
    when(context.getSystemService(CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    when(context.checkCallingOrSelfPermission(anyString())).thenReturn(
        scansNetworkChanges ? PERMISSION_GRANTED : PERMISSION_DENIED
    );
    return createDispatcher(context, service, cache);
  }

  protected abstract Dispatcher createDispatcher(Context context, ExecutorService service, PlatformLruCache cache);

  private Action noopAction(Request data, Runnable onComplete) {
    return new Action(picasso, data) {
      @Override
      public void complete(RequestHandler.Result result) {
        onComplete.run();
      }

      @Override
      public void error(Exception e) {
      }

      @Override
      public Object getTarget() {
        return this;
      }
    };
  }
}