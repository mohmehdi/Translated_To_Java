package com.squareup.picasso3;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.ConnectivityManagerListener;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.BitmapHunter.BitmapHunterKey;
import com.squareup.picasso3.MemoryPolicy.MemoryPolicy;
import com.squareup.picasso3.NetworkPolicy.NetworkPolicy;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Utils.LogId;
import com.squareup.picasso3.Utils.OWNER_DISPATCHER;
import com.squareup.picasso3.Utils.VERB_CANCELED;
import com.squareup.picasso3.Utils.VERB_DELIVERED;
import com.squareup.picasso3.Utils.VERB_ENQUEUED;
import com.squareup.picasso3.Utils.VERB_IGNORED;
import com.squareup.picasso3.Utils.VERB_PAUSED;
import com.squareup.picasso3.Utils.VERB_REPLAYING;
import com.squareup.picasso3.Utils.VERB_RETRYING;
import com.squareup.picasso3.Utils.getLogIdsForHunter;
import com.squareup.picasso3.Utils.hasPermission;
import com.squareup.picasso3.Utils.isAirplaneModeOn;
import com.squareup.picasso3.Utils.log;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Dispatcher {
  private final Context context;
  private final ExecutorService service;
  private final Handler mainThreadHandler;
  private final PlatformLruCache cache;
  private final Map<String, BitmapHunter> hunterMap;
  private final WeakHashMap<Object, Action> failedActions;
  private final WeakHashMap<Object, Action> pausedActions;
  private final Set<Object> pausedTags;
  private final NetworkBroadcastReceiver receiver;
  private boolean airplaneMode;
  private final boolean scansNetworkChanges;

  Dispatcher(
      Context context,
      ExecutorService service,
      Looper mainThreadLooper,
      PlatformLruCache cache) {
    this.context = context;
    this.service = service;
    this.mainThreadHandler = new Handler(mainThreadLooper);
    this.cache = cache;
    hunterMap = new WeakHashMap<>();
    failedActions = new WeakHashMap<>();
    pausedActions = new WeakHashMap<>();
    pausedTags = new HashSet<>();
    receiver = new NetworkBroadcastReceiver(this);
    scansNetworkChanges =
        hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);

    airplaneMode = isAirplaneModeOn(context);

    if (scansNetworkChanges) {
      ConnectivityManager connectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (connectivityManager != null) {
        connectivityManager.addListener(
            new ConnectivityManagerListener() {
              @Override
              public void onNetworkStateChanged(ConnectivityManager manager, NetworkInfo info) {
                dispatchNetworkStateChange(info);
              }
            });
      }
    }

    receiver.register();
  }

  @CallSuper
  void shutdown() {
    // Shutdown the thread pool only if it is the one created by Picasso.
    if (service instanceof PicassoExecutorService) {
      ((PicassoExecutorService) service).shutdown();
    }
    // Unregister network broadcast receiver on the main thread.
    mainThreadHandler.post(() -> receiver.unregister());
  }

  void dispatchSubmit(Action action) {
    // Implementation is missing.
  }

  void dispatchCancel(Action action) {
    // Implementation is missing.
  }

  void dispatchPauseTag(Object tag) {
    // Implementation is missing.
  }

  void dispatchResumeTag(Object tag) {
    // Implementation is missing.
  }

  void dispatchComplete(BitmapHunter hunter) {
    // Implementation is missing.
  }

  void dispatchRetry(BitmapHunter hunter) {
    // Implementation is missing.
  }

  void dispatchFailed(BitmapHunter hunter) {
    // Implementation is missing.
  }

  void dispatchNetworkStateChange(NetworkInfo info) {
    // Implementation is missing.
  }

  void dispatchAirplaneModeChange(boolean airplaneMode) {
    // Implementation is missing.
  }

  void dispatchCompleteMain(BitmapHunter hunter) {
    // Implementation is missing.
  }

  void dispatchBatchResumeMain(List<Action> batch) {
    // Implementation is missing.
  }

  void performSubmit(Action action, boolean dismissFailed) {
    if (action.getTag() != null && pausedTags.contains(action.getTag())) {
      pausedActions.put(action.getTarget(), action);
      if (action.picasso.isLoggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_PAUSED,
            action.request.logId(),
            "because tag '" + action.getTag() + "' is paused");
      }
      return;
    }

    BitmapHunter hunter = hunterMap.get(action.request.getKey());
    if (hunter != null) {
      hunter.attach(action);
      return;
    }

    if (service.isShutdown()) {
      if (action.picasso.isLoggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_IGNORED,
            action.request.logId(),
            "because shut down");
      }
      return;
    }

    hunter = BitmapHunter.forRequest(action.picasso, this, cache, action);
    hunter.future = service.submit(hunter);
    hunterMap.put(action.request.getKey(), hunter);
    if (dismissFailed) {
      failedActions.remove(action.getTarget());
    }

    if (action.picasso.isLoggingEnabled) {
      log(OWNER_DISPATCHER, VERB_ENQUEUED, action.request.logId());
    }
  }

  void performCancel(Action action) {
    String key = action.request.getKey();
    BitmapHunter hunter = hunterMap.get(key);
    if (hunter != null) {
      hunter.detach(action);
      if (hunter.cancel()) {
        hunterMap.remove(key);
        if (action.picasso.isLoggingEnabled) {
          log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId());
        }
      }
    }

    if (action.getTag() != null && pausedTags.contains(action.getTag())) {
      pausedActions.remove(action.getTarget());
      if (action.picasso.isLoggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_CANCELED,
            action.request.logId(),
            "because paused request got canceled");
      }
    }

    Action remove = failedActions.remove(action.getTarget());
    if (remove != null && remove.picasso.isLoggingEnabled) {
      log(
          OWNER_DISPATCHER,
          VERB_CANCELED,
          remove.request.logId(),
          "from replaying");
    }
  }

  void performPauseTag(Object tag) {
    // Trying to pause a tag that is already paused.
    if (!pausedTags.add(tag)) {
      return;
    }

    // Go through all active hunters and detach/pause the requests
    // that have the paused tag.
    Iterator<BitmapHunter> iterator = hunterMap.values().iterator();
    while (iterator.hasNext()) {
      BitmapHunter hunter = iterator.next();
      boolean loggingEnabled = hunter.picasso.isLoggingEnabled;

      Action single = hunter.action;
      List<Action> joined = hunter.actions;
      boolean hasMultiple = joined != null && !joined.isEmpty();

      // Hunter has no requests, bail early.
      if (single == null && !hasMultiple) {
        continue;
      }

      if (single != null && single.getTag().equals(tag)) {
        hunter.detach(single);
        pausedActions.put(single.getTarget(), single);
        if (loggingEnabled) {
          log(
              OWNER_DISPATCHER,
              VERB_PAUSED,
              action.request.logId(),
              "because tag '" + tag + "' was paused");
        }
      }

      if (joined != null) {
        for (int i = joined.size() - 1; i >= 0; i--) {
          Action action = joined.get(i);
          if (!action.getTag().equals(tag)) {
            continue;
          }
          hunter.detach(action);
          pausedActions.put(action.getTarget(), action);
          if (loggingEnabled) {
            log(
                OWNER_DISPATCHER,
                VERB_PAUSED,
                action.request.logId(),
                "because tag '" + tag + "' was paused");
          }
        }
      }

      // Check if the hunter can be cancelled in case all its requests
      // had the tag being paused here.
      if (hunter.cancel()) {
        iterator.remove();
        if (loggingEnabled) {
          log(
              OWNER_DISPATCHER,
              VERB_CANCELED,
              getLogIdsForHunter(hunter),
              "all actions paused");
        }
      }
    }
  }

  void performResumeTag(Object tag) {
    // Trying to resume a tag that is not paused.
    if (!pausedTags.remove(tag)) {
      return;
    }

    List<Action> batch = new ArrayList<>();
    Iterator<Action> iterator = pausedActions.values().iterator();
    while (iterator.hasNext()) {
      Action action = iterator.next();
      if (!action.getTag().equals(tag)) {
        continue;
      }
      batch.add(action);
      iterator.remove();
    }

    if (!batch.isEmpty()) {
      dispatchBatchResumeMain(batch);
    }
  }

  void performRetry(BitmapHunter hunter) {
    if (hunter.isCancelled()) {
      return;
    }

    if (service.isShutdown()) {
      performError(hunter);
      return;
    }

    NetworkInfo networkInfo = null;
    if (scansNetworkChanges) {
      ConnectivityManager connectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (connectivityManager != null) {
        networkInfo = connectivityManager.getActiveNetworkInfo();
      }
    }

    if (hunter.shouldRetry(airplaneMode, networkInfo)) {
      if (hunter.picasso.isLoggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_RETRYING,
            getLogIdsForHunter(hunter));
      }
      if (hunter.exception instanceof ContentLengthException) {
        hunter.data =
            hunter.data.newBuilder()
                .networkPolicy(NetworkPolicy.NO_CACHE)
                .build();
      }
      hunter.future = service.submit(hunter);
    } else {
      performError(hunter);
      // Mark for replay only if we observe network info changes and support replay.
      if (scansNetworkChanges && hunter.supportsReplay()) {
        markForReplay(hunter);
      }
    }
  }

  void performComplete(BitmapHunter hunter) {
    if (hunter.data.memoryPolicy.shouldWriteToMemoryCache()) {
      Result result = hunter.result;
      if (result != null) {
        if (result instanceof Bitmap) {
          Bitmap bitmap = (Bitmap) result;
          bitmap.prepareToDraw();
          cache.put(hunter.key, bitmap);
        }
      }
    }
    hunterMap.remove(hunter.key);
    deliver(hunter);
  }

  void performError(BitmapHunter hunter) {
    hunterMap.remove(hunter.key);
    deliver(hunter);
  }

  void performAirplaneModeChange(boolean airplaneMode) {
    this.airplaneMode = airplaneMode;
  }

  void performNetworkStateChange(NetworkInfo networkInfo) {
    // Intentionally check only if isConnected() here before we flush out failed actions.
    if (networkInfo != null && networkInfo.isConnected()) {
      flushFailedActions();
    }
  }

  @MainThread
  void performCompleteMain(BitmapHunter hunter) {
    hunter.picasso.complete(hunter);
  }

  @MainThread
  void performBatchResumeMain(List<Action> batch) {
    for (int i = 0; i < batch.size(); i++) {
      Action action = batch.get(i);
      action.picasso.resumeAction(action);
    }
  }

  private void flushFailedActions() {
    if (!failedActions.isEmpty()) {
      Iterator<Action> iterator = failedActions.values().iterator();
      while (iterator.hasNext()) {
        Action action = iterator.next();
        iterator.remove();
        if (action.picasso.isLoggingEnabled) {
          log(
              OWNER_DISPATCHER,
              VERB_REPLAYING,
              action.request.logId());
        }
        performSubmit(action, false);
      }
    }
  }

  private void markForReplay(BitmapHunter hunter) {
    Action action = hunter.action;
    action.willReplay = true;
    failedActions.put(action.getTarget(), action);
    List<Action> joined = hunter.actions;
    if (joined != null) {
      for (int i = 0; i < joined.size(); i++) {
        markForReplay(joined.get(i));
      }
    }
  }

  private void markForReplay(Action action) {
    Object target = action.getTarget();
    action.willReplay = true;
    failedActions.put(target, action);
  }

  private void deliver(BitmapHunter bitmapHunter) {
    if (bitmapHunter.isCancelled()) {
      return;
    }
    Result result = bitmapHunter.result;
    if (result != null) {
      if (result instanceof Bitmap) {
        Bitmap bitmap = (Bitmap) result;
        bitmap.prepareToDraw();
      }
    }

    performCompleteMain(bitmapHunter);

    logDelivery(bitmapHunter);
  }

  private void logDelivery(BitmapHunter bitmapHunter) {
    Picasso picasso = bitmapHunter.picasso;
    if (picasso.isLoggingEnabled) {
      log(
          OWNER_DISPATCHER,
          VERB_DELIVERED,
          getLogIdsForHunter(bitmapHunter));
    }
  }

  static class NetworkBroadcastReceiver extends BroadcastReceiver {
    private final Dispatcher dispatcher;

    NetworkBroadcastReceiver(Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    void register() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
      if (dispatcher.scansNetworkChanges) {
        filter.addAction(CONNECTIVITY_ACTION);
      }
      context.registerReceiver(this, filter);
    }

    void unregister() {
      context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      // On some versions of Android this may be called with a null Intent,
      // also without extras (getExtras() == null), in such case we use defaults.
      if (intent == null) {
        return;
      }
      String action = intent.getAction();
      if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
        if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
          return; // No airplane state, ignore it. Should we query Utils.isAirplaneModeOn?
        }
        dispatcher.dispatchAirplaneModeChange(
            intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false));
      } else if (CONNECTIVITY_ACTION.equals(action)) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo =
            connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        if (networkInfo == null) {
          Log.w(
              TAG,
              "No default network is currently active, ignoring attempt to change network state.");
          return;
        }
        dispatcher.dispatchNetworkStateChange(networkInfo);
      }
    }

    private static final String EXTRA_AIRPLANE_STATE = "state";
  }

  private static final String TAG = "Picasso";
  private static final int RETRY_DELAY = 500;
  private static final int HUNTER_COMPLETE = 4;
  private static final int NETWORK_STATE_CHANGE = 9;
  private static final int REQUEST_BATCH_RESUME = 13;
}