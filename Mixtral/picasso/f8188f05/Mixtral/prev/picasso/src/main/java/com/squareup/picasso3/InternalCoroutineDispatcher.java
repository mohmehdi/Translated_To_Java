package com.squareup.picasso3;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.CoroutineDispatcher;
import kotlin.coroutines.CoroutineScope;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.DelicateCoroutinesApi;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.launch;

@DelicateCoroutinesApi
public class InternalCoroutineDispatcher extends Dispatcher {
  private final CoroutineScope scope;

  public InternalCoroutineDispatcher(
      Context context,
      ExecutorService service,
      Handler mainThreadHandler,
      PlatformLruCache cache,
      CoroutineDispatcher picassoDispatcher) {
    super(context, service, mainThreadHandler, cache);
    this.scope =
        GlobalScope
            .newCoroutineContext(
                EmptyCoroutineContext.INSTANCE,
                new Job(picassoDispatcher.getLimitedParallelism(1)))
            .plus(picassoDispatcher);
  }

  @Override
  public void shutdown() {
    super.shutdown();
    this.scope.cancel();
  }

  @Override
  public void dispatchSubmit(Action action) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performSubmit(action);
          }
        });
  }

  @Override
  public void dispatchCancel(Action action) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performCancel(action);
          }
        });
  }

  @Override
  public void dispatchPauseTag(Object tag) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performPauseTag(tag);
          }
        });
  }

  @Override
  public void dispatchResumeTag(Object tag) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performResumeTag(tag);
          }
        });
  }

  @Override
  public void dispatchComplete(BitmapHunter hunter) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performComplete(hunter);
          }
        });
  }

  @Override
  public void dispatchRetry(BitmapHunter hunter) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            try {
              Thread.sleep(RETRY_DELAY);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            performRetry(hunter);
          }
        });
  }

  @Override
  public void dispatchFailed(BitmapHunter hunter) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performError(hunter);
          }
        });
  }

  @Override
  public void dispatchNetworkStateChange(NetworkInfo info) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performNetworkStateChange(info);
          }
        });
  }

  @Override
  public void dispatchAirplaneModeChange(boolean airplaneMode) {
    this.scope.launch(
        new Runnable() {
          @Override
          public void run() {
            performAirplaneModeChange(airplaneMode);
          }
        });
  }

  @Override
  public void dispatchCompleteMain(BitmapHunter hunter) {
    this.scope.launch(
        Dispatchers.Main,
        new Runnable() {
          @Override
          public void run() {
            performCompleteMain(hunter);
          }
        });
  }

  @Override
  public void dispatchBatchResumeMain(List<Action> batch) {
    this.scope.launch(
        Dispatchers.Main,
        new Runnable() {
          @Override
          public void run() {
            performBatchResumeMain(batch);
          }
        });
  }
}