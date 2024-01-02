package com.squareup.picasso3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.BitmapHunter.Companion;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.NetworkPolicy;
import com.squareup.picasso3.NetworkRequestHandler.ContentLengthException;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;
import com.squareup.picasso3.Utils;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

internal abstract class Dispatcher {
  private final Context context;
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-service")
  internal final ExecutorService service;
  private final Handler mainThreadHandler;
  private final PlatformLruCache cache;
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-hunterMap")
  internal final MutableMap<String, BitmapHunter> hunterMap = new HashMap<>();
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-failedActions")
  internal final WeakHashMap<Object, Action> failedActions = new WeakHashMap<>();
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-pausedActions")
  internal final WeakHashMap<Object, Action> pausedActions = new WeakHashMap<>();
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-pausedTags")
  internal final Set<Object> pausedTags = new HashSet<>();
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-receiver")
  internal final NetworkBroadcastReceiver receiver;
  @SuppressWarnings("WeakerAccess")
  @get:JvmName("-airplaneMode")
  @set:JvmName("-airplaneMode")
  internal boolean airplaneMode = Utils.isAirplaneModeOn(context);
  private final boolean scansNetworkChanges;

  protected Dispatcher(Context context, @SuppressWarnings("WeakerAccess") @get:JvmName("-service") ExecutorService service,
                       Handler mainThreadHandler, PlatformLruCache cache) {
    this.context = context;
    this.service = service;
    this.mainThreadHandler = mainThreadHandler;
    this.cache = cache;
    this.scansNetworkChanges = Utils.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
    this.receiver = new NetworkBroadcastReceiver(this);
    this.receiver.register();
  }

    @CallSuper
    public void shutdown() {
        if (service instanceof PicassoExecutorService) {
            ((PicassoExecutorService) service).shutdown();
        }
        mainThreadHandler.post(() -> receiver.unregister());
    }

    public abstract void dispatchSubmit(Action action);

    public abstract void dispatchCancel(Action action);

    public abstract void dispatchPauseTag(Object tag);

    public abstract void dispatchResumeTag(Object tag);

    public abstract void dispatchComplete(BitmapHunter hunter);

    public abstract void dispatchRetry(BitmapHunter hunter);

    public abstract void dispatchFailed(BitmapHunter hunter);

    public abstract void dispatchNetworkStateChange(NetworkInfo info);

    public abstract void dispatchAirplaneModeChange(boolean airplaneMode);

    public abstract void dispatchCompleteMain(BitmapHunter hunter);

    public abstract void dispatchBatchResumeMain(List<Action> batch);

    public void performSubmit(Action action, boolean dismissFailed) {
        if (pausedTags.contains(action.tag)) {
            pausedActions.put(action.getTarget(), action);
            if (action.picasso.isLoggingEnabled()) {
                log(OWNER_DISPATCHER, VERB_PAUSED, action.request.logId(), "because tag '" + action.tag + "' is paused");
            }
            return;
        }

        BitmapHunter hunter = hunterMap.get(action.request.key);
        if (hunter != null) {
            hunter.attach(action);
            return;
        }

        if (service.isShutdown()) {
            if (action.picasso.isLoggingEnabled()) {
                log(OWNER_DISPATCHER, VERB_IGNORED, action.request.logId(), "because shut down");
            }
            return;
        }

        hunter = forRequest(action.picasso, this, cache, action);
        hunter.future = service.submit(hunter);
        hunterMap.put(action.request.key, hunter);
        if (dismissFailed) {
            failedActions.remove(action.getTarget());
        }

        if (action.picasso.isLoggingEnabled()) {
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
                if (action.picasso.isLoggingEnabled()) {
                    log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId());
                }
            }
        }

        if (pausedTags.contains(action.tag)) {
            pausedActions.remove(action.getTarget());
            if (action.picasso.isLoggingEnabled()) {
                log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId(), "because paused request got canceled");
            }
        }

        Action remove = failedActions.remove(action.getTarget());
        if (remove != null && remove.picasso.isLoggingEnabled()) {
            log(OWNER_DISPATCHER, VERB_CANCELED, remove.request.logId(), "from replaying");
        }
    }

    public void performPauseTag(Object tag) {
        if (!pausedTags.add(tag)) {
            return;
        }

        for (BitmapHunter hunter : hunterMap.values()) {
            boolean loggingEnabled = hunter.picasso.isLoggingEnabled();

            Action single = hunter.action;
            List<Action> joined = hunter.actions;
            boolean hasMultiple = joined != null && !joined.isEmpty();

            if (single == null && !hasMultiple) {
                continue;
            }

            if (single != null && single.tag == tag) {
                hunter.detach(single);
                pausedActions.put(single.getTarget(), single);
                if (loggingEnabled) {
                    log(OWNER_DISPATCHER, VERB_PAUSED, single.request.logId(), "because tag '" + tag + "' was paused");
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
                        log(OWNER_DISPATCHER, VERB_PAUSED, action.request.logId(), "because tag '" + tag + "' was paused");
                    }
                }
            }

            if (hunter.cancel()) {
                hunterMap.remove(hunter);
                if (loggingEnabled) {
                    log(OWNER_DISPATCHER, VERB_CANCELED, getLogIdsForHunter(hunter), "all actions paused");
                }
            }
        }
    }

    public void performResumeTag(Object tag) {
        if (!pausedTags.remove(tag)) {
            return;
        }

        List<Action> batch = new java.util.ArrayList<>();
        for (Action action : pausedActions.values()) {
            if (action.tag.equals(tag)) {
                batch.add(action);
                pausedActions.remove(action.getTarget());
            }
        }

        if (!batch.isEmpty()) {
            dispatchBatchResumeMain(batch);
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
            ConnectivityManager connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager.class);
            if (connectivityManager != null) {
                networkInfo = connectivityManager.getActiveNetworkInfo();
            }
        }

        if (hunter.shouldRetry(airplaneMode, networkInfo)) {
            if (hunter.picasso.isLoggingEnabled()) {
                log(OWNER_DISPATCHER, VERB_RETRYING, getLogIdsForHunter(hunter));
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

    @MainThread
    public void performCompleteMain(BitmapHunter hunter) {
        hunter.picasso.complete(hunter);
    }

    @MainThread
    public void performBatchResumeMain(List<Action> batch) {
        for (Action action : batch) {
            action.picasso.resumeAction(action);
        }
    }

    private void flushFailedActions() {
        if (!failedActions.isEmpty()) {
            for (Action action : failedActions.values()) {
                if (action.picasso.isLoggingEnabled()) {
                    log(OWNER_DISPATCHER, VERB_REPLAYING, action.request.logId());
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

        dispatchCompleteMain(hunter);

        logDelivery(hunter);
    }

    private void logDelivery(BitmapHunter bitmapHunter) {
        Picasso picasso = bitmapHunter.picasso;
        if (picasso.isLoggingEnabled()) {
            log(OWNER_DISPATCHER, VERB_DELIVERED, getLogIdsForHunter(bitmapHunter));
        }
    }

  internal class NetworkBroadcastReceiver extends BroadcastReceiver {
    private final Dispatcher dispatcher;

    public NetworkBroadcastReceiver(Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    void register() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
      if (dispatcher.scansNetworkChanges) {
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
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
        case Intent.ACTION_AIRPLANE_MODE_CHANGED:
          if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
            return;
          }
          dispatcher.dispatchAirplaneModeChange(intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false));
          break;
        case ConnectivityManager.CONNECTIVITY_ACTION:
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

    internal static final String EXTRA_AIRPLANE_STATE = "state";
  }

  internal static final long RETRY_DELAY = 500L;
  internal static final int HUNTER_COMPLETE = 4;
  internal static final int NETWORK_STATE_CHANGE = 9;
  internal static final int REQUEST_BATCH_RESUME = 13;
}
