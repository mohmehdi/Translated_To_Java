package com.squareup.picasso3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Utils;
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
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import kotlinx.coroutines.CoroutineDispatcher;
import okhttp3.Call;
import okhttp3.Call.Factory;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

public class Picasso implements LifecycleObserver {
  private final Context context;
  private final Dispatcher dispatcher;
  private final Factory callFactory;
  private final Cache closeableCache;
  private final PlatformLruCache cache;
  private final Listener listener;
  private final List<RequestTransformer> requestTransformers;
  private final List<RequestHandler> requestHandlers;
  private final List<EventListener> eventListeners;
  private final Config defaultBitmapConfig;
  private boolean indicatorsEnabled;
  private volatile boolean isLoggingEnabled;

  public Picasso(
      Context context,
      Dispatcher dispatcher,
      Factory callFactory,
      Cache closeableCache,
      PlatformLruCache cache,
      Listener listener,
      List<RequestTransformer> requestTransformers,
      List<RequestHandler> requestHandlers,
      List<EventListener> eventListeners,
      Config defaultBitmapConfig,
      boolean indicatorsEnabled,
      boolean isLoggingEnabled) {
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
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  public void cancelAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      Target target = action.getTarget();
      if (target == null) {
        continue;
      }
      cancelExistingRequest(target);
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
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
        Target target = action.getTarget();
        if (target == null) {
          continue;
        }
        cancelExistingRequest(target);
      }
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (tag == deferredRequestCreator.tag) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  public void pauseAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchPauseTag(actions.get(i).tag);
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
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
  public void resumeAll() {
    checkMain();

    List<Action> actions = new ArrayList<>(targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchResumeTag(actions.get(i).tag);
    }

    List<DeferredRequestCreator> deferredRequestCreators =
        new ArrayList<>(targetToDeferredRequestCreator.values());
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
    return file == null
        ? new RequestCreator(this, null, 0)
        : load(Uri.fromFile(file));
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
    for (DeferredRequestCreator deferredRequestCreator :
        targetToDeferredRequestCreator.values()) {
      deferredRequestCreator.cancel();
    }
    targetToAction.clear();
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
    Target target = action.getTarget();
    if (target == null) {
      return;
    }
    Action existingAction = targetToAction.get(target);
    if (existingAction != null && existingAction != action) {
      cancelExistingRequest(target);
    }
    targetToAction.put(target, action);
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
    List<Action> single = hunter.action;
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
      for (int i = 0; i < joined.size(); i++) {
        deliverAction(result, joined.get(i), exception);
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, hunter.data.uri, exception);
    }
  }

  public void resumeAction(Action action) {
    Bitmap bitmap =
        shouldReadFromMemoryCache(action.request.memoryPolicy)
            ? quickMemoryCacheCheck(action.request.key)
            : null;

    if (bitmap != null) {
      deliverAction(
          new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action, null);
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
        log(OWNER_MAIN, VERB_RESUMED, action.request.logId());
      }
    }
  }

  private void deliverAction(
      Result result, List<Action> actions, Exception exception) {
    if (actions == null) {
      return;
    }
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      if (action.cancelled) {
        continue;
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
      } else if (exception != null) {
        action.error(exception);
        if (isLoggingEnabled) {
          log(
              OWNER_MAIN,
              VERB_ERRORED,
              action.request.logId(),
              exception.getMessage());
        }
      }
    }
  }

  private void deliverAction(
      Result result, Action action, Exception exception) {
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
    } else if (exception != null) {
      action.error(exception);
      if (isLoggingEnabled) {
        log(
            OWNER_MAIN,
            VERB_ERRORED,
            action.request.logId(),
            exception.getMessage());
      }
    }
  }

  private void cancelExistingRequest(Target target) {
    checkMain();
    Action action = targetToAction.remove(target);
    if (action != null) {
      action.cancel();
      dispatcher.dispatchCancel(action);
    }
    if (target instanceof ImageView) {
      DeferredRequestCreator deferredRequestCreator =
          targetToDeferredRequestCreator.remove(target);
      deferredRequestCreator.cancel();
    }
  }

  public Picasso.Builder newBuilder() {
    return new Picasso.Builder(this);
  }

  public static class Builder {
    private final Context context;
    private Factory callFactory;
    private ExecutorService service;
    private CoroutineDispatcher picassoDispatcher;
    private PlatformLruCache cache;
    private Listener listener;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final List<RequestHandler> requestHandlers = new ArrayList<>();
    private final List<EventListener> eventListeners = new ArrayList<>();
    private Config defaultBitmapConfig;
    private boolean indicatorsEnabled;
    private boolean loggingEnabled;

    public Builder(Context context) {
      this.context = context.getApplicationContext();
    }

    public Builder(Picasso picasso) {
      context = picasso.context;
      callFactory = picasso.callFactory;
      service = picasso.dispatcher.service;
      picassoDispatcher =
          (picasso.dispatcher instanceof InternalCoroutineDispatcher)
              ? ((InternalCoroutineDispatcher) picasso.dispatcher).picassoDispatcher
              : null;
      cache = picasso.cache;
      listener = picasso.listener;
      requestTransformers.addAll(picasso.requestTransformers);

      int numRequestHandlers = picasso.requestHandlers.size();
      requestHandlers.addAll(
          picasso.requestHandlers.subList(2, numRequestHandlers - 6));
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

    public Builder callFactory(Factory factory) {
      callFactory = factory;
      return this;
    }

    public Builder executor(ExecutorService executorService) {
      service = executorService;
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
      OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
      if (callFactory == null) {
        File cacheDir = createDefaultCacheDir(context);
        long maxSize = calculateDiskCacheSize(cacheDir);
        okHttpClientBuilder.cache(new okhttp3.Cache(cacheDir, maxSize));
        callFactory = okHttpClientBuilder.build();
      } else {
        okHttpClientBuilder.callFactory(callFactory);
      }

      if (cache == null) {
        cache = new PlatformLruCache(calculateMemoryCacheSize(context));
      }

      if (service == null) {
        service = new PicassoExecutorService();
      }

      Dispatcher dispatcher;
      if (picassoDispatcher != null) {
        dispatcher =
            new InternalCoroutineDispatcher(
                context, service, new Handler(Looper.getMainLooper()), cache, picassoDispatcher);
      } else {
        dispatcher = new HandlerDispatcher(context, service, new Handler(Looper.getMainLooper()), cache);
      }

      return new Picasso(
          context,
          dispatcher,
          callFactory,
          closeableCache,
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




@SuppressWarnings("unused")
internal void cacheMaxSize(int maxSize) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
        eventListeners.get(i).cacheMaxSize(maxSize);
    }
}

@SuppressWarnings("unused")
internal void cacheSize(int size) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
        eventListeners.get(i).cacheSize(size);
    }
}

@SuppressWarnings("unused")
internal void cacheHit() {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
        eventListeners.get(i).cacheHit();
    }
}

@SuppressWarnings("unused")
internal void cacheMiss() {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
        eventListeners.get(i).cacheMiss();
    }
}

@SuppressWarnings("unused")
internal void downloadFinished(long size) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
        eventListeners.get(i).downloadFinished(size);
    }
}

@SuppressWarnings("unused")
internal void bitmapDecoded(Bitmap bitmap) {
    int numListeners = eventListeners.size();
    for (int i = 0; i < numListeners; i++) {
        eventListeners.get(i).bitmapDecoded(bitmap);
    }
}

    // Kotlin will generate this method name as '_bitmapTransformed'
    @JvmName("-bitmapTransformed")
    public void bitmapTransformed(Bitmap bitmap) {
        int numListeners = eventListeners.size();
        for (int i = 0; i < numListeners; i++) {
            eventListeners.get(i).bitmapTransformed(bitmap);
        }
    }

    // Kotlin will generate this method name as '_close'
    @JvmName("-close")
    public void close() {
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
    MEMORY,
    DISK,
    NETWORK;

    public final int debugColor;

    LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }


}