package com.squareup.picasso3;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import java.util.concurrent.ExecutorService;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.cancel;
import kotlinx.coroutines.delay;
import kotlinx.coroutines.launch;

public class InternalCoroutineDispatcher extends Dispatcher {
  
  private CoroutineScope scope;
  private final CoroutineDispatcher picassoDispatcher;

  public InternalCoroutineDispatcher(Context context, ExecutorService service, Handler mainThreadHandler, PlatformLruCache cache, CoroutineDispatcher picassoDispatcher) {
    super(context, service, mainThreadHandler, cache);
    this.picassoDispatcher = picassoDispatcher;
    this.scope = new CoroutineScope(picassoDispatcher.limitedParallelism(1));
  }

  @Override
  public void shutdown() {
    super.shutdown();
    scope.cancel();
  }

  @Override
  public void dispatchSubmit(Action action) {
    scope.launch(Dispatchers.Default) {
      performSubmit(action);
    }
  }

  @Override
  public void dispatchCancel(Action action) {
    scope.launch(Dispatchers.Default) {
      performCancel(action);
    }
  }

  @Override
  public void dispatchPauseTag(Object tag) {
    scope.launch(Dispatchers.Default) {
      performPauseTag(tag);
    }
  }

  @Override
  public void dispatchResumeTag(Object tag) {
    scope.launch(Dispatchers.Default) {
      performResumeTag(tag);
    }
  }

  @Override
  public void dispatchComplete(BitmapHunter hunter) {
    scope.launch(Dispatchers.Default) {
      performComplete(hunter);
    }
  }

  @Override
  public void dispatchRetry(BitmapHunter hunter) {
    scope.launch(Dispatchers.Default) {
      delay(RETRY_DELAY);
      performRetry(hunter);
    }
  }

  @Override
  public void dispatchFailed(BitmapHunter hunter) {
    scope.launch(Dispatchers.Default) {
      performError(hunter);
    }
  }

  @Override
  public void dispatchNetworkStateChange(NetworkInfo info) {
    scope.launch(Dispatchers.Default) {
      performNetworkStateChange(info);
    }
  }

  @Override
  public void dispatchAirplaneModeChange(boolean airplaneMode) {
    scope.launch(Dispatchers.Default) {
      performAirplaneModeChange(airplaneMode);
    }
  }

  @Override
  public void dispatchCompleteMain(BitmapHunter hunter) {
    scope.launch(Dispatchers.Main) {
      performCompleteMain(hunter);
    }
  }

  @Override
  public void dispatchBatchResumeMain(List<Action> batch) {
    scope.launch(Dispatchers.Main) {
      performBatchResumeMain(batch);
    }
  }
}