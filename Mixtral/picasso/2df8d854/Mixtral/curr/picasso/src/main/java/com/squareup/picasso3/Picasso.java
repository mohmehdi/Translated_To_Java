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
import com.squareup.picasso3.MemoryPolicy.Companion;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;

@SuppressWarnings("ExperimentalTypeInference")
public class Picasso implements LifecycleObserver {
  private final Context context;
  private final Dispatcher dispatcher;
  private final Call.Factory callFactory;
  private final Cache closeableCache;
  private final PlatformLruCache cache;
  private final Listener listener;
  private final List<RequestTransformer> requestTransformers;
  private final List<RequestHandler> requestHandlers;
  private final List<EventListener> eventListeners;
  private final Config defaultBitmapConfig;
  private boolean indicatorsEnabled;
  private boolean isLoggingEnabled;
  private final Map<Object, Action> targetToAction = new HashMap<>();
  private final Map<ImageView, DeferredRequestCreator> targetToDeferredRequestCreator = new HashMap<>();
  private boolean shutdown;

  public Picasso(Context context, Dispatcher dispatcher, Call.Factory callFactory, Cache closeableCache,
      PlatformLruCache cache, Listener listener, List<RequestTransformer> requestTransformers,
      List<RequestHandler> extraRequestHandlers, List<EventListener> eventListeners, Config defaultBitmapConfig,
      boolean indicatorsEnabled, boolean isLoggingEnabled) {
    this.context = context;
    this.dispatcher = dispatcher;
    this.callFactory = callFactory;
    this.closeableCache = closeableCache;
    this.cache = cache;
    this.listener = listener;
    this.requestTransformers = new ArrayList<>(requestTransformers);
    int builtInHandlers = 8;
    this.requestHandlers = new ArrayList<>(builtInHandlers + extraRequestHandlers.size);
    this.requestHandlers.add(ResourceDrawableRequestHandler.create(context));
    this.requestHandlers.add(ResourceRequestHandler.create(context));
    this.requestHandlers.addAll(extraRequestHandlers);
    this.requestHandlers.add(ContactsPhotoRequestHandler.create(context));
    this.requestHandlers.add(MediaStoreRequestHandler.create(context));
    this.requestHandlers.add(ContentStreamRequestHandler.create(context));
    this.requestHandlers.add(AssetRequestHandler.create(context));
    this.requestHandlers.add(FileRequestHandler.create(context));
    this.requestHandlers.add(NetworkRequestHandler.create(callFactory));
    this.eventListeners = new ArrayList<>(eventListeners);
    this.defaultBitmapConfig = defaultBitmapConfig;
    this.indicatorsEnabled = indicatorsEnabled;
    this.isLoggingEnabled = isLoggingEnabled;
  }

  @OnLifecycleEvent(Event.ON_DESTROY)
  public void cancelAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      cancelExistingRequest(actions.get(i).getTarget());
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
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

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      if (tag == action.tag) {
        cancelExistingRequest(action.getTarget());
      }
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (tag == deferredRequestCreator.tag) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(Event.ON_STOP)
  public void pauseAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchPauseTag(actions.get(i).tag);
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (deferredRequestCreator.tag != null) {
        dispatcher.dispatchPauseTag(deferredRequestCreator.tag);
      }
    }
  }

  public void pauseTag(Object tag) {
    dispatcher.dispatchPauseTag(tag);
  }

  @OnLifecycleEvent(Event.ON_START)
  public void resumeAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchResumeTag(actions.get(i).tag);
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (deferredRequestCreator.tag != null) {
        dispatcher.dispatchResumeTag(deferredRequestCreator.tag);
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
    return file == null ? new RequestCreator(this, null, 0) : load(Uri.fromFile(file));
  }

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
      if (closeableCache != null) {
        closeableCache.close();
      }
    } catch (IOException ignored) {
    }
    for (DeferredRequestCreator deferredRequestCreator : targetToDeferredRequestCreator.values()) {
      deferredRequestCreator.cancel();
    }
    targetToDeferredRequestCreator.clear();
    shutdown = true;
  }

  public RequestCreator transformRequest(Request request) {
    Request nextRequest = request;
    for (int i = 0; i < requestTransformers.size(); i++) {
      RequestTransformer transformer = requestTransformers.get(i);
      nextRequest = transformer.transformRequest(nextRequest);
    }
    return new RequestCreator(this, nextRequest, 0);
  }

  public void defer(ImageView view, DeferredRequestCreator request) {
    if (targetToDeferredRequestCreator.containsKey(view)) {
      cancelExistingRequest(view);
    }
    targetToDeferredRequestCreator.put(view, request);
  }

  public void enqueueAndSubmit(Action action) {
    Object target = action.getTarget();
    if (targetToAction.get(target) != action) {
      cancelExistingRequest(target);
      targetToAction.put(target, action);
    }
    submit(action);
  }

  public void submit(Action action) {
    dispatcher.dispatchSubmit(action);
  }

  public Bitmap quickMemoryCacheCheck(String key) {
    Bitmap cached = cache.get(key);
    if (cached != null) {
      cacheHit();
    } else {
      cacheMiss();
    }
    return cached;
  }

  public void complete(BitmapHunter hunter) {
    List<Action> joined = hunter.actions;
    boolean hasMultiple = joined != null && !joined.isEmpty();
    boolean shouldDeliver = hunter.action != null || hasMultiple;

    if (!shouldDeliver) {
      return;
    }

    Exception exception = hunter.exception;
    Result result = hunter.result;

    if (hunter.action != null) {
      deliverAction(result, hunter.action, exception);
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

  public void resumeAction(Action action) {
    Bitmap bitmap = shouldReadFromMemoryCache(action.request.memoryPolicy)
        ? quickMemoryCacheCheck(action.request.key) : null;

    if (bitmap != null) {
      deliverAction(new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action, null);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.request.logId(), "from " + LoadedFrom.MEMORY);
      }
    } else {
      enqueueAndSubmit(action);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_RESUMED, action.request.logId());
      }
    }
  }

  private void deliverAction(Result result, Action action, Exception e) {
    if (action.cancelled) {
      return;
    }
    if (!action.willReplay) {
      targetToAction.remove(action.getTarget());
    }
    if (result != null) {
      action.complete(result);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.request.logId(), "from " + result.loadedFrom);
      }
    } else if (e != null) {
      action.error(e);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_ERRORED, action.request.logId(), e.getMessage());
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

  public void cacheMaxSize(int maxSize) {
    for (EventListener eventListener : eventListeners) {
      eventListener.cacheMaxSize(maxSize);
    }
  }

  public void cacheSize(int size) {
    for (EventListener eventListener : eventListeners) {
      eventListener.cacheSize(size);
    }
  }

  public void cacheHit() {
    for (EventListener eventListener : eventListeners) {
      eventListener.cacheHit();
    }
  }

  public void cacheMiss() {
    for (EventListener eventListener : eventListeners) {
      eventListener.cacheMiss();
    }
  }

  public void downloadFinished(long size) {
    for (EventListener eventListener : eventListeners) {
      eventListener.downloadFinished(size);
    }
  }

  public void bitmapDecoded(Bitmap bitmap) {
    for (EventListener eventListener : eventListeners) {
      eventListener.bitmapDecoded(bitmap);
    }
  }

  public void bitmapTransformed(Bitmap bitmap) {
    for (EventListener eventListener : eventListeners) {
      eventListener.bitmapTransformed(bitmap);
    }
  }

  public void close() {
    for (EventListener eventListener : eventListeners) {
      eventListener.close();
    }
  }


  @FunctionalInterface
public interface Listener {
    /**
     * Invoked when an image has failed to load. This is useful for reporting image failures to a
     * remote analytics service, for example.
     */
    void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception);
}
@FunctionalInterface
public interface RequestTransformer {
    /**
     * Transform a request before it is submitted to be processed.
     *
     * @return The original request or a new request to replace it. Must not be null.
     */
    Request transformRequest(Request request);
}


public enum Priority {
    LOW,
    NORMAL,
    HIGH
}



public enum LoadedFrom {
    MEMORY(Color.GREEN.getRGB()),
    DISK(Color.BLUE.getRGB()),
    NETWORK(Color.RED.getRGB());

    private final int debugColor;

    LoadedFrom(int debugColor) {
        this.debugColor = debugColor;
    }

    @SuppressWarnings("unused")  // for the -debugColor JVM syntax
    public int getDebugColor() {
        return debugColor;
    }
}



        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HUNTER_COMPLETE: {
                    BitmapHunter hunter = (BitmapHunter) msg.obj;
                    hunter.picasso.complete(hunter);
                    break;
                }
                case REQUEST_BATCH_RESUME: {
                    List<Action> batch = (List<Action>) msg.obj;
                    for (int i = 0; i < batch.size(); i++) {
                        Action action = batch.get(i);
                        action.picasso.resumeAction(action);
                    }
                    break;
                }
                default:
                    throw new AssertionError("Unknown handler message received: " + msg.what);
            }
        }

    private static final int HUNTER_COMPLETE = 1;
    private static final int REQUEST_BATCH_RESUME = 2;

    private static final MyHandler HANDLER = new MyHandler(Looper.getMainLooper());





public class Builder {
  private final Context context;
  private Call.Factory callFactory;
  private ExecutorService service;
  private PlatformLruCache cache;
  private Listener listener;
  private final List<RequestTransformer> requestTransformers = new ArrayList<>();
  private final List<RequestHandler> requestHandlers = new ArrayList<>();
  private final List<EventListener> eventListeners = new ArrayList<>();
  private Config defaultBitmapConfig;
  private boolean indicatorsEnabled;
  private boolean loggingEnabled;

  /**
   * Start building a new Picasso instance.
   */
  public Builder(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Internal constructor for creating a Builder from an existing Picasso instance.
   */
  Builder(@NonNull Picasso picasso) {
    context = picasso.context;
    callFactory = picasso.callFactory;
    service = picasso.dispatcher.service;
    cache = picasso.cache;
    listener = picasso.listener;
    requestTransformers.addAll(picasso.requestTransformers);

    int numRequestHandlers = picasso.requestHandlers.size();
    requestHandlers.addAll(picasso.requestHandlers.subList(2, numRequestHandlers - 6));
    eventListeners.addAll(picasso.eventListeners);

    defaultBitmapConfig = picasso.defaultBitmapConfig;
    indicatorsEnabled = picasso.indicatorsEnabled;
    loggingEnabled = picasso.isLoggingEnabled();
  }

  /**
   * Sets the default bitmap configuration to use when decoding bitmaps.
   */
  public Builder defaultBitmapConfig(@NonNull Config config) {
    defaultBitmapConfig = config;
    return this;
  }

  /**
   * Sets the OkHttpClient to use for making network requests.
   */
  public Builder client(@NonNull OkHttpClient client) {
    callFactory = client;
    return this;
  }

  /**
   * Sets the Call.Factory to use for making network requests.
   */
  public Builder callFactory(@NonNull Call.Factory factory) {
    callFactory = factory;
    return this;
  }

  /**
   * Sets the ExecutorService to use for running tasks.
   */
  public Builder executor(@NonNull ExecutorService executorService) {
    service = executorService;
    return this;
  }

  /**
   * Sets the maximum size of the in-memory cache.
   */
  public Builder withCacheSize(int maxByteCount) {
    require(maxByteCount >= 0);
    cache = PlatformLruCache.create(maxByteCount);
    return this;
  }

  /**
   * Sets the listener to be notified of Picasso events.
   */
  public Builder listener(@NonNull Listener listener) {
    this.listener = listener;
    return this;
  }

  /**
   * Adds a request transformer to the chain.
   */
  public Builder addRequestTransformer(@NonNull RequestTransformer transformer) {
    requestTransformers.add(transformer);
    return this;
  }

  /**
   * Adds a request handler to the chain.
   */
  public Builder addRequestHandler(@NonNull RequestHandler requestHandler) {
    requestHandlers.add(requestHandler);
    return this;
  }

  /**
   * Adds an event listener to the chain.
   */
  public Builder addEventListener(@NonNull EventListener eventListener) {
    eventListeners.add(eventListener);
    return this;
  }

  /**
   * Enables or disables display of download indicators.
   */
  public Builder indicatorsEnabled(boolean enabled) {
    indicatorsEnabled = enabled;
    return this;
  }

  /**
   * Enables or disables logging.
   */
  public Builder loggingEnabled(boolean enabled) {
    loggingEnabled = enabled;
    return this;
  }

  /**
   * Builds a new Picasso instance.
   */
  @NonNull
  public Picasso build() {
    OkHttpClient client = callFactory != null ? callFactory.build() : newBuilder().build();
    Cache unsharedCache = null;
    if (callFactory == null) {
      File cacheDir = createDefaultCacheDir(context);
      long maxSize = calculateDiskCacheSize(cacheDir);
      unsharedCache = new Cache(cacheDir, maxSize);
      callFactory =
          new OkHttpClient.Builder()
              .cache(unsharedCache)
              .build();
    }
    if (cache == null) {
      cache = PlatformLruCache.create(calculateMemoryCacheSize(context));
    }
    if (service == null) {
      service = new PicassoExecutorService();
    }

    Dispatcher dispatcher =
        new Dispatcher(context, service, HANDLER, cache);

    return new Picasso(
        context,
        dispatcher,
        callFactory,
        unsharedCache,
        cache,
        listener,
        requestTransformers,
        requestHandlers,
        eventListeners,
        defaultBitmapConfig,
        indicatorsEnabled,
        loggingEnabled);
  }
}





}
final String TAG = "Picasso";