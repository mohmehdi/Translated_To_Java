package com.squareup.picasso3;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.ConnectivityAction;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class BaseDispatcher implements Dispatcher {
  private final Context context;
  private final Handler mainThreadHandler;
  private final PlatformLruCache cache;
  private final Map<String, BitmapHunter> hunterMap;
  private final Map<Object, Action> failedActions;
  private final Map<Object, Action> pausedActions;
  private final Set<Object> pausedTags;
  private final NetworkBroadcastReceiver receiver;
  private boolean airplaneMode;
  private final boolean scansNetworkChanges;

  public BaseDispatcher(
      @NonNull Context context,
      @NonNull Handler mainThreadHandler,
      @NonNull PlatformLruCache cache) {
    this.context = context.getApplicationContext();
    this.mainThreadHandler = mainThreadHandler;
    this.cache = cache;
    hunterMap = new HashMap<>();
    failedActions = new WeakHashMap<>();
    pausedActions = new WeakHashMap<>();
    pausedTags = new HashSet<>();
    receiver = new NetworkBroadcastReceiver(this);
    scansNetworkChanges = context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        == PackageManager.PERMISSION_GRANTED;
    airplaneMode = isAirplaneModeOn(context);
  }

  @CallSuper
  @Override
  public void shutdown() {
    mainThreadHandler.post(() -> receiver.unregister());
  }

  public void performSubmit(Action action, boolean dismissFailed) {
    Object tag = action.getTag();
    if (pausedTags.contains(tag)) {
      pausedActions.put(action.getTarget(), action);
      if (action.picasso.loggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_PAUSED,
            action.request.logId(),
            "because tag '" + tag + "' is paused");
      }
      return;
    }

    BitmapHunter hunter = hunterMap.get(action.request.key);
    if (hunter != null) {
      hunter.attach(action);
      return;
    }

    if (isShutdown()) {
      if (action.picasso.loggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_IGNORED,
            action.request.logId(),
            "because shut down");
      }
      return;
    }

    hunter = forRequest(action.picasso, this, cache, action);
    dispatchSubmit(hunter);
    hunterMap.put(action.request.key, hunter);
    if (dismissFailed) {
      failedActions.remove(action.getTarget());
    }

    if (action.picasso.loggingEnabled) {
      log(OWNER_DISPATCHER, VERB_ENQUEUED, action.request.logId());
    }
  }

  public void performCancel(Action action) {
    String key = action.request.key;
    BitmapHunter hunter = hunterMap.get(key);
    if (hunter != null) {
      hunter.detach(action);
      if (hunter.cancel()) {
        hunterMap.remove(key);
        if (action.picasso.loggingEnabled) {
          log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId());
        }
      }
    }

    if (tag != null && pausedTags.contains(tag)) {
      pausedActions.remove(action.getTarget());
      if (action.picasso.loggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_CANCELED,
            action.request.logId(),
            "because paused request got canceled");
      }
    }

    Action remove = failedActions.remove(action.getTarget());
    if (remove != null && remove.picasso.loggingEnabled) {
      log(
          OWNER_DISPATCHER,
          VERB_CANCELED,
          remove.request.logId(),
          "from replaying");
    }
  }

  public void performPauseTag(Object tag) {
    if (!pausedTags.add(tag)) {
      return;
    }

    Iterator<BitmapHunter> iterator = hunterMap.values().iterator();
    while (iterator.hasNext()) {
      BitmapHunter hunter = iterator.next();
      boolean loggingEnabled = hunter.picasso.loggingEnabled;

      Action single = hunter.action;
      List<Action> joined = hunter.actions;
      boolean hasMultiple = joined != null && !joined.isEmpty();

      if (single == null && !hasMultiple) {
        continue;
      }

      if (single != null && single.tag.equals(tag)) {
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
          if (!action.tag.equals(tag)) {
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

  public void performResumeTag(Object tag) {
    if (!pausedTags.remove(tag)) {
      return;
    }

    List<Action> batch = new ArrayList<>();
    Iterator<Action> iterator = pausedActions.values().iterator();
    while (iterator.hasNext()) {
      Action action = iterator.next();
      if (!action.tag.equals(tag)) {
        continue;
      }
      batch.add(action);
      iterator.remove();
    }

    if (!batch.isEmpty()) {
      dispatchBatchResumeMain(batch);
    }
  }

  @SuppressLint("MissingPermission")
  public void performRetry(BitmapHunter hunter) {
    if (hunter.isCancelled) {
      return;
    }

    if (isShutdown()) {
      performError(hunter);
      return;
    }

    NetworkInfo networkInfo = null;
    if (scansNetworkChanges) {
      ConnectivityManager connectivityManager =
          (ConnectivityManager)
              context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (connectivityManager != null) {
        networkInfo = connectivityManager.getActiveNetworkInfo();
      }
    }

    if (hunter.shouldRetry(airplaneMode, networkInfo)) {
      if (hunter.picasso.loggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_RETRYING,
            getLogIdsForHunter(hunter));
      }
      if (hunter.exception instanceof ContentLengthException) {
        hunter.data =
            hunter.data.newBuilder()
                .networkPolicy(NO_CACHE)
                .build();
      }
      dispatchSubmit(hunter);
    } else {
      performError(hunter);

      if (scansNetworkChanges && hunter.supportsReplay()) {
        markForReplay(hunter);
      }
    }
  }

  public void performComplete(BitmapHunter hunter) {
    if (shouldWriteToMemoryCache(hunter.data.memoryPolicy)) {
      Object result = hunter.result;
      if (result instanceof Bitmap) {
        Bitmap bitmap = (Bitmap) result;
        bitmap.prepareToDraw();
      }
    }
    hunterMap.remove(hunter.key);
    deliver(hunter);
  }

  public void performError(BitmapHunter hunter) {
    hunterMap.remove(hunter.key);
    deliver(hunter);
  }

  public void performAirplaneModeChange(boolean airplaneMode) {
    this.airplaneMode = airplaneMode;
  }

  public void performNetworkStateChange(@Nullable NetworkInfo info) {
    if (info != null && info.isConnected()) {
      flushFailedActions();
    }
  }

  @MainThread
  public void performCompleteMain(BitmapHunter hunter) {
    hunter.picasso.complete(hunter);
  }

  @MainThread
  public void performBatchResumeMain(List<Action> batch) {
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
        if (action.picasso.loggingEnabled) {
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
    if (bitmapHunter.isCancelled) {
      return;
    }
    Object result = bitmapHunter.result;
    if (result != null) {
      if (result instanceof Bitmap) {
        Bitmap bitmap = (Bitmap) result;
        bitmap.prepareToDraw();
      }
    }

    dispatchCompleteMain(bitmapHunter);
    logDelivery(bitmapHunter);
  }

  private void logDelivery(BitmapHunter bitmapHunter) {
    Picasso picasso = bitmapHunter.picasso;
    if (picasso.loggingEnabled) {
      log(
          OWNER_DISPATCHER,
          VERB_DELIVERED,
          getLogIdsForHunter(bitmapHunter));
    }
  }

  public static class NetworkBroadcastReceiver extends BroadcastReceiver {
    private final BaseDispatcher dispatcher;

    public NetworkBroadcastReceiver(BaseDispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    public void register() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
      if (dispatcher.scansNetworkChanges) {
        filter.addAction(CONNECTIVITY_ACTION);
      }
      context.registerReceiver(this, filter);
    }

    public void unregister() {
      context.unregisterReceiver(this);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent == null) {
        return;
      }
      String action = intent.getAction();
      if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
        if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
          return;
        }
        dispatcher.dispatchAirplaneModeChange(
            intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false));
      } else if (CONNECTIVITY_ACTION.equals(action)) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo =
            connectivityManager != null
                ? connectivityManager.getActiveNetworkInfo()
                : null;
        if (networkInfo == null) {
          return;
        }
        dispatcher.dispatchNetworkStateChange(networkInfo);
      }
    }

    private static final String EXTRA_AIRPLANE_STATE = "state";
  }
}

