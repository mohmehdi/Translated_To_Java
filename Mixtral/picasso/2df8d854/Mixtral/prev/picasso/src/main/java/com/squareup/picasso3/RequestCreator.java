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
import android.os.Message;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Transformation;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.squareup.picasso3.Utils.checkMain;
import static com.squareup.picasso3.Utils.checkNotMain;
import static com.squareup.picasso3.Utils.log;

public class RequestCreator {
  private static final AtomicInteger nextId = new AtomicInteger();

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

  public RequestCreator(Picasso picasso, Uri uri, int resourceId) {
    checkNotMain();
    check(!picasso.isShutdown());
    this.picasso = picasso;
    this.data = new Request.Builder(uri, resourceId, picasso.defaultBitmapConfig);
  }

  @NonNull
  public RequestCreator noPlaceholder() {
    check(!picasso.isShutdown());
    check(placeholderResId == 0);
    check(placeholderDrawable == null);
    setPlaceholder = false;
    return this;
  }

  @NonNull
  public RequestCreator placeholder(@DrawableRes int placeholderResId) {
    check(!picasso.isShutdown());
    require(placeholderResId != 0);
    check(placeholderDrawable == null);
    this.placeholderResId = placeholderResId;
    return this;
  }


      public RequestCreator placeholder(Drawable placeholderDrawable) {
        check(setPlaceholder);
        check(placeholderResId == 0);
        this.placeholderDrawable = placeholderDrawable;
        return this;
    }

    public RequestCreator error(int errorResId) {
        require(errorResId != 0, "Error image resource invalid.");
        check(errorDrawable == null, "Error image already set.");
        this.errorResId = errorResId;
        return this;
    }

    public RequestCreator error(Drawable errorDrawable) {
        check(errorResId == 0, "Error image already set.");
        this.errorDrawable = errorDrawable;
        return this;
    }

    public RequestCreator tag(Object tag) {
        data.tag(tag);
        return this;
    }

    public RequestCreator fit() {
        deferred = true;
        return this;
    }

    public RequestCreator unfit() {
        deferred = false;
        return this;
    }

    public RequestCreator clearTag() {
        data.clearTag();
        return this;
    }

    public RequestCreator resizeDimen(int targetWidthResId, int targetHeightResId) {
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
        this.policy = policy;
        this.additionalPolicies = Arrays.asList(additional);
        data.memoryPolicy(policy, additional);
        return this;
    }

    public RequestCreator networkPolicy(NetworkPolicy policy, NetworkPolicy... additional) {
        this.networkPolicy = policy;
        this.additionalNetworkPolicies = Arrays.asList(additional);
        data.networkPolicy(policy, additional);
        return this;
    }

    public RequestCreator addHeader(String key, String value) {
        data.addHeader(key, value);
        return this;
    }

    public RequestCreator noFade() {
        this.noFade = true;
        return this;
    }

    @Throws(IOException.class)
    public Bitmap get() throws IOException {
        long started = System.nanoTime();
        checkNotMain();
        check(!deferred, "Fit cannot be used with get.");
        if (!data.hasImage()) {
            return null;
        }

        Request request = createRequest(started);
        GetAction action = new GetAction(picasso, request);
        Bitmap result = forRequest(picasso, picasso.dispatcher, picasso.cache, action).hunt()
                : null;

        Bitmap bitmap = result.bitmap;
        if (shouldWriteToMemoryCache(request.memoryPolicy)) {
            picasso.cache.put(request.key, bitmap);
        }

        return bitmap;
    }

    public void fetch(Callback callback) {
        long started = System.nanoTime();
        check(!deferred, "Fit cannot be used with fetch.");

        if (data.hasImage()) {

            if (!data.hasPriority()) {
                data.priority(Picasso.Priority.LOW);
            }

            Request request = createRequest(started);
            if (shouldReadFromMemoryCache(request.memoryPolicy)) {
                Bitmap bitmap = picasso.quickMemoryCacheCheck(request.key);
                if (bitmap != null) {
                    if (picasso.isLoggingEnabled) {
                        log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + LoadedFrom.MEMORY);
                    }
                    callback.onSuccess();
                    return;
                }
            }

            FetchAction action = new FetchAction(picasso, request, callback);
            picasso.submit(action);
        }
    }

    public void into(BitmapTarget target) {
        long started = System.nanoTime();
        checkMain();
        check(!deferred, "Fit cannot be used with a Target.");

        if (!data.hasImage()) {
            picasso.cancelRequest(target);
            target.onPrepareLoad(setPlaceholder ? getPlaceholderDrawable() : null);
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

        target.onPrepareLoad(setPlaceholder ? getPlaceholderDrawable() : null);
        BitmapTargetAction action = new BitmapTargetAction(picasso, target, request, errorDrawable, errorResId);
        picasso.enqueueAndSubmit(action);
    }

    public void into(RemoteViews remoteViews, int viewId, int notificationId, Notification notification, String notificationTag, Callback callback) {
        long started = System.nanoTime();
        check(!deferred, "Fit cannot be used with RemoteViews.");
        require(placeholderDrawable == null && errorDrawable == null, "Cannot use placeholder or error drawables with remote views.");

        Request request = createRequest(started);
        NotificationAction action = new NotificationAction(picasso, request, errorResId, new RemoteViewsTarget(remoteViews, viewId), notificationId, notification, notificationTag, callback);
        performRemoteViewInto(request, action);
    }

    public void into(RemoteViews remoteViews, int viewId, int appWidgetId, Callback callback) {
        into(remoteViews, viewId, appWidgetId, null, callback);
    }

    public void into(RemoteViews remoteViews, int viewId, int[] appWidgetIds, Callback callback) {
        long started = System.nanoTime();
        check(!deferred, "Fit cannot be used with remote views.");
        require(placeholderDrawable == null && errorDrawable == null, "Cannot use placeholder or error drawables with remote views.");

        Request request = createRequest(started);
        AppWidgetAction action = new AppWidgetAction(picasso, request, errorResId, new RemoteViewsTarget(remoteViews, viewId), appWidgetIds, callback);

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
                RequestHandler.Result result = new RequestHandler.Result.Bitmap(bitmap, LoadedFrom.MEMORY);
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

        ImageViewAction action = new ImageViewAction(picasso, target, request, errorDrawable, errorResId, noFade, callback);

        picasso.enqueueAndSubmit(action);
    }

    private Drawable getPlaceholderDrawable() {
        return placeholderResId == 0 ? placeholderDrawable : ContextCompat.getDrawable(picasso.context, placeholderResId);
    }

    private Request createRequest(long started) {
        int id = nextId.getAndIncrement();
        Request request = data.build();
        request.id = id;
        request.started = started;

        if (picasso.isLoggingEnabled) {
            log(OWNER_MAIN, Utils.VERB_CREATED, request.plainId(), request.toString());
        }

        Request transformed = picasso.transformRequest(request);
        if (transformed != request) {

            transformed.id = id;
            transformed.started = started;
            if (picasso.isLoggingEnabled) {
                log(OWNER_MAIN, Utils.VERB_CHANGED, transformed.logId(), "into " + transformed);
            }
        }

        return transformed;
    }

    private void performRemoteViewInto(Request request, RemoteViewsAction action) {
        if (shouldReadFromMemoryCache(request.memoryPolicy)) {
            Bitmap bitmap = picasso.quickMemoryCacheCheck(action.request.key);
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

private final AtomicInteger nextId = new AtomicInteger();  

}
