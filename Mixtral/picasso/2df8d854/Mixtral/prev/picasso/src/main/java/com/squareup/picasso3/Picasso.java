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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;

@SuppressWarnings("unused")
public class Picasso implements LifecycleObserver {
  private final Context context;
  private final Dispatcher dispatcher;
  private final Call.Factory callFactory;
  private final Cache closeableCache;
  private final PlatformLruCache cache;
  private final Listener listener;
  private final List requestTransformers;
  private final List requestHandlers;
  private final List eventListeners;
  private final Config defaultBitmapConfig;
  private boolean indicatorsEnabled;
  private boolean isLoggingEnabled;
  private final Map < Object, Action > targetToAction;
  private final Map < ImageView, DeferredRequestCreator > targetToDeferredRequestCreator;
  private boolean shutdown;

  public Picasso(Context context, Dispatcher dispatcher, Call.Factory callFactory, Cache closeableCache, PlatformLruCache cache, Listener listener, List requestTransformers, List extraRequestHandlers, List eventListeners, Config defaultBitmapConfig, boolean indicatorsEnabled, boolean isLoggingEnabled) {
    this.context = context;
    this.dispatcher = dispatcher;
    this.callFactory = callFactory;
    this.closeableCache = closeableCache;
    this.cache = cache;
    this.listener = listener;
    this.requestTransformers = new ArrayList < > (requestTransformers);
    this.requestHandlers = new ArrayList < > (extraRequestHandlers.size() + 8);
    this.eventListeners = new ArrayList < > (eventListeners);
    this.defaultBitmapConfig = defaultBitmapConfig;
    this.indicatorsEnabled = indicatorsEnabled;
    this.isLoggingEnabled = isLoggingEnabled;
    this.targetToAction = new HashMap < > ();
    this.targetToDeferredRequestCreator = new HashMap < > ();

    requestHandlers.add(ResourceDrawableRequestHandler.create(context));
    requestHandlers.add(ResourceRequestHandler.create(context));
    requestHandlers.addAll(extraRequestHandlers);
    requestHandlers.add(ContactsPhotoRequestHandler.create(context));
    requestHandlers.add(MediaStoreRequestHandler.create(context));
    requestHandlers.add(ContentStreamRequestHandler.create(context));
    requestHandlers.add(AssetRequestHandler.create(context));
    requestHandlers.add(FileRequestHandler.create(context));
    requestHandlers.add(NetworkRequestHandler.create(callFactory));
  }

  @OnLifecycleEvent(Event.ON_DESTROY)
  private void cancelAll() {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      cancelExistingRequest(actions.get(i).getTarget());
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

  public void cancelRequest(RemoteViews remoteViews, @IdRes int viewId) {
    cancelExistingRequest(new RemoteViewsTarget(remoteViews, viewId));
  }

  public void cancelTag(Object tag) {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      if (tag.equals(action.getTag())) {
        cancelExistingRequest(action.getTarget());
      }
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      if (tag.equals(deferredRequestCreator.getTag())) {
        deferredRequestCreator.cancel();
      }
    }
  }

  @OnLifecycleEvent(Event.ON_STOP)
  private void pauseAll() {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchPauseTag(actions.get(i).getTag());
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      Object tag = deferredRequestCreator.getTag();
      if (tag != null) {
        dispatcher.dispatchPauseTag(tag);
      }
    }
  }

  public void pauseTag(Object tag) {
    dispatcher.dispatchPauseTag(tag);
  }

  @OnLifecycleEvent(Event.ON_START)
  private void resumeAll() {
    checkMain();

    List actions = new ArrayList < > (targetToAction.values());
    for (int i = 0; i < actions.size(); i++) {
      dispatcher.dispatchResumeTag(actions.get(i).getTag());
    }

    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      DeferredRequestCreator deferredRequestCreator = deferredRequestCreators.get(i);
      Object tag = deferredRequestCreator.getTag();
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
      closeableCache.close();
    } catch (IOException ignored) {}
    List deferredRequestCreators = new ArrayList < > (targetToDeferredRequestCreator.values());
    for (int i = 0; i < deferredRequestCreators.size(); i++) {
      deferredRequestCreators.get(i).cancel();
    }
    targetToDeferredRequestCreator.clear();
    shutdown = true;
  }

  private Request transformRequest(Request request) {
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
    if (targetToAction.get(target) != action) {
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
    List single = hunter.getActions();
    List joined = hunter.getJoinedActions();

    boolean hasMultiple = joined != null && !joined.isEmpty();
    boolean shouldDeliver = single != null || hasMultiple;

    if (!shouldDeliver) {
      return;
    }

    Exception exception = hunter.getException();
    Result result = hunter.getResult();

    if (single != null) {
      deliverAction(result, single, exception);
    }

    if (joined != null) {
      for (int i = 0; i < joined.size(); i++) {
        deliverAction(result, joined.get(i), exception);
      }
    }

    if (listener != null && exception != null) {
      listener.onImageLoadFailed(this, hunter.getData().getUri(), exception);
    }
  }

  private void resumeAction(Action action) {
    Bitmap bitmap = shouldReadFromMemoryCache(action.getRequest().memoryPolicy) ? quickMemoryCacheCheck(action.getRequest().getKey()) : null;

    if (bitmap != null) {
      deliverAction(new Result.Bitmap(bitmap, LoadedFrom.MEMORY), action, null);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.getRequest().logId(), "from " + LoadedFrom.MEMORY);
      }
    } else {
      enqueueAndSubmit(action);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_RESUMED, action.getRequest().logId());
      }
    }
  }

  private void deliverAction(Result < ? > result, Action action, Exception e) {
    if (action.isCancelled()) {
      return;
    }
    if (!action.willReplay) {
      targetToAction.remove(action.getTarget());
    }
    if (result != null) {
      action.complete(result);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_COMPLETED, action.getRequest().logId(), "from " + result.loadedFrom);
      }
    } else if (e != null) {
      action.error(e);
      if (isLoggingEnabled) {
        log(OWNER_MAIN, VERB_ERRORED, action.getRequest().logId(), e.getMessage());
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

  private void cacheMaxSize(int maxSize) {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).cacheMaxSize(maxSize);
    }
  }

  private void cacheSize(int size) {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).cacheSize(size);
    }
  }

  private void cacheHit() {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).cacheHit();
    }
  }

  private void cacheMiss() {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).cacheMiss();
    }
  }

  private void downloadFinished(long size) {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).downloadFinished(size);
    }
  }

  private void bitmapDecoded(Bitmap bitmap) {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).bitmapDecoded(bitmap);
    }
  }

  private void bitmapTransformed(Bitmap bitmap) {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).bitmapTransformed(bitmap);
    }
  }

  private void close() {
    for (int i = 0; i < eventListeners.size(); i++) {
      eventListeners.get(i).close();
    }
  }




@FunctionalInterface
public interface Listener {
    void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception);
}

@FunctionalInterface
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

    private final int debugColor;

    LoadedFrom(int debugColor) {
        this.debugColor = debugColor;
    }

    @JvmName("-debugColor")
    public int getDebugColor() {
        return debugColor;
    }
}

  
        public static final Handler HANDLER = new Handler(Looper.getMainLooper()) {
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
        };

        public static final int HUNTER_COMPLETE = 1;
        public static final int REQUEST_BATCH_RESUME = 2;




public class Builder {
    private Context context;
    private Call.Factory callFactory;
    private ExecutorService service;
    private PlatformLruCache cache;
    private Listener listener;
    private List<RequestTransformer> requestTransformers = new ArrayList<>();
    private List<RequestHandler> requestHandlers = new ArrayList<>();
    private List<EventListener> eventListeners = new ArrayList<>();
    private Config defaultBitmapConfig;
    private boolean indicatorsEnabled;
    private boolean loggingEnabled;

    public Builder(Context context) {
        this.context = context.getApplicationContext();
    }

    internal Builder(Picasso picasso) {
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
        loggingEnabled = picasso.loggingEnabled;
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
        require(maxByteCount >= 0);
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

    public Picasso build() {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        if (callFactory == null) {
            File cacheDir = createDefaultCacheDir(context);
            long maxSize = calculateDiskCacheSize(cacheDir);
            okHttpClientBuilder.cache(new okhttp3.Cache(cacheDir, maxSize));
            callFactory = okHttpClientBuilder.build();
        }
        if (cache == null) {
            cache = new PlatformLruCache(calculateMemoryCacheSize(context));
        }
        if (service == null) {
            service = Executors.newFixedThreadPool(4);
        }

        Dispatcher dispatcher = new Dispatcher(context, service, HANDLER, cache);

        return new Picasso(
                context, dispatcher, callFactory, null, cache, listener,
                requestTransformers, requestHandlers, eventListeners, defaultBitmapConfig,
                indicatorsEnabled, loggingEnabled
        );
    }
}

}