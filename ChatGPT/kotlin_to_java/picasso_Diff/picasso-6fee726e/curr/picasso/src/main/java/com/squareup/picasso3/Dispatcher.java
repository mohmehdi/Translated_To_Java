package com.squareup.picasso3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.CallSuper;
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
import com.squareup.picasso3.Utils.getLogIdsForHunter;
import com.squareup.picasso3.Utils.hasPermission;
import com.squareup.picasso3.Utils.isAirplaneModeOn;
import com.squareup.picasso3.Utils.log;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;

public abstract class Dispatcher {
    private final Context context;
    private final ExecutorService service;
    private final Handler mainThreadHandler;
    private final PlatformLruCache cache;
    private final WeakHashMap<Object, Action> failedActions = new WeakHashMap<>();
    private final WeakHashMap<Object, Action> pausedActions = new WeakHashMap<>();
    private final WeakHashMap<Object, Action> pausedTags = new WeakHashMap<>();
    private final NetworkBroadcastReceiver receiver;
    private final boolean airplaneMode;
    private final boolean scansNetworkChanges;

    private final WeakHashMap<String, BitmapHunter> hunterMap = new WeakHashMap<>();

    public Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler, PlatformLruCache cache) {
        this.context = context;
        this.service = service;
        this.mainThreadHandler = mainThreadHandler;
        this.cache = cache;
        this.airplaneMode = isAirplaneModeOn(context);
        this.scansNetworkChanges = hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
        this.receiver = new NetworkBroadcastReceiver(this);
        this.receiver.register();
    }

    @CallSuper
    public void shutdown() {
        if (service instanceof PicassoExecutorService) {
            ((PicassoExecutorService) service).shutdown();
        }

        Picasso.HANDLER.post(() -> receiver.unregister());
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

    public void performSubmit(Action action, boolean dismissFailed) {
        if (pausedTags.contains(action.tag)) {
            pausedActions.put(action.getTarget(), action);
            if (action.picasso.isLoggingEnabled) {
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
            if (action.picasso.isLoggingEnabled) {
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

        if (pausedTags.contains(action.tag)) {
            pausedActions.remove(action.getTarget());
            if (action.picasso.isLoggingEnabled) {
                log(OWNER_DISPATCHER, VERB_CANCELED, action.request.logId(), "because paused request got canceled");
            }
        }

        Action remove = failedActions.remove(action.getTarget());
        if (remove != null && remove.picasso.isLoggingEnabled) {
            log(OWNER_DISPATCHER, VERB_CANCELED, remove.request.logId(), "from replaying");
        }
    }

    public void performPauseTag(Object tag) {
        if (!pausedTags.add(tag)) {
            return;
        }

        for (BitmapHunter hunter : hunterMap.values()) {
            boolean loggingEnabled = hunter.picasso.isLoggingEnabled;

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
                    if (action.tag != tag) {
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

        List<Action> batch = new ArrayList<>();
        Iterator<Action> iterator = pausedActions.values().iterator();
        while (iterator.hasNext()) {
            Action action = iterator.next();
            if (action.tag == tag) {
                batch.add(action);
                iterator.remove();
            }
        }

        if (!batch.isEmpty()) {
            mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(REQUEST_BATCH_RESUME, batch));
        }
    }

    @SuppressLint("MissingPermission")
    public void performRetry(BitmapHunter hunter) {
        if (hunter.isCancelled()) return;

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
            if (hunter.picasso.isLoggingEnabled) {
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

    private void flushFailedActions() {
        if (!failedActions.isEmpty()) {
            Iterator<Action> iterator = failedActions.values().iterator();
            while (iterator.hasNext()) {
                Action action = iterator.next();
                iterator.remove();
                if (action.picasso.isLoggingEnabled) {
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
            for (Action joinedAction : joined) {
                markForReplay(joinedAction);
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
            log(OWNER_DISPATCHER, VERB_DELIVERED, getLogIdsForHunter(bitmapHunter));
        }
    }

    internal class NetworkBroadcastReceiver extends BroadcastReceiver {
        private final Dispatcher dispatcher;

        public NetworkBroadcastReceiver(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        public void register() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
            if (dispatcher.scansNetworkChanges) {
                filter.addAction(CONNECTIVITY_ACTION);
            }
            dispatcher.context.registerReceiver(this, filter);
        }

        public void unregister() {
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
                    ConnectivityManager connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager.class);
                    NetworkInfo networkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
                    if (networkInfo != null && networkInfo.isConnected()) {
                        dispatcher.dispatchNetworkStateChange(networkInfo);
                    }
                    break;
            }
        }

        internal static final String EXTRA_AIRPLANE_STATE = "state";
    }

    internal static final int RETRY_DELAY = 500L;
    internal static final int HUNTER_COMPLETE = 4;
    internal static final int NETWORK_STATE_CHANGE = 9;
    internal static final int REQUEST_BATCH_RESUME = 13;
}
