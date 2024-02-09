package com.squareup.picasso3;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.RemoteViews;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.core.content.ContextCompat;
import com.squareup.picasso3.BitmapHunter.Companion.forRequest;
import com.squareup.picasso3.MemoryPolicy.Companion.shouldReadFromMemoryCache;
import com.squareup.picasso3.MemoryPolicy.Companion.shouldWriteToMemoryCache;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.PicassoDrawable.Companion.setPlaceholder;
import com.squareup.picasso3.PicassoDrawable.Companion.setResult;
import com.squareup.picasso3.RemoteViewsAction.AppWidgetAction;
import com.squareup.picasso3.RemoteViewsAction.NotificationAction;
import com.squareup.picasso3.RemoteViewsAction.RemoteViewsTarget;
import com.squareup.picasso3.Utils.OWNER_MAIN;
import com.squareup.picasso3.Utils.VERB_COMPLETED;
import com.squareup.picasso3.Utils.checkMain;
import com.squareup.picasso3.Utils.checkNotMain;
import com.squareup.picasso3.Utils.log;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestCreator {
    private final Picasso picasso;
    private final Request.Builder data;

    private boolean noFade = false;
    private boolean deferred = false;
    private boolean setPlaceholder = true;
    @DrawableRes
    private int placeholderResId = 0;
    @DrawableRes
    private int errorResId = 0;
    private Drawable placeholderDrawable = null;
    private Drawable errorDrawable = null;

    @JvmName("-tag")
    public Object tag() {
        return data.tag;
    }

    public RequestCreator(Picasso picasso, Uri uri, int resourceId) {
        this.picasso = picasso;
        this.data = new Request.Builder(uri, resourceId, picasso.defaultBitmapConfig);
        check(!picasso.shutdown, "Picasso instance already shut down. Cannot submit new requests.");
    }

    public RequestCreator noPlaceholder() {
        check(placeholderResId == 0, "Placeholder resource already set.");
        check(placeholderDrawable == null, "Placeholder image already set.");
        setPlaceholder = false;
        return this;
    }

    public RequestCreator placeholder(@DrawableRes int placeholderResId) {
        check(setPlaceholder, "Already explicitly declared as no placeholder.");
        require(placeholderResId != 0, "Placeholder image resource invalid.");
        check(placeholderDrawable == null, "Placeholder image already set.");
        this.placeholderResId = placeholderResId;
        return this;
    }

    public RequestCreator placeholder(Drawable placeholderDrawable) {
        check(setPlaceholder, "Already explicitly declared as no placeholder.");
        check(placeholderResId == 0, "Placeholder image already set.");
        this.placeholderDrawable = placeholderDrawable;
        return this;
    }

    public RequestCreator error(@DrawableRes int errorResId) {
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

    @JvmName("-unfit")
    public RequestCreator unfit() {
        deferred = false;
        return this;
    }

    @JvmName("-clearTag")
    public RequestCreator clearTag() {
        data.clearTag();
        return this;
    }

    public RequestCreator resizeDimen(@DimenRes int targetWidthResId, @DimenRes int targetHeightResId) {
        int targetWidth = picasso.context.getResources().getDimensionPixelSize(targetWidthResId);
        int targetHeight = picasso.context.getResources().getDimensionPixelSize(targetHeightResId);
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

    public RequestCreator purgeable() {
        data.purgeable();
        return this;
    }

    public RequestCreator noFade() {
        noFade = true;
        return this;
    }

    public Bitmap get() throws IOException {
        long started = System.nanoTime();
        checkNotMain();
        check(!deferred, "Fit cannot be used with get.");
        if (!data.hasImage()) {
            return null;
        }

        Request request = createRequest(started);
        GetAction action = new GetAction(picasso, request);
        RequestHandler.Result result = forRequest(picasso, picasso.dispatcher, picasso.cache, action).hunt();
        if (result == null) {
            return null;
        }

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

    public void into(RemoteViews remoteViews, @IdRes int viewId, int notificationId, Notification notification, String notificationTag, Callback callback) {
        long started = System.nanoTime();
        check(!deferred, "Fit cannot be used with RemoteViews.");
        require(!(placeholderDrawable != null || errorDrawable != null), "Cannot use placeholder or error drawables with remote views.");

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

    public void into(RemoteViews remoteViews, @IdRes int viewId, int appWidgetId, Callback callback) {
        into(remoteViews, viewId, new int[]{appWidgetId}, callback);
    }

    public void into(RemoteViews remoteViews, @IdRes int viewId, int[] appWidgetIds, Callback callback) {
        long started = System.nanoTime();
        check(!deferred, "Fit cannot be used with remote views.");
        require(!(placeholderDrawable != null || errorDrawable != null), "Cannot use placeholder or error drawables with remote views.");

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
        return placeholderResId == 0 ? placeholderDrawable :
                ContextCompat.getDrawable(picasso.context, placeholderResId);
    }

    private Request createRequest(long started) {
        int id = nextId.getAndIncrement();
        Request request = data.build();
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

        private static final AtomicInteger nextId = new AtomicInteger();
}