package com.squareup.picasso3;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Utils.OWNER_MAIN;
import com.squareup.picasso3.Utils.VERB_COMPLETED;
import com.squareup.picasso3.Utils.checkMain;
import com.squareup.picasso3.Utils.checkNotMain;
import com.squareup.picasso3.Utils.log;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.squareup.picasso3.MemoryPolicy.shouldReadFromMemoryCache;
import static com.squareup.picasso3.MemoryPolicy.shouldWriteToMemoryCache;
import static com.squareup.picasso3.Picasso.Priority;

class RequestCreator {
  private final Picasso picasso;
  private final Request.Builder data;

  private boolean noFade;
  private boolean deferred;
  private boolean setPlaceholder;
  @DrawableRes private int placeholderResId;
  @DrawableRes private int errorResId;
  private Drawable placeholderDrawable;
  private Drawable errorDrawable;

  @Nullable private Object tag;

  RequestCreator(Picasso picasso, Uri uri, int resourceId) {
    this.picasso = picasso;
    this.data = new Request.Builder(uri, resourceId, picasso.defaultBitmapConfig);
    init();
  }

  RequestCreator(Picasso picasso, String url) {
    this.picasso = picasso;
    this.data = new Request.Builder(url, picasso.defaultBitmapConfig);
    init();
  }

  private void init() {
    checkMain();
  }

  public RequestCreator noPlaceholder() {
    check(!setPlaceholder);
    noFade = false;
    return this;
  }

  public RequestCreator placeholder(@DrawableRes int placeholderResId) {
    check(setPlaceholder);
    check(placeholderResId != 0);
    this.placeholderResId = placeholderResId;
    return this;
  }

  public RequestCreator placeholder(Drawable placeholderDrawable) {
    check(setPlaceholder);
    check(placeholderResId == 0);
    this.placeholderDrawable = placeholderDrawable;
    return this;
  }

  public RequestCreator error(@DrawableRes int errorResId) {
    check(errorResId != 0);
    this.errorResId = errorResId;
    return this;
  }

  public RequestCreator error(Drawable errorDrawable) {
    check(errorResId == 0);
    this.errorDrawable = errorDrawable;
    return this;
  }

  public RequestCreator tag(Object tag) {
    data.tag(tag);
    this.tag = tag;
    return this;
  }

  public RequestCreator fit() {
    deferred = true;
    return this;
  }

    @JvmName("-unfit")
    public RequestCreator unfit() {
        this.deferred = false;
        return this;
    }

    @JvmName("-clearTag")
    public RequestCreator clearTag() {
        this.data.clearTag();
        return this;
    }

public RequestCreator resizeDimen(
    @DimenRes int targetWidthResId,
    @DimenRes int targetHeightResId
) {
    Resources resources = picasso.context.getResources();
    int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
    int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
    return resize(targetWidth, targetHeight);
}

public RequestCreator resize(int targetWidth, int targetHeight) {
    data.resize(targetWidth, targetHeight);
    return this;
}

public RequestCreator centerCrop() {
    data.centerCrop(Gravity.CENTER);
    return this;
}

public RequestCreator centerCrop(int alignGravity) {
    data.centerCrop(alignGravity);
    return this;
}

public RequestCreator centerInside() {
    data.centerInside();
    return this;
}

public RequestCreator onlyScaleDown() {
    data.onlyScaleDown();
    return this;
}


  public RequestCreator rotate(float degrees) {
    data.rotate(degrees);
    return this;
  }

  public RequestCreator rotate(float degrees, float pivotX, float pivotY) {
    data.rotate(degrees, pivotX, pivotY);
    return this;
  }

  public RequestCreator config(Bitmap.Config config) {
    data.config(config);
    return this;
  }

  public RequestCreator stableKey(String stableKey) {
    data.stableKey(stableKey);
    return this;
  }

  public RequestCreator priority(Picasso.Priority priority) {
    data.priority(priority);
    return this;
  }

  public RequestCreator transform(Transformation transformation) {
    data.transform(transformation);
    return this;
  }

  public RequestCreator transform(List<Transformation> transformations) {
    data.transform(transformations);
    return this;
  }

  public RequestCreator memoryPolicy(MemoryPolicy policy, MemoryPolicy... additional) {
    data.memoryPolicy(policy, additional);
    return this;
  }

  public RequestCreator networkPolicy(NetworkPolicy policy, NetworkPolicy... additional) {
    data.networkPolicy(policy, additional);
    return this;
  }

  public RequestCreator addHeader(String key, String value) {
    data.addHeader(key, value);
    return this;
  }

  public RequestCreator noFade() {
    // Assuming 'noFade' is an instance variable of the enclosing class
    noFade = true;
    return this;
  }

  // Assuming 'picasso', 'checkNotMain', 'deferred', 'shouldWriteToMemoryCache', and 'hunt' are defined in the enclosing class
  @Throws(IOException.class)
  public Bitmap get() {
    long started = System.nanoTime();
    checkNotMain();
    check(!deferred, "Fit cannot be used with get.");
    if (!data.hasImage()) {
      return null;
    }

    Request request = createRequest(started);
    GetAction action = new GetAction(picasso, request);
    Result result = forRequest(picasso, picasso.dispatcher, picasso.cache, action).hunt();
    if (result == null) {
      return null;
    }

    Bitmap bitmap = result.bitmap;
    if (shouldWriteToMemoryCache(request.memoryPolicy)) {
      picasso.cache.put(request.key, bitmap);
    }

    return bitmap;
  }


@MainThread
  public void fetch(@Nullable Callback callback) {
    checkNotMain();
    check(!deferred);

    if (!data.hasImage()) {
      return;
    }

    if (!data.hasPriority()) {
      data.priority(Priority.LOW);
    }

    Request request = createRequest();
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
      if (bitmap != null) {
        if (picasso.loggingEnabled) {
          log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + LoadedFrom.MEMORY);
        }
        callback.onSuccess();
        return;
      }
    }

    FetchAction action = new FetchAction(picasso, request, callback);
    picasso.submit(action);
  }


  


public void into(BitmapTarget target) {
    long started = System.nanoTime();
    checkMain();
    check(!deferred, "Fit cannot be used with a Target.");

    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      target.onPrepareLoad(getPlaceholderDrawable() != null ? getPlaceholderDrawable() : null);
      return;
    }

    Request request = createRequest(started);
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        target.onBitmapLoaded(bitmap, LoadedFrom.MEMORY);
        return;
      }
    }

    target.onPrepareLoad(getPlaceholderDrawable() != null ? getPlaceholderDrawable() : null);
    BitmapTargetAction action = new BitmapTargetAction(picasso, target, request, errorDrawable, errorResId);
    picasso.enqueueAndSubmit(action);
  }

  public void into(DrawableTarget target) {
    long started = System.nanoTime();
    checkMain();
    check(!deferred, "Fit cannot be used with a Target.");

    Drawable placeHolderDrawable = getPlaceholderDrawable() != null ? getPlaceholderDrawable() : null;
    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      target.onPrepareLoad(placeHolderDrawable);
      return;
    }

    Request request = createRequest(started);
    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        target.onDrawableLoaded(
          new PicassoDrawable(
            picasso.context,
            bitmap,
            null,
            LoadedFrom.MEMORY,
            noFade,
            picasso.indicatorsEnabled
          ),
          LoadedFrom.MEMORY
        );
        return;
      }
    }

    target.onPrepareLoad(placeHolderDrawable);
    DrawableTargetAction action = new DrawableTargetAction(picasso, target, request, noFade, placeHolderDrawable, errorDrawable, errorResId);
    picasso.enqueueAndSubmit(action);
  }

  public void into(
    RemoteViews remoteViews,
    int viewId,
    int notificationId,
    Notification notification,
    String notificationTag,
    Callback callback
  ) {
    long started = System.nanoTime();
    check(!deferred, "Fit cannot be used with RemoteViews.");
    require(placeholderDrawable == null && errorDrawable == null,
      "Cannot use placeholder or error drawables with remote views."
    );

    Request request = createRequest(started);
    NotificationAction action = new NotificationAction(
      picasso,
      request,
      errorResId,
      new RemoteViewsTarget(remoteViews, viewId),
      notificationId,
      notification,
      notificationTag,
      callback
    );
    performRemoteViewInto(request, action);
  }

  public void into(
    RemoteViews remoteViews,
    int viewId,
    int appWidgetId,
    Callback callback
  ) {
    into(remoteViews, viewId, appWidgetId, null, callback);
  }

  public void into(
    RemoteViews remoteViews,
    int viewId,
    int[] appWidgetIds,
    Callback callback
  ) {
    long started = System.nanoTime();
    check(!deferred, "Fit cannot be used with RemoteViews.");
    require(placeholderDrawable == null && errorDrawable == null,
      "Cannot use placeholder or error drawables with remote views."
    );

    Request request = createRequest(started);
    AppWidgetAction action = new AppWidgetAction(
      picasso,
      request,
      errorResId,
      new RemoteViewsTarget(remoteViews, viewId),
      appWidgetIds,
      callback
    );

    performRemoteViewInto(request, action);
  }

  public void into(ImageView target, Callback callback) {
    long started = System.nanoTime();
    checkMain();

    if (!data.hasImage()) {
      picasso.cancelRequest(target);
      if (setPlaceholder) {
        setPlaceholder(target, getPlaceholderDrawable());
      }
      return;
    }

    if (deferred) {
      check(!data.hasSize(), "Fit cannot be used with resize.");
      int width = target.getWidth();
      int height = target.getHeight();
      if (width == 0 || height == 0) {
        if (setPlaceholder) {
          setPlaceholder(target, getPlaceholderDrawable());
        }
        picasso.defer(target, new DeferredRequestCreator(this, target, callback));
        return;
      }
      data.resize(width, height);
    }

    Request request = createRequest(started);

    if (shouldReadFromMemoryCache(request.memoryPolicy)) {
      Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
      if (bitmap != null) {
        picasso.cancelRequest(target);
        RequestHandler.Result result = RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
        setResult(target, picasso.context, result, noFade, picasso.indicatorsEnabled);
        if (picasso.isLoggingEnabled) {
          log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + LoadedFrom.MEMORY);
        }
        callback.onSuccess();
        return;
      }
    }

    if (setPlaceholder) {
      setPlaceholder(target, getPlaceholderDrawable());
    }

    ImageViewAction action = new ImageViewAction(
      picasso,
      target,
      request,
      errorDrawable,
      errorResId,
      noFade,
      callback
    );

    picasso.enqueueAndSubmit(action);
  }








private Drawable getPlaceholderDrawable() {
  Context context;
  Drawable placeholderDrawable;
  if ((placeholderResId == 0)) {
    return placeholderDrawable;
  } else {
    context = picasso.context;
    return ContextCompat.getDrawable(context, placeholderResId);
  }
}

Request createRequest(long started) {
  int id;
  Request request;
  id = nextId.getAndIncrement();
  request = data.build();
  request.id = id;
  request.started = started;
  boolean loggingEnabled = picasso.isLoggingEnabled;
  if (loggingEnabled) {
    log(OWNER_MAIN, Utils.VERB_CREATED, request.plainId(), request.toString());
  }
  Request transformed = picasso.transformRequest(request);
  if (transformed != request) {
    transformed.id = id;
    transformed.started = started;
    if (loggingEnabled) {
      log(OWNER_MAIN, Utils.VERB_CHANGED, transformed.logId(), "into " + transformed);
    }
  }
  return transformed;
}

void performRemoteViewInto(Request request, RemoteViewsAction action) {
  if (shouldReadFromMemoryCache(request.memoryPolicy)) {
    Bitmap bitmap;
    bitmap = picasso.quickMemoryCacheCheck(action.request.key);
    if (bitmap != null) {
      action.complete(new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY));
      return;
    }
  }
  if (placeholderResId != 0) {
    action.setImageResource(placeholderResId);
  }
  picasso.enqueueAndSubmit(action);
}

  private static AtomicInteger nextId = new AtomicInteger();
}