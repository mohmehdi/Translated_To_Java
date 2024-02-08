package com.squareup.picasso3;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.PlatformLruCache;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.Request.Builder;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;
import com.squareup.picasso3.TestUtils;
import java.lang.Exception;
import java.lang.RuntimeException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public class InternalCoroutineDispatcherTest {

  @Mock
  private Context context;

  @Mock
  private ConnectivityManager connectivityManager;

  private Picasso picasso;
  private InternalCoroutineDispatcher dispatcher;
  private TestDispatcher testDispatcher;

  private final PlatformLruCache cache = new PlatformLruCache(2048);
  private final android.graphics.Bitmap bitmap1 = TestUtils.makeBitmap();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(context.applicationContext).thenReturn(context);
    dispatcher = createDispatcher();
  }

  @Test
  public void shutdownCancelsRunningJob() {
    createDispatcher(true);
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    dispatcher.dispatchSubmit(action);

    dispatcher.shutdown();
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.isShutdown()).isEqualTo(true);
    assertThat(action.completedResult).isNull();
  }

  @Test
  public void shutdownUnregistersReceiver() {
    dispatcher.shutdown();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    Mockito.verify(context).unregisterReceiver(dispatcher.receiver);
  }

  @Test
  public void dispatchSubmitWithNewRequestQueuesHunter() {
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    dispatcher.dispatchSubmit(action);

    testDispatcher.scheduler.runCurrent();

    assertThat(action.completedResult).isNotNull();
  }

  @Test
  public void dispatchSubmitWithTwoDifferentRequestsQueuesHunters() {
    Action action1 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    Action action2 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_2,
      TestUtils.URI_2
    );

    dispatcher.dispatchSubmit(action1);
    dispatcher.dispatchSubmit(action2);

    testDispatcher.scheduler.runCurrent();

    assertThat(action1.completedResult).isNotNull();
    assertThat(action2.completedResult).isNotNull();
    assertThat(action2.completedResult).isNotEqualTo(action1.completedResult);
  }

  @Test
  public void performSubmitWithExistingRequestAttachesToHunter() {
    Action action1 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    Action action2 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );

    dispatcher.dispatchSubmit(action1);
    dispatcher.dispatchSubmit(action2);
    testDispatcher.scheduler.runCurrent();

    assertThat(action1.completedResult).isNotNull();
    assertThat(action2.completedResult).isEqualTo(action1.completedResult);
  }

  @Test
  public void dispatchSubmitWithShutdownServiceIgnoresRequest() {
    dispatcher.shutdown();

    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    dispatcher.dispatchSubmit(action);
    testDispatcher.scheduler.runCurrent();

    assertThat(action.completedResult).isNull();
  }

  @Test
  public void dispatchSubmitWithFetchAction() {
    String pausedTag = "pausedTag";
    dispatcher.dispatchPauseTag(pausedTag);
    testDispatcher.scheduler.runCurrent();
    assertThat(dispatcher.pausedActions).isEmpty();

    boolean[] completed = { false };
    Action fetchAction1 = noopAction(
      new Request.Builder(TestUtils.URI_1).tag(pausedTag).build(),
      () -> completed[0] = true
    );
    Action fetchAction2 = noopAction(
      new Request.Builder(TestUtils.URI_1).tag(pausedTag).build(),
      () -> completed[0] = true
    );
    dispatcher.dispatchSubmit(fetchAction1);
    dispatcher.dispatchSubmit(fetchAction2);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedActions).hasSize(2);
    assertThat(completed[0]).isFalse();
  }

  @Test
  public void dispatchCancelWithFetchActionWithCallback() {
    String pausedTag = "pausedTag";
    dispatcher.dispatchPauseTag(pausedTag);
    testDispatcher.scheduler.runCurrent();
    assertThat(dispatcher.pausedActions).isEmpty();

    Action callback = TestUtils.mockCallback();

    Action fetchAction1 = new FetchAction(
      picasso,
      new Request.Builder(TestUtils.URI_1).tag(pausedTag).build(),
      callback
    );
    dispatcher.dispatchSubmit(fetchAction1);
    testDispatcher.scheduler.runCurrent();
    assertThat(dispatcher.pausedActions).hasSize(1);

    dispatcher.dispatchCancel(fetchAction1);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void dispatchCancelDetachesRequestAndCleansUp() {
    Object target = TestUtils.mockBitmapTarget();
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      target
    );
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action
    );
    hunter.job = new Job();
    dispatcher.hunterMap.put(
      TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR,
      hunter
    );
    dispatcher.failedActions.put(target, action);

    dispatcher.dispatchCancel(action);
    testDispatcher.scheduler.runCurrent();

    assertThat(hunter.job.isCancelled()).isTrue();
    assertThat(hunter.action).isNull();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void dispatchCancelMultipleRequestsDetachesOnly() {
    Action action1 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    Action action2 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action1
    );
    hunter.attach(action2);
    dispatcher.hunterMap.put(
      TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR,
      hunter
    );

    dispatcher.dispatchCancel(action1);
    testDispatcher.scheduler.runCurrent();

    assertThat(hunter.action).isNull();
    assertThat(hunter.actions).containsExactly(action2);
    assertThat(dispatcher.hunterMap).hasSize(1);
  }

  @Test
  public void dispatchCancelUnqueuesAndDetachesPausedRequest() {
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
      action
    );
    dispatcher.dispatchSubmit(action);
    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();
    dispatcher.hunterMap.put(
      TestUtils.URI_KEY_1 + Request.KEY_SEPARATOR,
      hunter
    );

    dispatcher.dispatchCancel(action);
    testDispatcher.scheduler.runCurrent();

    assertThat(hunter.action).isNull();
    assertThat(dispatcher.pausedTags).containsExactly("tag");
    assertThat(dispatcher.pausedActions).isEmpty();
  }

  @Test
  public void dispatchCompleteSetsResultInCache() {
    Request data = new Request.Builder(TestUtils.URI_1).build();
    Action action = noopAction(data);
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action
    );
    hunter.run();
    assertThat(cache.size()).isEqualTo(0);

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    Bitmap result = (Bitmap) hunter.result;
    assertThat(result.bitmap).isEqualTo(bitmap1);
    assertThat(result.loadedFrom).isEqualTo(LoadedFrom.NETWORK);
    assertThat(cache.get(hunter.key)).isSameInstanceAs(bitmap1);
  }

  @Test
  public void dispatchCompleteWithNoStoreMemoryPolicy() {
    Request data = new Request.Builder(TestUtils.URI_1)
      .memoryPolicy(MemoryPolicy.NO_STORE)
      .build();
    Action action = noopAction(data);
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action
    );
    hunter.run();
    assertThat(cache.size()).isEqualTo(0);

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(cache.size()).isEqualTo(0);
  }

  @Test
  public void dispatchCompleteCleansUpAndPostsToMain() {
    Request data = new Request.Builder(TestUtils.URI_1).build();
    boolean[] completed = { false };
    Action action = noopAction(data, () -> completed[0] = true);
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action
    );
    hunter.run();

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(completed[0]).isTrue();
  }

  @Test
  public void dispatchCompleteCleansUpAndDoesNotPostToMainIfCancelled() {
    Request data = new Request.Builder(TestUtils.URI_1).build();
    boolean[] completed = { false };
    Action action = noopAction(data, () -> completed[0] = true);
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action
    );
    hunter.run();
    hunter.job = new Job().apply(Job::cancel);

    dispatcher.dispatchComplete(hunter);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(completed[0]).isFalse();
  }

  @Test
  public void dispatchErrorCleansUpAndPostsToMain() {
    RuntimeException exception = new RuntimeException();
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
      exception
    );
    hunter.run();
    dispatcher.hunterMap.put(hunter.key, hunter);

    dispatcher.dispatchFailed(hunter);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(action.errorException).isEqualTo(exception);
  }

  @Test
  public void dispatchErrorCleansUpAndDoesNotPostToMainIfCancelled() {
    RuntimeException exception = new RuntimeException();
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
      exception
    );
    hunter.run();
    hunter.job = new Job().apply(Job::cancel);
    dispatcher.hunterMap.put(hunter.key, hunter);

    dispatcher.dispatchFailed(hunter);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(action.errorException).isNull();
  }

  @Test
  public void dispatchRetrySkipsIfHunterIsCancelled() {
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
      action
    );
    hunter.job = new Job().apply(Job::cancel);

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.runCurrent();

    assertThat(hunter.isCancelled).isTrue();
    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void dispatchRetryForContentLengthResetsNetworkPolicy() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Mockito.when(connectivityManager.activeNetworkInfo).thenReturn(networkInfo);
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_2,
      TestUtils.URI_2
    );
    ContentLengthException e = new ContentLengthException("304 error");
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action,
      e,
      true
    );
    hunter.run();

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(NetworkPolicy.shouldReadFromDiskCache(hunter.data.networkPolicy))
      .isFalse();
  }

  @Test
  public void dispatchRetryDoesNotMarkForReplayIfNotSupported() {
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo(true);
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      TestUtils.mockAction(picasso, TestUtils.URI_KEY_1, TestUtils.URI_1)
    );
    Mockito
      .when(connectivityManager.getActiveNetworkInfo())
      .thenReturn(networkInfo);

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void dispatchRetryDoesNotMarkForReplayIfNoNetworkScanning() {
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      TestUtils.mockAction(
        picasso,
        TestUtils.URI_KEY_1,
        TestUtils.URI_1,
        null,
        false,
        true
      )
    );
    InternalCoroutineDispatcher dispatcher = createDispatcher(false);

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void dispatchRetryMarksForReplayIfSupportedScansNetworkChangesAndShouldNotRetry() {
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
    Mockito
      .when(connectivityManager.getActiveNetworkInfo())
      .thenReturn(networkInfo);

    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    assertThat(action.willReplay).isTrue();
  }

  @Test
  public void dispatchRetryRetriesIfNoNetworkScanning() {
    InternalCoroutineDispatcher dispatcher = createDispatcher(false);
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
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
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(action.completedResult).isInstanceOf(Bitmap.class);
  }

  @Test
  public void dispatchRetryMarksForReplayIfSupportsReplayAndShouldNotRetry() {
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
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).hasSize(1);
    assertThat(action.willReplay).isTrue();
  }

  @Test
  public void dispatchRetryRetriesIfShouldRetry() {
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
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(action.completedResult).isInstanceOf(Bitmap.class);
  }

  @Test
  public void dispatchRetrySkipIfServiceShutdown() {
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      TestUtils.mockBitmapTarget()
    );
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action
    );

    dispatcher.shutdown();
    dispatcher.dispatchRetry(hunter);
    testDispatcher.scheduler.advanceUntilIdle();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.failedActions).isEmpty();
    assertThat(action.completedResult).isNull();
  }

  @Test
  public void dispatchAirplaneModeChange() {
    assertThat(dispatcher.airplaneMode).isFalse();

    dispatcher.dispatchAirplaneModeChange(true);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.airplaneMode).isTrue();

    dispatcher.dispatchAirplaneModeChange(false);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.airplaneMode).isFalse();
  }

  @Test
  public void dispatchNetworkStateChangeWithDisconnectedInfoIgnores() {
    NetworkInfo info = TestUtils.mockNetworkInfo();
    Mockito.when(info.isConnectedOrConnecting()).thenReturn(false);

    dispatcher.dispatchNetworkStateChange(info);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void dispatchNetworkStateChangeWithConnectedInfoDifferentInstanceIgnores() {
    NetworkInfo info = TestUtils.mockNetworkInfo(true);

    dispatcher.dispatchNetworkStateChange(info);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.failedActions).isEmpty();
  }

  @Test
  public void dispatchPauseAndResumeUpdatesListOfPausedTags() {
    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedTags).containsExactly("tag");

    dispatcher.dispatchResumeTag("tag");
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedTags).isEmpty();
  }

  @Test
  public void dispatchPauseTagIsIdempotent() {
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
      action
    );
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);
    assertThat(dispatcher.pausedActions).isEmpty();

    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedActions)
      .containsEntry(action.getTarget(), action);

    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedActions)
      .containsEntry(action.getTarget(), action);
  }

  @Test
  public void dispatchPauseTagQueuesNewRequestDoesNotComplete() {
    dispatcher.dispatchPauseTag("tag");
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      "tag"
    );

    dispatcher.dispatchSubmit(action);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    assertThat(action.completedResult).isNull();
  }

  @Test
  public void dispatchPauseTagDoesNotQueueUnrelatedRequest() {
    dispatcher.dispatchPauseTag("tag");
    testDispatcher.scheduler.runCurrent();

    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
      "anothertag"
    );
    dispatcher.dispatchSubmit(action);
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.pausedActions).isEmpty();
    assertThat(action.completedResult).isNotNull();
  }

  @Test
  public void dispatchPauseDetachesRequestAndCancelsHunter() {
    Action action = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1,
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

    assertThat(dispatcher.hunterMap).isEmpty();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action)).isTrue();
    assertThat(hunter.action).isNull();
    assertThat(action.completedResult).isNull();
  }

  @Test
  public void dispatchPauseOnlyDetachesPausedRequest() {
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
    Hunter hunter = TestUtils.mockHunter(
      picasso,
      new Bitmap(bitmap1, MEMORY),
      action1
    );
    hunter.attach(action2);
    dispatcher.hunterMap.put(TestUtils.URI_KEY_1, hunter);

    dispatcher.dispatchPauseTag("tag1");
    testDispatcher.scheduler.runCurrent();

    assertThat(dispatcher.hunterMap).hasSize(1);
    assertThat(dispatcher.hunterMap.containsValue(hunter)).isTrue();
    assertThat(dispatcher.pausedActions).hasSize(1);
    assertThat(dispatcher.pausedActions.containsValue(action1)).isTrue();
    assertThat(hunter.action).isNull();
    assertThat(hunter.actions).containsExactly(action2);
  }

  @Test
  public void dispatchResumeTagIsIdempotent() {
    int[] completedCount = { 0 };
    Action action = noopAction(
      new Builder(TestUtils.URI_1).tag("tag").build(),
      () -> completedCount[0]++
    );

    dispatcher.dispatchPauseTag("tag");
    dispatcher.dispatchSubmit(action);
    dispatcher.dispatchResumeTag("tag");
    dispatcher.dispatchResumeTag("tag");
    testDispatcher.scheduler.runCurrent();

    assertThat(completedCount[0]).isEqualTo(1);
  }

  @Test
  public void dispatchNetworkStateChangeFlushesFailedHunters() {
    NetworkInfo info = TestUtils.mockNetworkInfo(true);
    Action failedAction1 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_1,
      TestUtils.URI_1
    );
    Action failedAction2 = TestUtils.mockAction(
      picasso,
      TestUtils.URI_KEY_2,
      TestUtils.URI_2
    );
    dispatcher.failedActions.put(TestUtils.URI_KEY_1, failedAction1);
    dispatcher.failedActions.put(TestUtils.URI_KEY_2, failedAction2);

    dispatcher.dispatchNetworkStateChange(info);
    testDispatcher.scheduler.runCurrent();

    assertThat(failedAction1.completedResult).isNotNull();
    assertThat(failedAction2.completedResult).isNotNull();
    assertThat(dispatcher.failedActions).isEmpty();
  }

  private InternalCoroutineDispatcher createDispatcher(
    boolean scansNetworkChanges
  ) {
    Mockito
      .when(connectivityManager.getActiveNetworkInfo())
      .thenReturn(scansNetworkChanges ? Mockito.mock(NetworkInfo.class) : null);
    Mockito
      .when(context.getSystemService(Context.CONNECTIVITY_SERVICE))
      .thenReturn(connectivityManager);
    Mockito
      .when(context.checkCallingOrSelfPermission(ArgumentMatchers.anyString()))
      .thenReturn(
        scansNetworkChanges
          ? PackageManager.PERMISSION_GRANTED
          : PackageManager.PERMISSION_DENIED
      );

    testDispatcher = new StandardTestDispatcher();
    picasso =
      TestUtils
        .mockPicasso(context)
        .newBuilder()
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
