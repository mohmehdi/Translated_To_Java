package com.squareup.picasso3;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.CoroutineDispatcher;
import kotlin.coroutines.CoroutineName;
import kotlin.coroutines.CoroutineScope;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.SupervisorJob;
import kotlinx.coroutines.channels.Channel;

@SuppressWarnings("unused")
public class InternalCoroutineDispatcher extends BaseDispatcher {
  private final CoroutineScope scope;
  private final Channel<Runnable> channel;

  public InternalCoroutineDispatcher(Context context, Handler mainThreadHandler, PlatformLruCache cache,
      CoroutineDispatcher mainDispatcher, CoroutineDispatcher backgroundDispatcher) {
    super(context, mainThreadHandler, cache);
    this.mainDispatcher = mainDispatcher;
    this.backgroundDispatcher = backgroundDispatcher;
    scope = CoroutineScope(new SupervisorJob() + backgroundDispatcher);
    channel = Channel.factory().openConcurrent(Integer.MAX_VALUE);

    scope.launch(new Executor() {
      @Override
      public void execute(Runnable command) {
        while (!channel.isClosedForReceive()) {
          try {
            channel.receive().run();
          } catch (Exception e) {
            // Ignore exceptions
          }
        }
      }
    });
  }

  @Override
  public void shutdown() {
    super.shutdown();
    channel.close();
    scope.cancel();
  }

  @Override
  public void dispatchSubmit(Action action) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performSubmit(action);
      }
    });
  }

  @Override
  public void dispatchCancel(Action action) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performCancel(action);
      }
    });
  }

  @Override
  public void dispatchPauseTag(Object tag) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performPauseTag(tag);
      }
    });
  }

  @Override
  public void dispatchResumeTag(Object tag) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performResumeTag(tag);
      }
    });
  }

  @Override
  public void dispatchComplete(BitmapHunter hunter) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performComplete(hunter);
      }
    });
  }

  @Override
  public void dispatchRetry(BitmapHunter hunter) {
    scope.launch(new Executor() {
      @Override
      public void execute(Runnable command) {
        try {
          Thread.sleep(RETRY_DELAY);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        channel.send(new Runnable() {
          @Override
          public void run() {
            performRetry(hunter);
          }
        });
      }
    });
  }

  @Override
  public void dispatchFailed(BitmapHunter hunter) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performError(hunter);
      }
    });
  }

  @Override
  public void dispatchNetworkStateChange(NetworkInfo info) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performNetworkStateChange(info);
      }
    });
  }

  @Override
  public void dispatchAirplaneModeChange(boolean airplaneMode) {
    channel.trySend(new Runnable() {
      @Override
      public void run() {
        performAirplaneModeChange(airplaneMode);
      }
    });
  }

  @Override
  public void dispatchCompleteMain(BitmapHunter hunter) {
    scope.launch(mainDispatcher, new Runnable() {
      @Override
      public void run() {
        performCompleteMain(hunter);
      }
    });
  }

  @Override
  public void dispatchBatchResumeMain(ConcurrentLinkedQueue<Action> batch) {
    scope.launch(mainDispatcher, new Runnable() {
      @Override
      public void run() {
        performBatchResumeMain(batch);
      }
    });
  }

  @Override
  public void dispatchSubmit(BitmapHunter hunter) {
    hunter.job = scope.launch(new CoroutineName("hunter." + hunter.getName()),
        new Executor() {
          @Override
          public void execute(Runnable command) {
            hunter.run();
          }
        });
  }

  @Override
  public boolean isShutdown() {
    return !scope.isActive;
  }
}

