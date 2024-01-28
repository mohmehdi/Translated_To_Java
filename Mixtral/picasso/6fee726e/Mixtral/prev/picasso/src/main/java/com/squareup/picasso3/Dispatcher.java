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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.BitmapHunter.Companion;
import com.squareup.picasso3.MemoryPolicy.Companion;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.Priority;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;
import com.squareup.picasso3.Utils.OWNER_DISPATCHER;
import com.squareup.picasso3.Utils.VERB_CANCELED;
import com.squareup.picasso3.Utils.VERB_DELIVERED;
import com.squareup.picasso3.Utils.VERB_ENQUEUED;
import com.squareup.picasso3.Utils.VERB_IGNORED;
import com.squareup.picasso3.Utils.VERB_PAUSED;
import com.squareup.picasso3.Utils.VERB_REPLAYING;
import com.squareup.picasso3.Utils.VERB_RETRYING;
import com.squareup.picasso3.Utils.flushStackLocalLeaks;
import com.squareup.picasso3.Utils.getLogIdsForHunter;
import com.squareup.picasso3.Utils.hasPermission;
import com.squareup.picasso3.Utils.isAirplaneModeOn;
import com.squareup.picasso3.Utils.log;
import com.squareup.picasso3.Utils.shouldWriteToMemoryCache;
import java.lang.ref.WeakHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class Dispatcher {
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
  private final DispatcherThread dispatcherThread;
  private final Handler handler;
  private final boolean scansNetworkChanges;

  public Dispatcher(
      Context context,
      ExecutorService service,
      Handler mainThreadHandler,
      PlatformLruCache cache) {
    this.context = context;
    this.service = service;
    this.mainThreadHandler = mainThreadHandler;
    this.cache = cache;
    hunterMap = new HashMap<>();
    failedActions = new WeakHashMap<>();
    pausedActions = new WeakHashMap<>();
    pausedTags = new HashSet<>();
    receiver = new NetworkBroadcastReceiver(this);
    airplaneMode = isAirplaneModeOn(context);
    dispatcherThread = new DispatcherThread();
    dispatcherThread.start();
    Looper looper = dispatcherThread.getLooper();
    flushStackLocalLeaks(looper);
    handler = new DispatcherHandler(looper, this);
    scansNetworkChanges = hasPermission(context, "android.permission.ACCESS_NETWORK_STATE");
    if (scansNetworkChanges) {
      ConnectivityManager connectivityManager =
          (ConnectivityManager)
              context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (connectivityManager != null) {
        connectivityManager.registerConnectivityManagerListener(
            new ConnectivityManagerListener() {
              @Override
              public void onAvailable(NetworkInfo networkInfo) {
                dispatchNetworkStateChange(networkInfo);
              }

              @Override
              public void onLost(NetworkInfo networkInfo) {
                dispatchNetworkStateChange(networkInfo);
              }
            });
      }
    }
    receiver.register();
  }

  public void shutdown() {
    if (service instanceof PicassoExecutorService) {
      PicassoExecutorService picassoExecutorService = (PicassoExecutorService) service;
      picassoExecutorService.shutdown();
    }
    dispatcherThread.quit();
    Picasso.HANDLER.post(
        new Runnable() {
          @Override
          public void run() {
            receiver.unregister();
          }
        });
  }

  public void dispatchSubmit(Action action) {
    handler.sendMessage(handler.obtainMessage(1, action));
  }

  public void dispatchCancel(Action action) {
    handler.sendMessage(handler.obtainMessage(2, action));
  }

  public void dispatchPauseTag(Object tag) {
    handler.sendMessage(handler.obtainMessage(11, tag));
  }

  public void dispatchResumeTag(Object tag) {
    handler.sendMessage(handler.obtainMessage(12, tag));
  }

  public void dispatchComplete(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(4, hunter));
  }

  public void dispatchRetry(BitmapHunter hunter) {
    handler.sendMessageDelayed(
        handler.obtainMessage(5, hunter), 500L); // RETRY_DELAY
  }

  public void dispatchFailed(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(6, hunter));
  }

  public void dispatchNetworkStateChange(NetworkInfo info) {
    handler.sendMessage(handler.obtainMessage(9, info));
  }

  public void dispatchAirplaneModeChange(boolean airplaneMode) {
    handler.sendMessage(
        handler.obtainMessage(
            10,
            airplaneMode ? 1 : 0,
            0)); // AIRPLANE_MODE_ON or AIRPLANE_MODE_OFF
  }

  public void performSubmit(Action action, boolean dismissFailed) {
    if (pausedTags.contains(action.tag)) {
      pausedActions.put(action.getTarget(), action);
      if (action.picasso.isLoggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_PAUSED,
            action.request.logId(),
            "because tag '" + action.tag + "' is paused");
      }
      return;
    }

    BitmapHunter hunter = hunterMap.get(action.request.key);
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

    hunter = Companion.forRequest(action.picasso, this, cache, action);
    hunter.future = service.submit(hunter);
    hunterMap.put(action.request.key, hunter);
    if (dismissFailed) {
      failedActions.remove(action.getTarget());
    }

    if (action.picasso.isLoggingEnabled) {
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
        if (action.picasso.isLoggingEnabled) {
          log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId());
        }
      }
    }

    if (action.tag != null && pausedTags.contains(action.tag)) {
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

  public void performPauseTag(Object tag) {
    if (!pausedTags.add(tag)) {
      return;
    }

    Iterator<BitmapHunter> iterator = hunterMap.values().iterator();
    while (iterator.hasNext()) {
      BitmapHunter hunter = iterator.next();
      boolean loggingEnabled = hunter.picasso.isLoggingEnabled;

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
              single.request.logId(),
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
      mainThreadHandler.sendMessage(
          mainThreadHandler.obtainMessage(13, batch)); // REQUEST_BATCH_RESUME
    }
  }

  public void performRetry(BitmapHunter hunter) {
    if (hunter.isCancelled) {
      return;
    }

    if (service.isShutdown()) {
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

      if (scansNetworkChanges && hunter.supportsReplay()) {
        markForReplay(hunter);
      }
    }
  }

  public void performComplete(BitmapHunter hunter) {
    if (shouldWriteToMemoryCache(hunter.data.memoryPolicy)) {
      Result result = hunter.result;
      if (result != null && result instanceof Bitmap) {
        Bitmap bitmap = (Bitmap) result;
        bitmap.prepareToDraw();
        cache.put(hunter.key, bitmap);
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

  public void performNetworkStateChange(NetworkInfo info) {
    if (info != null && info.isConnected()) {
      flushFailedActions();
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
      for (int i = joined.size() - 1; i >= 0; i--) {
        markForReplay(joined.get(i));
      }
    }
  }

  private void markForReplay(Action action) {
    Object target = action.getTarget();
    action.willReplay = true;
    failedActions.put(target, action);
  }

  private void deliver(BitmapHunter hunter) {
    if (hunter.isCancelled) {
      return;
    }
    Result result = hunter.result;
    if (result != null && result instanceof Bitmap) {
      Bitmap bitmap = (Bitmap) result;
      bitmap.prepareToDraw();
    }

    Message message = mainThreadHandler.obtainMessage(4, hunter);
    if (hunter.priority == Priority.HIGH) {
      mainThreadHandler.sendMessageAtFrontOfQueue(message);
    } else {
      mainThreadHandler.sendMessage(message);
    }
    logDelivery(hunter);
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

  private static class DispatcherHandler extends Handler {
    private final Dispatcher dispatcher;

    DispatcherHandler(Looper looper, Dispatcher dispatcher) {
      super(looper);
      this.dispatcher = dispatcher;
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case 1:
          Action action = (Action) msg.obj;
          dispatcher.performSubmit(action);
          break;
        case 2:
          action = (Action) msg.obj;
          dispatcher.performCancel(action);
          break;
        case 11:
          Object tag = msg.obj;
          dispatcher.performPauseTag(tag);
          break;
        case 12:
          tag = msg.obj;
          dispatcher.performResumeTag(tag);
          break;
        case 4:
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performComplete(hunter);
          break;
        case 5:
          hunter = (BitmapHunter) msg.obj;
          dispatcher.performRetry(hunter);
          break;
        case 6:
          hunter = (BitmapHunter) msg.obj;
          dispatcher.performError(hunter);
          break;
        case 9:
          NetworkInfo info = (NetworkInfo) msg.obj;
          dispatcher.performNetworkStateChange(info);
          break;
        case 10:
          int airplaneMode = msg.arg1;
          dispatcher.performAirplaneModeChange(airplaneMode == 1);
          break;
        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
      }
    }
  }

  private static class DispatcherThread extends HandlerThread {
    public DispatcherThread() {
      super("Dispatcher" + DISPATCHER_THREAD_NAME, Process.THREAD_PRIORITY_BACKGROUND);
    }
  }

  private static class NetworkBroadcastReceiver extends BroadcastReceiver {
    private final Dispatcher dispatcher;

    public NetworkBroadcastReceiver(Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    public void register() {
      IntentFilter filter = new IntentFilter();
      filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
      filter.addAction("android.intent.action.AIRPLANE_MODE");
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
      if ("android.intent.action.AIRPLANE_MODE".equals(action)) {
        if (!intent.hasExtra("state")) {
          return;
        }
        dispatcher.dispatchAirplaneModeChange(
            intent.getBooleanExtra("state", false));
      } else if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info =
            connectivityManager != null
                ? connectivityManager.getActiveNetworkInfo()
                : null;
        dispatcher.dispatchNetworkStateChange(info);
      }
    }
  }

  private static final String DISPATCHER_THREAD_NAME = "Dispatcher";
}