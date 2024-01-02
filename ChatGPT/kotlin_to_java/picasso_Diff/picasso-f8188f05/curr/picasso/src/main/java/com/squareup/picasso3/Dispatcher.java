package com.squareup.picasso3;

import android.net.NetworkInfo;

internal interface Dispatcher {
  void shutdown();

  void dispatchSubmit(Action action);

  void dispatchCancel(Action action);

  void dispatchPauseTag(Object tag);

  void dispatchResumeTag(Object tag);

  void dispatchComplete(BitmapHunter hunter);

  void dispatchRetry(BitmapHunter hunter);

  void dispatchFailed(BitmapHunter hunter);

  void dispatchNetworkStateChange(NetworkInfo info);

  void dispatchAirplaneModeChange(boolean airplaneMode);

  void dispatchSubmit(BitmapHunter hunter);

  void dispatchCompleteMain(BitmapHunter hunter);

  void dispatchBatchResumeMain(List<Action> batch);

  boolean isShutdown();

  interface Companion {
    long RETRY_DELAY = 500L;
  }
}