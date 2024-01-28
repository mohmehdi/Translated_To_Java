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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver;
import com.squareup.picasso3.Dispatcher.NetworkBroadcastReceiver.ExtraConstants;
import com.squareup.picasso3.MemoryPolicy.NoStore;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.LoadedFrom;
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
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@RunWith(AndroidJUnit4.class)
@Config(sdk = {Build.VERSION_CODES.P})
class DispatcherTest {
  @Mock lateinit var context;

  @Mock lateinit var connectivityManager;

  @Mock lateinit var serviceMock;

  private Picasso picasso;
  private Dispatcher dispatcher;

  private final ExecutorService executorService = Mockito.spy(new PicassoExecutorService());
  private final PlatformLruCache cache = new PlatformLruCache(2048);
  private final TestDelegatingService service = new TestDelegatingService(executorService);
  private final Bitmap bitmap1 = makeBitmap();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(context.getApplicationContext()).thenReturn(context);
    Mockito.doReturn(Mockito.mock(Future.class)).when(executorService).submit(any(Runnable.class));
    picasso = mockPicasso(context);
    dispatcher = createDispatcher(service);
  }

  @Test
  public void shutdownStopsService() {
    final PicassoExecutorService service = new PicassoExecutorService();
    dispatcher = createDispatcher(service);
    dispatcher.shutdown();
    Truth.assertThat(service.isShutdown()).isEqualTo(true);
  }

  @Test
  public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    Mockito.verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test
  public void performSubmitWithNewRequestQueuesHunter() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performSubmitWithTwoDifferentRequestsQueuesHunters() {
    final Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    final Action action2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.performSubmit(action1);
    dispatcher.performSubmit(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(2);
    Truth.assertThat(service.submissions).isEqualTo(2);
  }

  @Test
  public void performSubmitWithExistingRequestAttachesToHunter() {
    final Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    final Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
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
    final Action action = mockAction(picasso, URI_KEY_1, URI_1);
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performSubmitWithFetchAction() {
    final String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();

    final FetchAction fetchAction1 = new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null);
    final FetchAction fetchAction2 = new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), null);
    dispatcher.performSubmit(fetchAction1);
    dispatcher.performSubmit(fetchAction2);

    Truth.assertThat(dispatcher.pausedActions).hasSize(2);
  }

  @Test
  public void performCancelWithFetchActionWithCallback() {
    final String pausedTag = "pausedTag";
    dispatcher.pausedTags.add(pausedTag);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    final Callback callback = mockCallback();

    final FetchAction fetchAction1 = new FetchAction(picasso, Request.Builder(URI_1).tag(pausedTag).build(), callback);
    dispatcher.performCancel(fetchAction1);
    fetchAction1.cancel();
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void performCancelDetachesRequestAndCleansUp() {
    final Target target = mockBitmapTarget();
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, target);
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);
    dispatcher.performCancel(action);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performCancelMultipleRequestsDetachesOnly() {
    final Action action1 = mockAction(picasso, URI_KEY_1, URI_1);
    final Action action2 = mockAction(picasso, URI_KEY_1, URI_1);
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performCancel(action1);
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).containsExactly(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test
  public void performCancelUnqueuesAndDetachesPausedRequest() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
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
    final Request data = Request.Builder(URI_1).build();
    final Action action = noopAction(data);
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    Truth.assertThat(hunter.result).isInstanceOf(RequestHandler.Result.Bitmap.class);
    final RequestHandler.Result.Bitmap result = (RequestHandler.Result.Bitmap) hunter.result;
    Truth.assertThat(result.bitmap).isEqualTo(bitmap1);
    Truth.assertThat(result.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
    Truth.assertThat(cache.get(hunter.key)).isSameInstanceAs(bitmap1);
  }

  @Test
  public void performCompleteWithNoStoreMemoryPolicy() {
    final Request data = Request.Builder(URI_1).memoryPolicy(NoStore).build();
    final Action action = noopAction(data);
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  public void performCompleteCleansUpAndPostsToMain() {
    final Request data = Request.Builder(URI_1).build();
    final Action action = noopAction(data);
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    
  }

  @Test
  public void performCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    final Request data = Request.Builder(URI_1).build();
    final Action action = noopAction(data);
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.run();
    hunter.future = new FutureTask<>(mock(Runnable.class), null);
    hunter.future.cancel(false);

    dispatcher.performComplete(hunter);

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    
  }

  @Test
  public void performErrorCleansUpAndPostsToMain() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performError(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    
  }

  @Test
  public void performErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.hunterMap.put(URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.performError(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    
  }

  @Test
  public void performRetrySkipsIfHunterIsCancelled() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    hunter.future.cancel(false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(hunter.isCancelled()).isTrue();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performRetryForContentLengthResetsNetworkPolicy() {
    final NetworkInfo networkInfo = mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    final Action action = mockAction(picasso, URI_KEY_2, URI_2);
    final ContentLengthException e = new ContentLengthException("304 error");
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action, e, true);
    hunter.run();
    dispatcher.performRetry(hunter);
    Truth.assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse();
  }

  @Test
  public void performRetryDoesNotMarkForReplayIfNotSupported() {
    final NetworkInfo networkInfo = mockNetworkInfo(true);
    final Hunter hunter = mockHunter(
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

  @Test
  public void performRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    final Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1),
      null,
      false,
      true
    );
    final Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    final NetworkInfo networkInfo = mockNetworkInfo(true);
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    final Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action,
      null,
      false,
      true
    );
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryRetriesIfNoNetworkScanning() {
    final Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      mockAction(picasso, URI_KEY_1, URI_1),
      null,
      true
    );
    final Dispatcher dispatcher = createDispatcher(false);
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    final Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action,
      null,
      false,
      true
    );
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performRetryRetriesIfShouldRetry() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    final Hunter hunter = mockHunter(
      picasso,
      new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action,
      null,
      true
    );
    dispatcher.performRetry(hunter);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performRetrySkipIfServiceShutdown() {
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget());
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
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
    final Dispatcher dispatcher = createDispatcher(serviceMock);
    dispatcher.performNetworkStateChange(null);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performNetworkStateChangeWithDisconnectedInfoIgnores() {
    final Dispatcher dispatcher = createDispatcher(serviceMock);
    final NetworkInfo info = mockNetworkInfo();
    Mockito.when(info.isConnectedOrConnecting).thenReturn(false);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void performNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    final Dispatcher dispatcher = createDispatcher(serviceMock);
    final NetworkInfo info = mockNetworkInfo(true);
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
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, mockBitmapTarget(), "tag");
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action);
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
    final Action action = mockAction(picasso = picasso, key = URI_KEY_1, uri = URI_1, tag = "tag");
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(service.submissions).isEqualTo(0);
  }

  @Test
  public void performPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.performPauseTag("tag");
    final Action action = mockAction(picasso, URI_KEY_1, URI_1, "anothertag");
    dispatcher.performSubmit(action);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    Truth.assertThat(service.submissions).isEqualTo(1);
  }

  @Test
  public void performPauseDetachesRequestAndCancelsHunter() {
    final Action action = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      tag = "tag"
    );
    final Hunter hunter = mockHunter(
      picasso = picasso,
      result = new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY),
      action = action,
      dispatcher = dispatcher
    );
    hunter.future = new FutureTask<>(mock(Runnable.class), mock(Object.class));
    dispatcher.hunterMap.put(URI_KEY_1, hunter);
    dispatcher.performPauseTag("tag");
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(hunter.action).isNull();
  }

  @Test
  public void performPauseOnlyDetachesPausedRequest() {
    final Action action1 = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      target = mockBitmapTarget(),
      tag = "tag1"
    );
    final Action action2 = mockAction(
      picasso = picasso,
      key = URI_KEY_1,
      uri = URI_1,
      target = mockBitmapTarget(),
      tag = "tag2"
    );
    final Hunter hunter = mockHunter(picasso, new RequestHandler.Result.Bitmap(bitmap1, LoadedFrom.MEMORY), action1);
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
    final NetworkInfo info = mockNetworkInfo(true);
    final Action failedAction1 = mockAction(picasso, URI_KEY_1, URI_1);
    final Action failedAction2 = mockAction(picasso, URI_KEY_2, URI_2);
    dispatcher.failedActions.put(URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(URI_KEY_2, failedAction2);
    dispatcher.performNetworkStateChange(info);
    Truth.assertThat(service.submissions).isEqualTo(2);
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void nullIntentOnReceiveDoesNothing() {
    final Dispatcher dispatcher = Mockito.mock(Dispatcher.class);
    final NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, null);
    Mockito.verifyNoInteractions(dispatcher);
  }

  @Test
  public void nullExtrasOnReceiveConnectivityAreOk() {
    final ConnectivityManager connectivityManager = Mockito.mock(ConnectivityManager.class);
    final NetworkInfo networkInfo = mockNetworkInfo();
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    final Dispatcher dispatcher = Mockito.mock(Dispatcher.class);
    final NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
    Mockito.verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test
  public void nullExtrasOnReceiveAirplaneDoesNothing() {
    final Dispatcher dispatcher = Mockito.mock(Dispatcher.class);
    final NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    receiver.onReceive(context, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
    Mockito.verifyNoInteractions(dispatcher);
  }

  @Test
  public void correctExtrasOnReceiveAirplaneDispatches() {
    setAndVerifyAirplaneMode(false);
    setAndVerifyAirplaneMode(true);
  }

  private void setAndVerifyAirplaneMode(boolean airplaneOn) {
    final Dispatcher dispatcher = Mockito.mock(Dispatcher.class);
    final NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    final Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra(ExtraConstants.EXTRA_AIRPLANE_STATE, airplaneOn);
    receiver.onReceive(context, intent);
    Mockito.verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
  }

  private Dispatcher createDispatcher(ExecutorService service) {
    return createDispatcher(service, true);
  }

  private Dispatcher createDispatcher(
    ExecutorService service,
    boolean scansNetworkChanges
  ) {
    if (scansNetworkChanges) {
      final ConnectivityManager connectivityManager = Mockito.mock(ConnectivityManager.class);
      final NetworkInfo networkInfo = mockNetworkInfo();
      Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
      Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    }
    return new Dispatcher(context, service, new Handler(Looper.getMainLooper()), cache);
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