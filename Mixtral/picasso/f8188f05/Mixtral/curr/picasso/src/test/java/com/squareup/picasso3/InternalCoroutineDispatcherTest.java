 Here is the Java equivalent of the provided Kotlin code:

package com.squareup.picasso3;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import com.google.common.truth.Truth;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
import com.squareup.picasso3.Picasso.LoadedFrom.NETWORK;
import com.squareup.picasso3.Request.Builder;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import java.lang.Exception;
import java.lang.RuntimeException;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.test.StandardTestDispatcher;
import kotlinx.coroutines.test.TestDispatcher;

@RunWith(RobolectricTestRunner.class)
public class InternalCoroutineDispatcherTest {

  @Mock lateinit Context context;
  @Mock lateinit ConnectivityManager connectivityManager;
  private Picasso picasso;
  private InternalCoroutineDispatcher dispatcher;
  private TestDispatcher testDispatcher;

  private PlatformLruCache cache = new PlatformLruCache(2048);
  private Bitmap bitmap1 = TestUtils.makeBitmap();

  @Before public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(context.getApplicationContext()).thenReturn(context);
    dispatcher = createDispatcher();
  }

  @Test public void shutdownCancelsRunningJob() {
    createDispatcher(true);
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    dispatcher.dispatchSubmit(action);

    dispatcher.shutdown();
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.isShutdown()).isEqualTo(true);
    Truth.assertThat(action.completedResult).isNull();
  }

  @Test public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    Mockito.verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test public void dispatchSubmitWithNewRequestQueuesHunter() {
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    dispatcher.dispatchSubmit(action);

    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(action.completedResult).isNotNull();
  }

  @Test public void dispatchSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2);

    dispatcher.dispatchSubmit(action1);
    dispatcher.dispatchSubmit(action2);

    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(action1.completedResult).isNotNull();
    Truth.assertThat(action2.completedResult).isNotNull();
    Truth.assertThat(action2.completedResult).isNotEqualTo(action1.completedResult);
  }

  @Test public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);

    dispatcher.dispatchSubmit(action1);
    dispatcher.dispatchSubmit(action2);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(action1.completedResult).isNotNull();
    Truth.assertThat(action2.completedResult).isEqualTo(action1.completedResult);
  }

  @Test public void dispatchSubmitWithShutdownServiceIgnoresRequest() {
    dispatcher.shutdown();

    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    dispatcher.dispatchSubmit(action);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(action.completedResult).isNull();
  }

  @Test public void dispatchSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.dispatchPauseTag(pausedTag);
    testDispatcher.scheduler.runCurrent();
    Truth.assertThat(dispatcher.pausedActions).isEmpty();

    boolean completed = false;
    Action fetchAction1 = noopAction(Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), () -> {
      completed = true;
    });
    Action fetchAction2 = noopAction(Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), () -> {
      completed = true;
    });
    dispatcher.dispatchSubmit(fetchAction1);
    dispatcher.dispatchSubmit(fetchAction2);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedActions).hasSize(2);
    Truth.assertThat(completed).isFalse();
  }

  @Test public void dispatchCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.dispatchPauseTag(pausedTag);
    testDispatcher.scheduler.runCurrent();
    Truth.assertThat(dispatcher.pausedActions).isEmpty();

    Callback callback = TestUtils.mockCallback();

    Action fetchAction1 = new FetchAction(picasso, Request.Builder(TestUtils.URI_1).tag(pausedTag).build(), callback);
    dispatcher.dispatchSubmit(fetchAction1);
    testDispatcher.scheduler.runCurrent();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);

    dispatcher.dispatchCancel(fetchAction1);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test public void dispatchCancelDetachesRequestAndCleansUp() {
    Target target = TestUtils.mockBitmapTarget();
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, target);
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action).apply {
      job = new Job();
    };
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR, hunter);
    dispatcher.failedActions.put(target, action);

    dispatcher.dispatchCancel(action);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(hunter.job.isCancelled()).isTrue();
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void dispatchCancelMultipleRequestsDetachesOnly() {
    Action action1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Action action2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR, hunter);

    dispatcher.dispatchCancel(action1);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).containsExactly(action2);
    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test public void dispatchCancelUnqueuesAndDetachesPausedRequest() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag"
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    Bitmap result = (Bitmap) hunter.result;
    Truth.assertThat(result.bitmap).isEqualTo(bitmap1);
    Truth.assertThat(result.loadedFrom).isEqualTo(NETWORK);
    Truth.assertThat(cache.get(hunter.key)).isSameInstanceAs(bitmap1);
  }

  @Test public void dispatchCompleteWithNoStoreMemoryPolicy() {
    Request data = Request.Builder(TestUtils.URI_1).memoryPolicy(MemoryPolicy.NO_STORE).build();
    Action action = noopAction(data);
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    Truth.assertThat(cache.size()).isEqualTo(0);

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(cache.size()).isEqualTo(0);
  }

  @Test public void dispatchCompleteCleansUpAndPostsToMain() {
    Request data = Request.Builder(TestUtils.URI_1).build();
    boolean completed = false;
    Action action = noopAction(data, () -> {
      completed = true;
    });
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);
    hunter.run();

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(completed).isTrue();
  }

  @Test public void dispatchCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    Request data = Request.Builder(TestUtils.URI_1).build();
    boolean completed = false;
    Action action = noopAction(data, () -> {
      completed = true;
    });
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);
    hunter.run();
    hunter.job = new Job().apply { cancel(); }

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(completed).isFalse();
  }

  @Test public void dispatchErrorCleansUpAndPostsToMain() {
    Exception exception = new RuntimeException();
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag"
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action, exception);
    hunter.run();
    dispatcher.hunterMap.put(hunter.key, hunter);

    dispatcher.dispatchFailed(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(action.errorException).isEqualTo(exception);
  }

  @Test public void dispatchErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    Exception exception = new RuntimeException();
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag"
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action, exception);
    hunter.run();
    hunter.job = new Job().apply { cancel(); }
    dispatcher.hunterMap.put(hunter.key, hunter);

    dispatcher.dispatchFailed(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(action.errorException).isNull();
  }

  @Test public void dispatchRetrySkipsIfHunterIsCancelled() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag"
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);
    hunter.job = new Job().apply { cancel(); }

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(hunter.isCancelled).isTrue();
    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void dispatchRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2);
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action, e, true);
    hunter.run();

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.advanceUntilIdle();

    Truth.assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy)).isFalse();
  }

  @Test public void dispatchRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    );

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void dispatchRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1),
        null,
        false,
        true
    );
    StandardTestDispatcher dispatcher = createDispatcher(false);

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void dispatchRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget()
    );
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        action,
        null,
        false,
        true
    );
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(action.willReplay).isTrue();
  }

  @Test public void dispatchRetryRetriesIfNoNetworkScanning() {
    StandardTestDispatcher dispatcher = createDispatcher(false);
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        action,
        null,
        true,
        dispatcher
    );

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(action.completedResult).isInstanceOf(Bitmap.class);
  }

  @Test public void dispatchRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget()
    );
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        action,
        null,
        false,
        true
    );

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).hasSize(1);
    Truth.assertThat(action.willReplay).isTrue();
  }

  @Test public void dispatchRetryRetriesIfShouldRetry() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget()
    );
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        action,
        null,
        true,
        dispatcher
    );

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(action.completedResult).isInstanceOf(Bitmap.class);
  }

  @Test public void dispatchRetrySkipIfServiceShutdown() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget()
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);

    dispatcher.shutdown();
    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
    Truth.assertThat(action.completedResult).isNull();
  }

  @Test public void dispatchAirplaneModeChange() {
    Truth.assertThat(dispatcher.airplaneMode).isFalse();

    dispatcher.dispatchAirplaneModeChange(true);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.airplaneMode).isTrue();

    dispatcher.dispatchAirplaneModeChange(false);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.airplaneMode).isFalse();
  }

  @Test public void dispatchNetworkStateChangeWithDisconnectedInfoIgnores() {
    NetworkInfo info = TestUtils.mockNetworkInfo();
    Mockito.when(info.isConnectedOrConnecting).thenReturn(false);

    dispatcher.dispatchNetworkStateChange(info);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void dispatchNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    NetworkInfo info = TestUtils.mockNetworkInfo(true);

    dispatcher.dispatchNetworkStateChange(info);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test public void dispatchPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedTags).containsExactly("tag");

    dispatcher.dispatchResumeTag("tag");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedTags).isEmpty();
  }

  @Test public void dispatchPauseTagIsIdempotent() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag"
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    Truth.assertThat(dispatcher.pausedActions).isEmpty();

    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);

    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedActions).containsEntry(action.getTarget(), action);
  }

  @Test public void dispatchPauseTagQueuesNewRequestDoesNotComplete() {
    dispatcher.dispatchPauseTag("tag");
    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, "tag");

    dispatcher.dispatchSubmit(action);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(action.completedResult).isNull();
  }

  @Test public void dispatchPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    Action action = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1, "anothertag");
    dispatcher.dispatchSubmit(action);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.pausedActions).isEmpty();
    Truth.assertThat(action.completedResult).isNotNull();
  }

  @Test public void dispatchPauseDetachesRequestAndCancelsHunter() {
    Action action = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag"
    );
    Hunter hunter = TestUtils.mockHunter(
        picasso,
        new Bitmap(bitmap1, MEMORY),
        action,
        dispatcher
    );
    hunter.job = new Job();

    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).isEmpty();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(action.completedResult).isNull();
  }

  @Test public void dispatchPauseOnlyDetachesPausedRequest() {
    Action action1 = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag1"
    );
    Action action2 = TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        TestUtils.mockBitmapTarget(),
        "tag2"
    );
    Hunter hunter = TestUtils.mockHunter(picasso, new Bitmap(bitmap1, MEMORY), action1);
    hunter.attach(action2);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);

    dispatcher.dispatchPauseTag("tag1");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(dispatcher.hunterMap).hasSize(1);
    Truth.assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue();
    Truth.assertThat(dispatcher.pausedActions).hasSize(1);
    Truth.assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue();
    Truth.assertThat(hunter.action).isNull();
    Truth.assertThat(hunter.actions).containsExactly(action2);
  }

  @Test public void dispatchResumeTagIsIdempotent() {
    int completedCount = 0;
    Action action = noopAction(Request.Builder(TestUtils.URI_1).tag("tag").build(), () -> {
      completedCount++;
    });

    dispatcher.dispatchPauseTag("tag");
    dispatcher.dispatchSubmit(action);
    dispatcher.dispatchResumeTag("tag");
    dispatcher.dispatchResumeTag("tag");
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(completedCount).isEqualTo(1);
  }

  @Test public void dispatchNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = TestUtils.mockNetworkInfo(true);
    FailedAction failedAction1 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1);
    FailedAction failedAction2 = TestUtils.mockAction(picasso, TestUtils.URI_KEY_2, TestUtils.URI_2);
    dispatcher.failedActions.put(TestUtils.URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(TestUtils.URI_KEY_2, failedAction2);

    dispatcher.dispatchNetworkStateChange(info);
    testDispatcher.scheduler.runCurrent();

    Truth.assertThat(failedAction1.completedResult).isNotNull();
    Truth.assertThat(failedAction2.completedResult).isNotNull();
    Truth.assertThat(dispatcher.failedActions).isEmpty();
  }

  private InternalCoroutineDispatcher createDispatcher(
      boolean scansNetworkChanges
  ) {
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(
        scansNetworkChanges ? Mockito.mock(NetworkInfo.class) : null
    );
    Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    Mockito.when(context.checkCallingOrSelfPermission(ArgumentMatchers.anyString()))
        .thenReturn(
            scansNetworkChanges
                ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED
        );

    testDispatcher = new StandardTestDispatcher();
    picasso = TestUtils.mockPicasso(context).newBuilder()
        .dispatchers(testDispatcher, testDispatcher)
        .build();
    return new InternalCoroutineDispatcher(
        context,
        new Handler(Looper.getMainLooper()),
        cache,
        testDispatcher,
        testDispatcher
    );
  }

  private Action noopAction(Request data, Runnable onComplete) {
    return new Action(picasso, data) {
      @Override public void complete(Result<Bitmap> result) {
        onComplete.run();
      }

      @Override public void error(Exception e) {
      }

      @Override public Object getTarget() {
        return this;
      }
    };
  }
}
