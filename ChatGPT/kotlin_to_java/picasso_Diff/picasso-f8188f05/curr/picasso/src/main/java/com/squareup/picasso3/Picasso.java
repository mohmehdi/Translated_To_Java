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
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY;
import androidx.lifecycle.Lifecycle.Event.ON_START;
import androidx.lifecycle.Lifecycle.Event.ON_STOP;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import com.squareup.picasso3.MemoryPolicy.Companion.shouldReadFromMemoryCache;
import com.squareup.picasso3.Picasso.LoadedFrom.MEMORY;
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
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import java.io.File;
import java.io.IOException;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;

@OptIn(ExperimentalStdlibApi::class)
class Picasso internal constructor(
  @get:JvmName("-context") internal val context: Context,
  @get:JvmName("-dispatcher") internal val dispatcher: Dispatcher,
  @get:JvmName("-callFactory") internal val callFactory: Call.Factory,
  private val closeableCache: Cache?,
  @get:JvmName("-cache") internal val cache: PlatformLruCache,
  @get:JvmName("-listener") internal val listener: Listener?,
  requestTransformers: List<RequestTransformer>,
  extraRequestHandlers: List<RequestHandler>,
  eventListeners: List<EventListener>,
  @get:JvmName("-defaultBitmapConfig") internal val defaultBitmapConfig: Config?,
    var indicatorsEnabled: Boolean,
    @Volatile var isLoggingEnabled: Boolean
) : LifecycleObserver {
  @get:JvmName("-requestTransformers")
  internal val requestTransformers: List<RequestTransformer> = requestTransformers.toList();

  @get:JvmName("-requestHandlers")
  internal val requestHandlers: List<RequestHandler>;

  @get:JvmName("-eventListeners")
  internal val eventListeners: List<EventListener> = eventListeners.toList();

  @get:JvmName("-targetToAction")
  internal val targetToAction = new WeakHashMap<>();

  @get:JvmName("-targetToDeferredRequestCreator")
  internal val targetToDeferredRequestCreator = new WeakHashMap<>();

  @get:JvmName("-shutdown")
  @set:JvmName("-shutdown")
  internal var shutdown = false;

  {
    val builtInHandlers = 8;

    requestHandlers = new ArrayList<>(builtInHandlers + extraRequestHandlers.size());
    requestHandlers.add(ResourceDrawableRequestHandler.create(context));
    requestHandlers.add(new ResourceRequestHandler(context));
    requestHandlers.addAll(extraRequestHandlers);
    requestHandlers.add(new ContactsPhotoRequestHandler(context));
    requestHandlers.add(new MediaStoreRequestHandler(context));
    requestHandlers.add(new ContentStreamRequestHandler(context));
    requestHandlers.add(new AssetRequestHandler(context));
    requestHandlers.add(new FileRequestHandler(context));
    requestHandlers.add(new NetworkRequestHandler(callFactory));
  }

  @OnLifecycleEvent(ON_DESTROY)
  @JvmName("-cancelAll")
  internal void cancelAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (Action action : actions) {
      Object target = action.getTarget();
      if (target != null) {
        cancelExistingRequest(target);
      }
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator : deferredRequestCreators) {
      deferredRequestCreator.cancel();
    }
  }

    void cancelRequest(ImageView view) {
    cancelExistingRequest(view);
  }

    void cancelRequest(BitmapTarget target) {
    cancelExistingRequest(target);
  }

    void cancelRequest(DrawableTarget target) {
    cancelExistingRequest(target);
  }

    void cancelRequest(RemoteViews remoteViews, @IdRes int viewId) {
    cancelExistingRequest(new RemoteViewsTarget(remoteViews, viewId));
  }

    void cancelTag(Object tag) {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (Action action : actions) {
      if (tag.equals(action.getTag())) {
        Object target = action.getTarget();
        if (target != null) {
          cancelExistingRequest(target);
        }
      }
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator : deferredRequestCreators) {
      if (tag.equals(deferredRequestCreator.getTag())) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(ON_STOP)
  @JvmName("-pauseAll")
  internal void pauseAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (Action action : actions) {
      dispatcher.dispatchPauseTag(action.getTag());
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator : deferredRequestCreators) {
      Object tag = deferredRequestCreator.getTag();
      if (tag != null) {
        dispatcher.dispatchPauseTag(tag);
      }
    }
  }

    void pauseTag(Object tag) {
    dispatcher.dispatchPauseTag(tag);
  }

  @OnLifecycleEvent(ON_START)
  @JvmName("-resumeAll")
  internal void resumeAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (Action action : actions) {
      dispatcher.dispatchResumeTag(action.getTag());
    }

    List<DeferredRequestCreator> deferredRequestCreators = new ArrayList<>(targetToDeferredRequestCreator.values());
    for (DeferredRequestCreator deferredRequestCreator : deferredRequestCreators) {
      Object tag = deferredRequestCreator.getTag();
      if (tag != null) {
        dispatcher.dispatchResumeTag(tag);
      }
    }
  }

    void resumeTag(Object tag) {
    dispatcher.dispatchResumeTag(tag);
  }

    RequestCreator load(Uri uri) {
    return new RequestCreator(this, uri, 0);
  }

    RequestCreator load(String path) {
    if (path == null) {
      return new RequestCreator(this, null, 0);
    }
    if (path.trim().isEmpty()) {
      throw new IllegalArgumentException("Path must not be empty.");
    }
    return load(Uri.parse(path));
  }

    RequestCreator load(File file) {
    return file == null ? new RequestCreator(this, null, 0) : load(Uri.fromFile(file));
  }

    RequestCreator load(@DrawableRes int resourceId) {
    if (resourceId == 0) {
      throw new IllegalArgumentException("Resource ID must not be zero.");
    }
    return new RequestCreator(this, null, resourceId);
  }

    void evictAll() {
    cache.clear();
  }

    void invalidate(Uri uri) {
    if (uri != null) {
      cache.clearKeyUri(uri.toString());
    }
  }

    void invalidate(String path) {
    if (path != null) {
      invalidate(Uri.parse(path));
    }
  }

    void invalidate(File file) {
    invalidate(Uri.fromFile(file));
  }

    void shutdown() {
    if (shutdown) {
      return;
    }
    cache.clear();

    close();

    dispatcher.shutdown();
    try {
      closeableCache.close();
    } catch (IOException ignored) {
    }
    for (DeferredRequestCreator deferredRequestCreator : targetToDeferredRequestCreator.values()) {
      deferredRequestCreator.cancel();
    }
    targetToAction.clear();
    targetToDeferredRequestCreator.clear();
    shutdown = true;
  }

  @JvmName("-transformRequest")
  internal Request transformRequest(Request request) {
    Request nextRequest = request;
    for (RequestTransformer transformer : requestTransformers) {
      nextRequest = transformer.transformRequest(nextRequest);
    }
    return nextRequest;
  }

  @JvmName("-defer")
  internal void defer(ImageView view, DeferredRequestCreator request) {
    // If there is already a deferred request, cancel it.
    if (targetToDeferredRequestCreator.containsKey(view)) {
      cancelExistingRequest(view);
    }
    targetToDeferredRequestCreator.put(view, request);
  }

  @JvmName("-enqueueAndSubmit")
  internal void enqueueAndSubmit(Action action) {
    Object target = action.getTarget();
    if (target != null && targetToAction.get(target) != action) {
      // This will also check we are on the main thread.
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
    List<Action> joined = hunter.actions;

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
      for (Action action : joined) {
        deliverAction(result, action, exception);
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, hunter.data.uri, exception);
    }
  }

  @JvmName("-resumeAction")
  internal void resumeAction(Action action) {
    Bitmap bitmap = shouldReadFromMemoryCache(action.request.memoryPolicy)
        ? quickMemoryCacheCheck(action.request.key)
        : null;

    if (bitmap != null) {
      deliverAction(new Result.Bitmap(bitmap, MEMORY), action, null);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_COMPLETED,
          action.request.logId(),
          "from $MEMORY"
        );
      }
    } else {
      enqueueAndSubmit(action);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_RESUMED,
          action.request.logId()
        );
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
        log(
          OWNER_MAIN,
          VERB_COMPLETED,
          action.request.logId(),
          "from " + result.loadedFrom
        );
      }
    } else if (e != null) {
      action.error(e);
      if (isLoggingEnabled) {
        log(
          OWNER_MAIN,
          VERB_ERRORED,
          action.request.logId(),
          e.getMessage()
        );
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

  Builder newBuilder() {
    return new Builder(this);
  }

public class Builder {
    private final Context context;
    private Call.Factory callFactory;
    private ExecutorService service;
    private CoroutineDispatcher mainDispatcher;
    private CoroutineDispatcher backgroundDispatcher;
    private PlatformLruCache cache;
    private Listener listener;
    private final MutableList<RequestTransformer> requestTransformers = new ArrayList<>();
    private final MutableList<RequestHandler> requestHandlers = new ArrayList<>();
    private final MutableList<EventListener> eventListeners = new ArrayList<>();
    private Config defaultBitmapConfig;
    private boolean indicatorsEnabled = false;
    private boolean loggingEnabled = false;

    public Builder(Context context) {
        this.context = context.getApplicationContext();
    }

    internal Builder(Picasso picasso) {
        context = picasso.getContext();
        callFactory = picasso.getCallFactory();
        service = (picasso.getDispatcher() instanceof HandlerDispatcher)
            ? ((HandlerDispatcher) picasso.getDispatcher()).getService()
            : null;
        mainDispatcher = (picasso.getDispatcher() instanceof InternalCoroutineDispatcher)
            ? ((InternalCoroutineDispatcher) picasso.getDispatcher()).getMainDispatcher()
            : null;
        backgroundDispatcher = (picasso.getDispatcher() instanceof InternalCoroutineDispatcher)
            ? ((InternalCoroutineDispatcher) picasso.getDispatcher()).getBackgroundDispatcher()
            : null;
        cache = picasso.getCache();
        listener = picasso.getListener();
        requestTransformers.addAll(picasso.getRequestTransformers());
        int numRequestHandlers = picasso.getRequestHandlers().size();
        requestHandlers.addAll(picasso.getRequestHandlers().subList(2, numRequestHandlers - 6));
        eventListeners.addAll(picasso.getEventListeners());
        defaultBitmapConfig = picasso.getDefaultBitmapConfig();
        indicatorsEnabled = picasso.indicatorsEnabled;
        loggingEnabled = picasso.isLoggingEnabled();
    }

    public Builder defaultBitmapConfig(Config bitmapConfig) {
        this.defaultBitmapConfig = bitmapConfig;
        return this;
    }

    public Builder client(OkHttpClient client) {
        this.callFactory = client;
        return this;
    }

    public Builder callFactory(Call.Factory factory) {
        this.callFactory = factory;
        return this;
    }

    public Builder executor(ExecutorService executorService) {
        this.service = executorService;
        return this;
    }

    public Builder withCacheSize(int maxByteCount) {
        require(maxByteCount >= 0, "maxByteCount < 0: " + maxByteCount);
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
        this.indicatorsEnabled = enabled;
        return this;
    }

    public Builder loggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        return this;
    }

    public Builder dispatchers(CoroutineDispatcher mainDispatcher, CoroutineDispatcher backgroundDispatcher) {
        this.mainDispatcher = mainDispatcher;
        this.backgroundDispatcher = backgroundDispatcher;
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

        Dispatcher dispatcher;
        if (backgroundDispatcher != null) {
            dispatcher = new InternalCoroutineDispatcher(context, HANDLER, cache, mainDispatcher, backgroundDispatcher);
        } else {
            if (service == null) {
                service = new PicassoExecutorService();
            }
            dispatcher = new HandlerDispatcher(context, service, HANDLER, cache);
        }

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

    internal static final Handler HANDLER = new Handler(Looper.getMainLooper());
  }


const String TAG = "Picasso";