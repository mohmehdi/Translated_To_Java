package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import com.squareup.picasso3.Dispatcher.Companion;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Utils.OWNER_MAIN;
import com.squareup.picasso3.Utils.VERB_COMPLETED;
import com.squareup.picasso3.Utils.VERB_ERRORED;
import com.squareup.picasso3.Utils.VERB_RESUMED;
import com.squareup.picasso3.Utils.calculateDiskCacheSize;
import com.squareup.picasso3.Utils.calculateMemoryCacheSize;
import com.squareup.picasso3.Utils.checkMain;
import com.squareup.picasso3.Utils.createDefaultCacheDir;
import com.squareup.picasso3.Utils.log;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import okhttp3.Call;
import okhttp3.Call.Factory;
import okhttp3.OkHttpClient;

@SuppressWarnings("unused")
public class Picasso implements LifecycleObserver {

  private final Context context;
  private final Dispatcher dispatcher;
  private final Factory callFactory;
  private final Cache closeableCache;
  private final PlatformLruCache cache;
  private final Listener listener;
  private final List requestTransformers;
  private final List requestHandlers;
  private final List eventListeners;
  private final Config defaultBitmapConfig;
  private final boolean indicatorsEnabled;
  private final boolean isLoggingEnabled;
  private final Map < Object, Action > targetToAction;
  private final Map < ImageView, DeferredRequestCreator > targetToDeferredRequestCreator;
  private boolean shutdown;

  public Picasso(Context context, Dispatcher dispatcher, Factory callFactory, Cache closeableCache,
    PlatformLruCache cache, Listener listener, List requestTransformers,
    List requestHandlers, List eventListeners, Config defaultBitmapConfig,
    boolean indicatorsEnabled, boolean isLoggingEnabled) {
    this.context = context.getApplicationContext();
    this.dispatcher = dispatcher;
    this.callFactory = callFactory;
    this.closeableCache = closeableCache;
    this.cache = cache;
    this.listener = listener;
    this.requestTransformers = Collections.unmodifiableList(requestTransformers);
    this.requestHandlers = Collections.unmodifiableList(requestHandlers);
    this.eventListeners = Collections.unmodifiableList(eventListeners);
    this.defaultBitmapConfig = defaultBitmapConfig;
    this.indicatorsEnabled = indicatorsEnabled;
    this.isLoggingEnabled = isLoggingEnabled;
    this.targetToAction = new WeakHashMap < > ();
    this.targetToDeferredRequestCreator = new WeakHashMap < > ();
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  private void cancelAll() {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      Object target = action.getTarget();
      if (target == null) {
        continue;
      }
      cancelExistingRequest(target);
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      deferredRequestCreators.get(i).cancel();
    }
  }

  public void cancelRequest(ImageView view) {
    cancelExistingRequest(view);
  }

  public void cancelRequest(BitmapTarget target) {
    cancelExistingRequest(target);
  }

  public void cancelRequest(DrawableTarget target) {
    cancelExistingRequest(target);
  }

  public void cancelRequest(RemoteViews remoteViews, @IdRes int viewId) {
    cancelExistingRequest(new RemoteViewsTarget(remoteViews, viewId));
  }

  public void cancelTag(Object tag) {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      if (tag == action.tag) {
        Object target = action.getTarget();
        if (target == null) {
          continue;
        }
        cancelExistingRequest(target);
      }
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (tag == deferredRequestCreator.tag) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  private void pauseAll() {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchPauseTag(actions.get(i).tag);
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      Object tag = deferredRequestCreator.tag;
      if (tag != null) {
        dispatcher.dispatchPauseTag(tag);
      }
    }
  }

  public void pauseTag(Object tag) {
    dispatcher.dispatchPauseTag(tag);
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  private void resumeAll() {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchResumeTag(actions.get(i).tag);
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      Object tag = deferredRequestCreator.tag;
      if (tag != null) {
        dispatcher.dispatchResumeTag(tag);
      }
    }
  }

  public void resumeTag(Object tag) {
    dispatcher.dispatchResumeTag(tag);
  }

  public RequestCreator load(Uri uri) {
    return new RequestCreator(this, uri, 0);
  }

  public RequestCreator load(String path) {
    if (path == null) {
      return new RequestCreator(this, null, 0);
    }
    require(path.length() > 0, "Path must not be empty.");
    return load(Uri.parse(path));
  }

  public RequestCreator load(File file) {
    return file == null ?
      new RequestCreator(this, null, 0) :
      new RequestCreator(this, Uri.fromFile(file), 0);
  }

  @SuppressWarnings("SameParameterValue")
  public RequestCreator load(@DrawableRes int resourceId) {
    require(resourceId != 0, "Resource ID must not be zero.");
    return new RequestCreator(this, null, resourceId);
  }

  public void evictAll() {
    cache.clear();
  }

  public void invalidate(Uri uri) {
    if (uri != null) {
      cache.clearKeyUri(uri.toString());
    }
  }

  public void invalidate(String path) {
    if (path != null) {
      invalidate(Uri.parse(path));
    }
  }

  public void invalidate(File file) {
    invalidate(Uri.fromFile(file));
  }

  public void shutdown() {
    if (shutdown) {
      return;
    }
    cache.clear();

    close();

    dispatcher.shutdown();
    try {
      closeableCache.close();
    } catch (IOException ignored) {}
    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      deferredRequestCreators.get(i).cancel();
    }
    targetToAction.clear();
    targetToDeferredRequestCreator.clear();
    shutdown = true;
  }

  private Request transformRequest(Request request) {
    List requestTransformers = new ArrayList < > (this.requestTransformers);
    Request nextRequest = request;
    for (int i = 0; i < requestTransformers.size(); i++) {
      RequestTransformer transformer = requestTransformers.get(i);
      nextRequest = transformer.transformRequest(nextRequest);
    }
    return nextRequest;
  }

  private void defer(ImageView view, DeferredRequestCreator request) {
    if (targetToDeferredRequestCreator.containsKey(view)) {
      cancelExistingRequest(view);
    }
    targetToDeferredRequestCreator.put(view, request);
  }

  private void enqueueAndSubmit(Action action) {
    Object target = action.getTarget();
    if (target == null) {
      return;
    }
    Action existingAction = targetToAction.get(target);
    if (existingAction != action) {
      cancelExistingRequest(target);
      targetToAction.put(target, action);
    }
    submit(action);
  }

  private void submit(Action action) {
    dispatcher.dispatchSubmit(action);
  }

  private Bitmap quickMemoryCacheCheck(String key) {
    Bitmap cached = cache.get(key);
    if (cached != null) {
      cacheHit();
    } else {
      cacheMiss();
    }
    return cached;
  }

  private void complete(BitmapHunter hunter) {
    List single = hunter.actions;
    List joined = hunter.actions;

    boolean hasMultiple = joined != null && !joined.isEmpty();
    boolean shouldDeliver = single != null || hasMultiple;

    if (!shouldDeliver) {
      return;
    }

    Exception exception = hunter.exception;
    Result result = hunter.result;

    if (single != null) {
      deliverAction(result, single, exception);
    }

    if (joined != null) {
      for (int i = 0; i < joined.size(); i++) {
        deliverAction(result, joined.get(i), exception);
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, hunter.data.uri, exception);
    }
  }

  private void resumeAction(Action action) {
    Bitmap bitmap = action.request.memoryPolicy == MemoryPolicy.NO_CACHE ?
      null :
      quickMemoryCacheCheck(action.request.key);

    if (bitmap != null) {
      deliverAction(Result.bitmap(bitmap, LoadedFrom.MEMORY), action, null);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_COMPLETED,
          action.request.logId(),
          "from " + LoadedFrom.MEMORY);
      }
    } else {
      enqueueAndSubmit(action);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_RESUMED,
          action.request.logId());
      }
    }
  }

  private void deliverAction(Result < ? > result, Action action, Exception e) {
    if (action.cancelled) {
      return;
    }
    if (!action.willReplay) {
      targetToAction.remove(action.getTarget());
    }
    if (result != null) {
      action.complete(result);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_COMPLETED,
          action.request.logId(),
          "from " + result.loadedFrom);
      }
    } else if (e != null) {
      action.error(e);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_ERRORED,
          action.request.logId(),
          e.getMessage());
      }
    }
  }

  private void cancelExistingRequest(Object target) {
    checkMain();
    Action action = targetToAction.remove(target);
    if (action != null) {
      action.cancel();
      dispatcher.dispatchCancel(action);
    }
    if (target instanceof ImageView) {
      DeferredRequestCreator deferredRequestCreator = targetToDeferredRequestCreator.remove(target);
      deferredRequestCreator.cancel();
    }
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  /** Fluent API for creating [Picasso] instances. */
  public static class Builder {
    private final Context context;
    private Factory callFactory;
    private ExecutorService service;
    private PlatformLruCache cache;
    private Listener listener;
    private final List requestTransformers = new ArrayList < > ();
    private final List requestHandlers = new ArrayList < > ();
    private final List eventListeners = new ArrayList < > ();
    private Config defaultBitmapConfig;
    private boolean indicatorsEnabled;
    private boolean loggingEnabled;

    public Builder(Context context) {
      this.context = context.getApplicationContext();
    }

    public Builder(Picasso picasso) {
      this.context = picasso.context;
      this.callFactory = picasso.callFactory;
      this.service = picasso.dispatcher.service;
      this.cache = picasso.cache;
      this.listener = picasso.listener;
      this.requestTransformers.addAll(picasso.requestTransformers);
      this.requestHandlers.addAll(picasso.requestHandlers.subList(2, picasso.requestHandlers.size() - 6));
      this.eventListeners.addAll(picasso.eventListeners);
      this.defaultBitmapConfig = picasso.defaultBitmapConfig;
      this.indicatorsEnabled = picasso.indicatorsEnabled;
      this.loggingEnabled = picasso.isLoggingEnabled;
    }

    public Builder defaultBitmapConfig(Config bitmapConfig) {
      this.defaultBitmapConfig = bitmapConfig;
      return this;
    }

    public Builder client(OkHttpClient client) {
      this.callFactory = client;
      return this;
    }

    public Builder callFactory(Factory factory) {
      this.callFactory = factory;
      return this;
    }

    public Builder executor(ExecutorService executorService) {
      this.service = executorService;
      return this;
    }

    public Builder withCacheSize(int maxByteCount) {
      require(maxByteCount >= 0, "maxByteCount < 0: " + maxByteCount);
      this.cache = new PlatformLruCache(maxByteCount);
      return this;
    }

    public Builder listener(Listener listener) {
      this.listener = listener;
      return this;
    }

    public Builder addRequestTransformer(RequestTransformer transformer) {
      this.requestTransformers.add(transformer);
      return this;
    }

    public Builder addRequestHandler(RequestHandler requestHandler) {
      this.requestHandlers.add(requestHandler);
      return this;
    }

    public Builder addEventListener(EventListener eventListener) {
      this.eventListeners.add(eventListener);
      return this;
    }

    public Builder indicatorsEnabled(boolean enabled) {
      this.indicatorsEnabled = enabled;
      return this;
    }

    public Builder loggingEnabled(boolean enabled) {
      this.loggingEnabled = enabled;
      return this;
    }

    public Picasso build() {
      ExecutorService service;
      if (callFactory == null) {
        File cacheDir = createDefaultCacheDir(context);
        int maxSize = (int) calculateDiskCacheSize(cacheDir);
        OkHttpClient unsharedCache = new OkHttpClient.Builder()
          .cache(new okhttp3.Cache(cacheDir, maxSize))
          .build();
        callFactory = unsharedCache;
      }
      if (cache == null) {
        cache = new PlatformLruCache((int) calculateMemoryCacheSize(context));
      }
      if (service == null) {
        service = new PicassoExecutorService();
      }

      Dispatcher dispatcher = new HandlerDispatcher(context, service, Companion.HUNTER_COMPLETE, cache);

      return new Picasso(
        context, dispatcher, callFactory, closeableCache, cache, listener,
        requestTransformers, requestHandlers, eventListeners, defaultBitmapConfig,
        indicatorsEnabled, loggingEnabled
      );
    }
  }

  private void cacheMaxSize(int maxSize) {
    for (EventListener eventListener: eventListeners) {
      eventListener.cacheMaxSize(maxSize);
    }
  }

  private void cacheSize(int size) {
    for (EventListener eventListener: eventListeners) {
      eventListener.cacheSize(size);
    }
  }

  private void cacheHit() {
    for (EventListener eventListener: eventListeners) {
      eventListener.cacheHit();
    }
  }

  private void cacheMiss() {
    for (EventListener eventListener: eventListeners) {
      eventListener.cacheMiss();
    }
  }

  private void downloadFinished(long size) {
    for (EventListener eventListener: eventListeners) {
      eventListener.downloadFinished(size);
    }
  }

  private void bitmapDecoded(Bitmap bitmap) {
    for (EventListener eventListener: eventListeners) {
      eventListener.bitmapDecoded(bitmap);
    }
  }

  private void bitmapTransformed(Bitmap bitmap) {
    for (EventListener eventListener: eventListeners) {
      eventListener.bitmapTransformed(bitmap);
    }
  }

  private void close() {
    for (EventListener eventListener: eventListeners) {
      eventListener.close();
    }
  }

  public interface Listener {
    /**

    Invoked when an image has failed to load. This is useful for reporting image failures to a
    remote analytics service, for example.
    */
    void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception);
  }
  public interface RequestTransformer {
    /**

    Transform a request before it is submitted to be processed.
    @return The original request or a new request to replace it. Must not be null.
    */
    Request transformRequest(Request request);
  }
  public enum Priority {
    LOW,
    NORMAL,
    HIGH
  }

  public enum LoadedFrom {
    MEMORY,
    DISK,
    NETWORK;

    public final int debugColor;

    LoadedFrom() {
      this.debugColor = Color.argb(255, 255, 255, 255);
    }

    LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }




  
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case HUNTER_COMPLETE:
                BitmapHunter hunter = (BitmapHunter) msg.obj;
                hunter.picasso.complete(hunter);
                break;
            case REQUEST_BATCH_RESUME:
                List<Action> batch = (List<Action>) msg.obj;
                for (int i = 0; i < batch.size(); i++) {
                    Action action = batch.get(i);
                    action.picasso.resumeAction(action);
                }
                break;
            default:
                throw new AssertionError("Unknown handler message received: " + msg.what);
        }
    }
}