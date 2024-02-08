package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import kotlinx.coroutines.CoroutineDispatcher;

@OptIn(ExperimentalStdlibApi.class)
public class Picasso implements LifecycleObserver {
  internal final Context context;
  internal final Dispatcher dispatcher;
  internal final Call.Factory callFactory;
  private final Cache closeableCache;
  internal final PlatformLruCache cache;
  internal final Listener listener;
  internal final List < RequestTransformer > requestTransformers;
  internal final List < RequestHandler > requestHandlers;
  internal final List < EventListener > eventListeners;
  internal final Config defaultBitmapConfig;
  internal boolean indicatorsEnabled;
  volatile boolean isLoggingEnabled;
  internal final WeakHashMap < Object, Action > targetToAction = new WeakHashMap < > ();
  internal final WeakHashMap < ImageView, DeferredRequestCreator > targetToDeferredRequestCreator = new WeakHashMap < > ();
  internal boolean shutdown = false;

  public Picasso(Context context, Dispatcher dispatcher, Call.Factory callFactory,
    Cache closeableCache, PlatformLruCache cache, Listener listener,
    List < RequestTransformer > requestTransformers, List < RequestHandler > extraRequestHandlers,
    List < EventListener > eventListeners, Config defaultBitmapConfig, boolean indicatorsEnabled,
    boolean isLoggingEnabled) {
    this.context = context;
    this.dispatcher = dispatcher;
    this.callFactory = callFactory;
    this.closeableCache = closeableCache;
    this.cache = cache;
    this.listener = listener;
    this.requestTransformers = requestTransformers;
    this.defaultBitmapConfig = defaultBitmapConfig;
    this.indicatorsEnabled = indicatorsEnabled;
    this.isLoggingEnabled = isLoggingEnabled;

    int builtInHandlers = 8;
    requestHandlers = buildRequestHandlers(builtInHandlers, extraRequestHandlers);
  }

  private List < RequestHandler > buildRequestHandlers(int builtInHandlers, List < RequestHandler > extraRequestHandlers) {
    List < RequestHandler > handlers = new ArrayList < > (builtInHandlers + extraRequestHandlers.size());
    handlers.add(ResourceDrawableRequestHandler.create(context));
    handlers.add(new ResourceRequestHandler(context));
    handlers.addAll(extraRequestHandlers);
    handlers.add(new ContactsPhotoRequestHandler(context));
    handlers.add(new MediaStoreRequestHandler(context));
    handlers.add(new ContentStreamRequestHandler(context));
    handlers.add(new AssetRequestHandler(context));
    handlers.add(new FileRequestHandler(context));
    handlers.add(new NetworkRequestHandler(callFactory));
    return handlers;
  }

  @OnLifecycleEvent(Event.ON_DESTROY)
  internal void cancelAll() {
    checkMain();

    List < Action > actions = new ArrayList < > (targetToAction.values());
    for (Action action: actions) {
      Object target = action.getTarget();
      if (target != null) {
        cancelExistingRequest(target);
      }
    }

    List < DeferredRequestCreator > deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator: deferredRequestCreators) {
      deferredRequestCreator.cancel();
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

    List < Action > actions = new ArrayList < > (targetToAction.values());
    for (Action action: actions) {
      if (tag.equals(action.tag)) {
        Object target = action.getTarget();
        if (target != null) {
          cancelExistingRequest(target);
        }
      }
    }

    List < DeferredRequestCreator > deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator: deferredRequestCreators) {
      if (tag.equals(deferredRequestCreator.tag)) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(Event.ON_STOP)
  internal void pauseAll() {
    checkMain();

    List < Action > actions = new ArrayList < > (targetToAction.values());
    for (Action action: actions) {
      dispatcher.dispatchPauseTag(action.tag);
    }

    List < DeferredRequestCreator > deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator: deferredRequestCreators) {
      Object tag = deferredRequestCreator.tag;
      if (tag != null) {
        dispatcher.dispatchPauseTag(tag);
      }
    }
  }

  public void pauseTag(Object tag) {
    dispatcher.dispatchPauseTag(tag);
  }

  @OnLifecycleEvent(Event.ON_START)
  internal void resumeAll() {
    checkMain();

    List < Action > actions = new ArrayList < > (targetToAction.values());
    for (Action action: actions) {
      dispatcher.dispatchResumeTag(action.tag);
    }

    List < DeferredRequestCreator > deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator: deferredRequestCreators) {
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
    requireNotBlank(path);
    return load(Uri.parse(path));
  }

  public RequestCreator load(File file) {
    return file == null ? new RequestCreator(this, null, 0) : load(Uri.fromFile(file));
  }

  public RequestCreator load(@DrawableRes int resourceId) {
    requireNonZero(resourceId);
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
    for (DeferredRequestCreator deferredRequestCreator: targetToDeferredRequestCreator.values()) {
      deferredRequestCreator.cancel();
    }
    targetToAction.clear();
    targetToDeferredRequestCreator.clear();
    shutdown = true;
  }

  @JvmName("-transformRequest")
  internal Request transformRequest(Request request) {
    Request nextRequest = request;
    for (RequestTransformer transformer: requestTransformers) {
      nextRequest = transformer.transformRequest(nextRequest);
    }
    return nextRequest;
  }

  @JvmName("-defer")
  internal void defer(ImageView view, DeferredRequestCreator request) {
    if (targetToDeferredRequestCreator.containsKey(view)) {
      cancelExistingRequest(view);
    }
    targetToDeferredRequestCreator.put(view, request);
  }

  @JvmName("-enqueueAndSubmit")
  internal void enqueueAndSubmit(Action action) {
    Object target = action.getTarget();
    if (targetToAction.get(target) != action) {
      cancelExistingRequest(target);
      targetToAction.put(target, action);
    }
    submit(action);
  }

  @JvmName("-submit")
  internal void submit(Action action) {
    dispatcher.dispatchSubmit(action);
  }

  @JvmName("-quickMemoryCacheCheck")
  internal Bitmap quickMemoryCacheCheck(String key) {
    Bitmap cached = cache.get(key);
    if (cached != null) {
      cacheHit();
    } else {
      cacheMiss();
    }
    return cached;
  }

  @JvmName("-complete")
  internal void complete(BitmapHunter hunter) {
    Action single = hunter.action;
    List < Action > joined = hunter.actions;

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
      for (Action action: joined) {
        deliverAction(result, action, exception);
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, hunter.data.uri, exception);
    }
  }

  @JvmName("-resumeAction")
  internal void resumeAction(Action action) {
    Bitmap bitmap = shouldReadFromMemoryCache(action.request.memoryPolicy) ?
      quickMemoryCacheCheck(action.request.key) : null;

    if (bitmap != null) {
      deliverAction(new Result.Bitmap(bitmap, MEMORY), action, null);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.request.logId(), "from " + MEMORY);
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
      DeferredRequestCreator deferredRequestCreator = targetToDeferredRequestCreator.remove((ImageView) target);
      if (deferredRequestCreator != null) {
        deferredRequestCreator.cancel();
      }
    }
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  public class Builder {
    private final Context context;
    private Call.Factory callFactory;
    private ExecutorService service;
    private CoroutineDispatcher picassoDispatcher;
    private PlatformLruCache cache;
    private Listener listener;
    private final List < RequestTransformer > requestTransformers = new ArrayList < > ();
    private final List < RequestHandler > requestHandlers = new ArrayList < > ();
    private final List < EventListener > eventListeners = new ArrayList < > ();
    private Config defaultBitmapConfig;
    private boolean indicatorsEnabled = false;
    private boolean loggingEnabled = false;

    public Builder(Context context) {
      this.context = context.getApplicationContext();
    }

    internal Builder(Picasso picasso) {
      context = picasso.context;
      callFactory = picasso.callFactory;
      service = picasso.dispatcher.service;
      picassoDispatcher = (picasso.dispatcher instanceof InternalCoroutineDispatcher) ?
        ((InternalCoroutineDispatcher) picasso.dispatcher).picassoDispatcher :
        null;
      cache = picasso.cache;
      listener = picasso.listener;
      requestTransformers.addAll(picasso.requestTransformers);
      // See Picasso(). Removes internal request handlers added before and after custom handlers.
      int numRequestHandlers = picasso.requestHandlers.size();
      requestHandlers.addAll(picasso.requestHandlers.subList(2, numRequestHandlers - 6));
      eventListeners.addAll(picasso.eventListeners);

      defaultBitmapConfig = picasso.defaultBitmapConfig;
      indicatorsEnabled = picasso.indicatorsEnabled;
      loggingEnabled = picasso.isLoggingEnabled;
    }

    public Builder defaultBitmapConfig(Config bitmapConfig) {
      defaultBitmapConfig = bitmapConfig;
      return this;
    }

    public Builder client(OkHttpClient client) {
      callFactory = client;
      return this;
    }

    public Builder callFactory(Call.Factory factory) {
      callFactory = factory;
      return this;
    }

    public Builder executor(ExecutorService executorService) {
      service = executorService;
      return this;
    }

    public Builder withCacheSize(int maxByteCount) {
      if (maxByteCount < 0) {
        throw new IllegalArgumentException("maxByteCount < 0: " + maxByteCount);
      }
      cache = new PlatformLruCache(maxByteCount);
      return this;
    }

    public Builder listener(Listener listener) {
      this.listener = listener;
      return this;
    }

    public Builder addRequestTransformer(RequestTransformer transformer) {
      requestTransformers.add(transformer);
      return this;
    }

    public Builder addRequestHandler(RequestHandler requestHandler) {
      requestHandlers.add(requestHandler);
      return this;
    }

    public Builder addEventListener(EventListener eventListener) {
      eventListeners.add(eventListener);
      return this;
    }

    public Builder indicatorsEnabled(boolean enabled) {
      indicatorsEnabled = enabled;
      return this;
    }

    public Builder loggingEnabled(boolean enabled) {
      loggingEnabled = enabled;
      return this;
    }

    public Builder dispatcher(CoroutineDispatcher picassoDispatcher) {
      this.picassoDispatcher = picassoDispatcher;
      return this;
    }

    public Picasso build() {
      okhttp3.Cache unsharedCache = null;
      if (callFactory == null) {
        File cacheDir = createDefaultCacheDir(context);
        long maxSize = calculateDiskCacheSize(cacheDir);
        unsharedCache = new okhttp3.Cache(cacheDir, maxSize);
        callFactory = new OkHttpClient.Builder()
          .cache(unsharedCache)
          .build();
      }
      if (cache == null) {
        cache = new PlatformLruCache(calculateMemoryCacheSize(context));
      }
      if (service == null) {
        service = new PicassoExecutorService();
      }

      CoroutineDispatcher dispatcher = (picassoDispatcher != null) ?
        new InternalCoroutineDispatcher(context, service, HANDLER, cache, picassoDispatcher) :
        new HandlerDispatcher(context, service, HANDLER, cache);

      return new Picasso(
        context, dispatcher, callFactory, unsharedCache, cache, listener,
        requestTransformers, requestHandlers, eventListeners, defaultBitmapConfig,
        indicatorsEnabled, loggingEnabled
      );
    }
  }

  @JvmName("-cacheMaxSize") // Prefix with '-' to hide from Java.
  internal void cacheMaxSize(int maxSize) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).cacheMaxSize(maxSize);
    }
  }

  @JvmName("-cacheSize") // Prefix with '-' to hide from Java.
  internal void cacheSize(int size) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).cacheSize(size);
    }
  }

  @JvmName("-cacheHit") // Prefix with '-' to hide from Java.
  internal void cacheHit() {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).cacheHit();
    }
  }

  @JvmName("-cacheMiss") // Prefix with '-' to hide from Java.
  internal void cacheMiss() {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).cacheMiss();
    }
  }

  @JvmName("-downloadFinished") // Prefix with '-' to hide from Java.
  internal void downloadFinished(long size) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).downloadFinished(size);
    }
  }

  @JvmName("-bitmapDecoded") // Prefix with '-' to hide from Java.
  internal void bitmapDecoded(Bitmap bitmap) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).bitmapDecoded(bitmap);
    }
  }

  @JvmName("-bitmapTransformed") // Prefix with '-' to hide from Java.
  internal void bitmapTransformed(Bitmap bitmap) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).bitmapTransformed(bitmap);
    }
  }

  @JvmName("-close") // Prefix with '-' to hide from Java.
  internal void close() {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
      eventListeners.get(i).close();
    }
  }

  public interface Listener {
    void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception);
  }

  public interface RequestTransformer {
    Request transformRequest(Request request);
  }

  public enum Priority {
    LOW,
    NORMAL,
    HIGH
  }

  public enum LoadedFrom {
    MEMORY(Color.GREEN),
      DISK(Color.BLUE),
      NETWORK(Color.RED);

    @JvmName("-debugColor")
    internal final int debugColor;

    LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }

  @JvmName("-handler")
  internal static final Handler HANDLER = new Handler(Looper.getMainLooper());

}

public class Picasso {
  public static final String TAG = "Picasso";
}