package com.squareup.picasso3;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.ConnectivityAction;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver.Companion;
import com.squareup.picasso3.MemoryPolicy.NO_STORE;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.TestUtils.TestDelegatingService;
import com.squareup.picasso3.TestUtils.URI_1;
import com.squareup.picasso3.TestUtils.URI_2;
import com.squareup.picasso3.TestUtils.URI_KEY_1;
import com.squareup.picasso3.TestUtils.URI_KEY_2;
import com.squareup.picasso3.TestUtils.makeBitmap;
import com.squareup.picasso3.TestUtils.mockAction;
import com.squareup.picasso3.TestUtils.mockBitmapTarget;
import com.squareup.picasso3.TestUtils.mockCallback;
import com.squareup.picasso3.TestUtils.mockHunter;
import com.squareup.picasso3.TestUtils.mockNetworkInfo;
import com.squareup.picasso3.TestUtils.mockPicasso;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLooper;

@Config(sdk = {Build.VERSION_CODES.P})
public class DispatcherTest {

  @Mock private Context context;
  @Mock private ConnectivityManager connectivityManager;
  @Mock private ExecutorService serviceMock;

  private Picasso picasso;
  private Dispatcher dispatcher;
  private PlatformLruCache cache;
  private TestDelegatingService service;
  private Bitmap bitmap1;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    ShadowApplication shadowApplication = Robolectric.shadowOf(ApplicationProvider.getApplicationContext());
    shadowApplication.grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE);

    cache = new PlatformLruCache(2048);
    bitmap1 = makeBitmap();
    service = new TestDelegatingService(new PicassoExecutorService());

    Mockito.when(context.getApplicationContext()).thenReturn(context);
    picasso = mockPicasso(context);
    dispatcher = createDispatcher(service);

    ShadowConnectivityManager shadowConnectivityManager =
        Shadows.shadowOf(context.getSystemService(Context.CONNECTIVITY_SERVICE));
    shadowConnectivityManager.setActiveNetworkInfo(mockNetworkInfo(true));
  }

  private Dispatcher createDispatcher(ExecutorService service) {
    return createDispatcher(service, true);
  }

  private Dispatcher createDispatcher(ExecutorService service, boolean scansNetworkChanges) {
    if (scansNetworkChanges) {
      Mockito.when(context.checkCallingOrSelfPermission(ArgumentMatchers.anyString()))
          .thenReturn(PackageManager.PERMISSION_GRANTED);
    } else {
      Mockito.when(context.checkCallingOrSelfPermission(ArgumentMatchers.anyString()))
          .thenReturn(PackageManager.PERMISSION_DENIED);
    }

    return new Dispatcher(context, service, new Handler(Looper.getMainLooper()), cache);
  }

  @Test
  public void shutdownStopsService() {
    ExecutorService service = Executors.newSingleThreadExecutor();
    Dispatcher dispatcher = createDispatcher(service);
    dispatcher.shutdown();
    Truth.assertThat(service.isShutdown()).isTrue();
  }

  @Test
  public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    ShadowLooper.idleMainLooper();
    Mockito.verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test
  public void performSubmitWithNewRequestQueuesHunter() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(2);
    Truth.assertThat(service.submissions).isEqualTo(2);
  }

  @Test
  public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action1);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
    dispatcher.performSubmit(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performSubmitWithShutdownServiceIgnoresRequest() {
    service.shutdown();
    Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();

    FetchAction fetchAction1 =
        new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null);
    FetchAction fetchAction2 =
        new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    Truth.assertThat(dispatcher.pausedActions).hasSize(2);
  }

  @Test
  public void performCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    Callback callback = mockCallback();

    FetchAction fetchAction1 =
        new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void performCancelDetachesRequestAndCleansUp() {
    BitmapTarget target = mockBitmapTarget();
    Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Callable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performCancelMultipleRequestsDetachesOnly() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performCancel(action1);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).containsExactly(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test
  public void performCancelUnqueuesAndDetachesPausedRequest() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.pausedTags.add("tag");
    dispatcher.pausedActions.put(action.getTarget(), action);
    dispatcher.performCancel(action);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(dispatcher.pausedTags).containsExactly("tag");
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void performCompleteSetsResultInCache() {
    Request data = Request.Builder(URI_1).build();
    Action action = noopAction(data);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    Truth.assertThat(hunter.result).isInstanceOf(RequestHandler.Result.Bitmap.class);
    RequestHandler.Result.Bitmap result = (RequestHandler.Result.Bitmap) hunter.result;
    Truth.assertThat(result.bitmap).isEqualTo(bitmap1);
    Truth.assertThat(result.loadedFrom).isEqualTo(NETWORK);
    Truth.assertThat(cache.get(hunter.key)).isSameInstanceAs(bitmap1);
  }

  @Test
  public void performCompleteWithNoStoreMemoryPolicy() {
    Request data = Request.Builder(URI_1).memoryPolicy(NO_STORE).build();
    Action action = noopAction(data);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  public void performCompleteCleansUpAndPostsToMain() {
    Request data = Request.Builder(URI_1).build();
    Action action = noopAction(data);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test
  public void performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    Request data = Request.Builder(URI_1).build();
    Action action = noopAction(data);
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    hunter.future = new FutureTask<>(mock(Callable.class), null);
    hunter.future.cancel(false);

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test
  public void performErrorCleansUpAndPostsToMain() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(hunter.key, hunter);
    dispatcher.performError(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test
  public void performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Callable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.hunterMap.put(hunter.key, hunter);
    dispatcher.performError(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
  }

  @Test
  public void performRetrySkipsIfHunterIsCancelled() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Callable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(hunter.isCancelled).isTrue();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Action action = mockAction(picasso, URI_KEY_2, URI_2);
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter =
        mockHunter(
            picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, true, true);
    hunter.run();
    dispatcher.performRetry(hunter);
    Truth.assertThat(
        NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse();
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Hunter hunter =
        mockHunter(
            picasso,
            RequestHandler.Result.Bitmap(bitmap1, MEMORY),
            mockAction(picasso, URI_KEY_1, URI_1),
            null,
            false,
            true);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    Hunter hunter =
        mockHunter(
            picasso,
            RequestHandler.Result.Bitmap(bitmap1, MEMORY),
            mockAction(picasso, URI_KEY_1, URI_1),
            null,
            false,
            true);
    Dispatcher dispatcher = createDispatcher(serviceMock, false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter =
        mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, false, true);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryRetriesIfNoNetworkScanning() {
    Hunter hunter =
        mockHunter(
            picasso,
            RequestHandler.Result.Bitmap(bitmap1, MEMORY),
            mockAction(picasso, URI_KEY_1, URI_1),
            null,
            true);
    Dispatcher dispatcher = createDispatcher(serviceMock, false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter =
        mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, false, true);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryRetriesIfShouldRetry() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter =
        mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action, e, true);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performRetrySkipIfServiceShutdown() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    service.shutdown();
    dispatcher.performRetry(hunter);
    Truth.assertThat(service.submissions).isEqualTo(0);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performAirplaneModeChange() {
    Truth.assertThat(dispatcher.airplaneMode).isFalse();
    dispatcher.performAirplaneModeChange(true);
    Truth.assertThat(dispatcher.airplaneMode).isTrue();
    dispatcher.performAirplaneModeChange(false);
    Truth.assertThat(dispatcher.airplaneMode).isFalse();
  }

  @Test
  public void performNetworkStateChangeWithNullInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo();
    Mockito.when(info.isConnectedOrConnecting).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    Dispatcher dispatcher = createDispatcher(serviceMock);
    NetworkInfo info = mockNetworkInfo(true);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedTags).containsExactly("tag");
    dispatcher.performResumeTag("tag");
    Truth.assertThat(dispatcher.pausedTags).isEmpty();
  }

  @Test
  public void performPauseTagIsIdempotent() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
  }

  @Test
  public void performPauseTagQueuesNewRequestDoesNotSubmit() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "tag");
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "anothertag");
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performPauseDetachesRequestAndCancelsHunter() {
    Action action = mockAction(picasso, URI_KEY_1, URI_1, "tag");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action);
    hunter.future = new FutureTask<>(mock(Callable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(hunter.action).isNull();
  }

  @Test
  public void performPauseOnlyDetachesPausedRequest() {
    Action action1 = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag1");
    Action action2 = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag2");
    Hunter hunter = mockHunter(picasso, RequestHandler.Result.Bitmap(bitmap1, MEMORY), action1);
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

  @Test
  public void performResumeTagIsIdempotent() {
    dispatcher.performResumeTag("tag");
  }

  @Test
  public void performNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = mockNetworkInfo(true);
    Action failedAction1 = mockAction(picasso, URI_KEY_1, URI_1);
    Action failedAction2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.failedActions.put(URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(service.submissions).isEqualTo(2);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void nullIntentOnReceiveDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, null);
    Mockito.verifyZeroInteractions(dispatcher);
  }

  @Test
  public void nullExtrasOnReceiveConnectivityAreOk() {
    ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
    NetworkInfo networkInfo = mockNetworkInfo();
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE))
        .thenReturn(connectivityManager);
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
    Mockito.verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test
  public void nullExtrasOnReceiveAirplaneDoesNothing() {
    Dispatcher dispatcher = mock(Dispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(ACTION_AIRPLANE_MODE_CHANGED));
    Mockito.verifyZeroInteractions(dispatcher);
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
    Mockito.verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
  }

  private Action noopAction(Request data) {
    return new Action(picasso, data) {
      @Override
      public void complete(RequestHandler.Result result) {}

      @Override
      public void error(Exception e) {}

      @Override
      public Object getTarget() {
        throw new AssertionError();
      }
    };
  }
}
