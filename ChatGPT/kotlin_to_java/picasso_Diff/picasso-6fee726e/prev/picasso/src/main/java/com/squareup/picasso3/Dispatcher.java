package com.squareup.picasso3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.BitmapHunter.Companion.forRequest;
import com.squareup.picasso3.MemoryPolicy.Companion.shouldWriteToMemoryCache;
import com.squareup.picasso3.NetworkPolicy.NO_CACHE;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.Picasso.Priority.HIGH;
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
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

internal class Dispatcher {
    private final Context context;
    @SuppressLint({"NewApi", "Deprecated"})
    private final ExecutorService service;
    private final Handler mainThreadHandler;
    private final PlatformLruCache cache;

    private final MutableMap<String, BitmapHunter> hunterMap = new LinkedHashMap<>();
    private final WeakHashMap<Object, Action> failedActions = new WeakHashMap<>();
    private final WeakHashMap<Object, Action> pausedActions = new WeakHashMap<>();
    private final Set<Object> pausedTags = new HashSet<>();
    private final NetworkBroadcastReceiver receiver;
    private boolean airplaneMode;

    private final DispatcherThread dispatcherThread;
    private final Handler handler;
    private final boolean scansNetworkChanges;

    Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler, PlatformLruCache cache) {
        this.context = context;
        this.service = service;
        this.mainThreadHandler = mainThreadHandler;
        this.cache = cache;
        this.dispatcherThread = new DispatcherThread();
        this.dispatcherThread.start();
        Looper dispatcherThreadLooper = this.dispatcherThread.getLooper();
        flushStackLocalLeaks(dispatcherThreadLooper);
        this.handler = new DispatcherHandler(dispatcherThreadLooper, this);
        this.scansNetworkChanges = hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
        this.receiver = new NetworkBroadcastReceiver(this);
        this.receiver.register();
    }

  public void shutdown() {
    if (service instanceof PicassoExecutorService) {
      ((PicassoExecutorService) service).shutdown();
    }
    dispatcherThread.quit();
    Picasso.HANDLER.post(() -> receiver.unregister());
  }

  public void dispatchSubmit(Action action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, action));
  }

  public void dispatchCancel(Action action) {
    handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, action));
  }

  public void dispatchPauseTag(Object tag) {
    handler.sendMessage(handler.obtainMessage(TAG_PAUSE, tag));
  }

  public void dispatchResumeTag(Object tag) {
    handler.sendMessage(handler.obtainMessage(TAG_RESUME, tag));
  }

  public void dispatchComplete(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_COMPLETE, hunter));
  }

  public void dispatchRetry(BitmapHunter hunter) {
    handler.sendMessageDelayed(handler.obtainMessage(HUNTER_RETRY, hunter), RETRY_DELAY);
  }

  public void dispatchFailed(BitmapHunter hunter) {
    handler.sendMessage(handler.obtainMessage(HUNTER_DECODE_FAILED, hunter));
  }

  public void dispatchNetworkStateChange(NetworkInfo info) {
    handler.sendMessage(handler.obtainMessage(NETWORK_STATE_CHANGE, info));
  }

  public void dispatchAirplaneModeChange(boolean airplaneMode) {
    handler.sendMessage(
        handler.obtainMessage(
            AIRPLANE_MODE_CHANGE,
            airplaneMode ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF,
            0
        )
    );
  }

  public void performSubmit(Action action) {
    performSubmit(action, true);
  }

  public void performCancel(Action action) {
    String key = action.request.key;
    BitmapHunter hunter = hunterMap.get(key);
    if (hunter != null) {
      hunter.detach(action);
      if (hunter.cancel()) {
        hunterMap.remove(key);
        log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId());
      }
    }

    if (pausedTags.contains(action.tag)) {
      pausedActions.remove(action.getTarget());
      log(
          OWNER_DISPATCHER,
          VERB_CANCELED,
          action.request.logId(),
          "because paused request got canceled"
      );
    }

    Action remove = failedActions.remove(action.getTarget());
    if (remove != null) {
      log(OWNER_DISPATCHER, VERB_CANCELED, remove.request.logId(), "from replaying");
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

      if (single != null && Objects.equals(single.tag, tag)) {
        hunter.detach(single);
        pausedActions.put(single.getTarget(), single);
        if (loggingEnabled) {
          log(
              OWNER_DISPATCHER,
              VERB_PAUSED,
              single.request.logId(),
              "because tag '" + tag + "' was paused"
          );
        }
      }

      if (joined != null) {
        for (int i = joined.size() - 1; i >= 0; i--) {
          Action action = joined.get(i);
          if (!Objects.equals(action.tag, tag)) {
            continue;
          }
          hunter.detach(action);
          pausedActions.put(action.getTarget(), action);
          if (loggingEnabled) {
            log(
                OWNER_DISPATCHER,
                VERB_PAUSED,
                action.request.logId(),
                "because tag '" + tag + "' was paused"
            );
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
              "all actions paused"
          );
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
      if (Objects.equals(action.tag, tag)) {
        batch.add(action);
        iterator.remove();
      }
    }

    if (!batch.isEmpty()) {
      mainThreadHandler.sendMessage(
          mainThreadHandler.obtainMessage(REQUEST_BATCH_RESUME, batch)
      );
    }
  }

  @SuppressLint("MissingPermission")
  public void performRetry(BitmapHunter hunter) {
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
          ContextCompat.getSystemService(context, ConnectivityManager.class);
      if (connectivityManager != null) {
        networkInfo = connectivityManager.getActiveNetworkInfo();
      }
    }

    if (hunter.shouldRetry(airplaneMode, networkInfo)) {
      if (hunter.picasso.isLoggingEnabled) {
        log(
            OWNER_DISPATCHER,
            VERB_RETRYING,
            getLogIdsForHunter(hunter)
        );
      }
      if (hunter.exception instanceof ContentLengthException) {
        hunter.data = hunter.data.newBuilder().networkPolicy(NO_CACHE).build();
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
        Bitmap bitmap = ((Bitmap) result).bitmap;
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
              action.request.logId()
          );
        }
        performSubmit(action, false);
      }
    }
  }

  private void markForReplay(BitmapHunter hunter) {
    Action action = hunter.action;
    if (action != null) {
      markForReplay(action);
    }
    List<Action> joined = hunter.actions;
    if (joined != null) {
      for (Action a : joined) {
        markForReplay(a);
      }
    }
  }

  private void markForReplay(Action action) {
    Object target = action.getTarget();
    action.willReplay = true;
    failedActions.put(target, action);
  }

  private void deliver(BitmapHunter hunter) {
    if (hunter.isCancelled()) {
      return;
    }
    Result result = hunter.result;
    if (result != null && result instanceof Bitmap) {
      Bitmap bitmap = ((Bitmap) result).bitmap;
      bitmap.prepareToDraw();
    }

    Message message = mainThreadHandler.obtainMessage(HUNTER_COMPLETE, hunter);
    if (hunter.priority == HIGH) {
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
          getLogIdsForHunter(bitmapHunter)
      );
    }
  }

  private static class DispatcherHandler extends Handler {
    private final Dispatcher dispatcher;

    public DispatcherHandler(Looper looper, Dispatcher dispatcher) {
      super(looper);
      this.dispatcher = dispatcher;
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case REQUEST_SUBMIT:
          Action submitAction = (Action) msg.obj;
          dispatcher.performSubmit(submitAction);
          break;
        case REQUEST_CANCEL:
          Action cancelAction = (Action) msg.obj;
          dispatcher.performCancel(cancelAction);
          break;
        case TAG_PAUSE:
          Object pauseTag = msg.obj;
          dispatcher.performPauseTag(pauseTag);
          break;
        case TAG_RESUME:
          Object resumeTag = msg.obj;
          dispatcher.performResumeTag(resumeTag);
          break;
        case HUNTER_COMPLETE:
          BitmapHunter completeHunter = (BitmapHunter) msg.obj;
          dispatcher.performComplete(completeHunter);
          break;
        case HUNTER_RETRY:
          BitmapHunter retryHunter = (BitmapHunter) msg.obj;
          dispatcher.performRetry(retryHunter);
          break;
        case HUNTER_DECODE_FAILED:
          BitmapHunter failedHunter = (BitmapHunter) msg.obj;
          dispatcher.performError(failedHunter);
          break;
        case NETWORK_STATE_CHANGE:
          NetworkInfo networkInfo = (NetworkInfo) msg.obj;
          dispatcher.performNetworkStateChange(networkInfo);
          break;
        case AIRPLANE_MODE_CHANGE:
          boolean airplaneMode = msg.arg1 == AIRPLANE_MODE_ON;
          dispatcher.performAirplaneModeChange(airplaneMode);
          break;
        default:
          Picasso.HANDLER.post(() ->
              throw new AssertionError("Unknown handler message received: " + msg.what)
          );
          break;
      }
    }
  }
    internal static class DispatcherThread extends HandlerThread {
        DispatcherThread() {
            super(Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
        }
    }

    internal class NetworkBroadcastReceiver extends BroadcastReceiver {
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
            dispatcher.context.registerReceiver(this, filter);
        }

        void unregister() {
            dispatcher.context.unregisterReceiver(this);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            switch (intent.getAction()) {
                case ACTION_AIRPLANE_MODE_CHANGED:
                    if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
                        return;
                    }
                    dispatcher.dispatchAirplaneModeChange(intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false));
                    break;
                case CONNECTIVITY_ACTION:
                    ConnectivityManager connectivityManager =
                            ContextCompat.getSystemService(context, ConnectivityManager.class);
                    NetworkInfo networkInfo;
                    try {
                        networkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
                    } catch (RuntimeException re) {
                        Log.w(TAG, "System UI crashed, ignoring attempt to change network state.");
                        return;
                    }
                    if (networkInfo == null) {
                        Log.w(TAG, "No default network is currently active, ignoring attempt to change network state.");
                        return;
                    }
                    dispatcher.dispatchNetworkStateChange(networkInfo);
                    break;
            }
        }

        internal static class EXTRA_AIRPLANE_STATE {
            static final String EXTRA_AIRPLANE_STATE = "state";
        }
    }

    internal static class Constants {
        static final long RETRY_DELAY = 500L;
        static final int AIRPLANE_MODE_ON = 1;
        static final int AIRPLANE_MODE_OFF = 0;
        static final int REQUEST_SUBMIT = 1;
        static final int REQUEST_CANCEL = 2;
        static final int HUNTER_COMPLETE = 4;
        static final int HUNTER_RETRY = 5;
        static final int HUNTER_DECODE_FAILED = 6;
        static final int NETWORK_STATE_CHANGE = 9;
        static final int AIRPLANE_MODE_CHANGE = 10;
        static final int TAG_PAUSE = 11;
        static final int TAG_RESUME = 12;
        static final int REQUEST_BATCH_RESUME = 13;
        static final String DISPATCHER_THREAD_NAME = "Dispatcher";
    }
}
