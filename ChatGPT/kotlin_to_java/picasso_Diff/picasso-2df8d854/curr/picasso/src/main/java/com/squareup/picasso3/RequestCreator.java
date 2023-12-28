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
    private boolean noFade;
    private boolean deferred;
    private boolean setPlaceholder;
    @DrawableRes
    private int placeholderResId;
    @DrawableRes
    private int errorResId;
    private Drawable placeholderDrawable;
    private Drawable errorDrawable;

    private Object tag;

    public RequestCreator(Picasso picasso, Uri uri, int resourceId) {
        this.picasso = picasso;
        this.data = new Request.Builder(uri, resourceId, picasso.defaultBitmapConfig);
        this.setPlaceholder = true;
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public RequestCreator noPlaceholder() {
        check(placeholderResId == 0, "Placeholder resource already set.");
        check(placeholderDrawable == null, "Placeholder image already set.");
        setPlaceholder = false;
        return this;
    }

    public RequestCreator placeholder(int placeholderResId) {
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

    public RequestCreator resizeDimen(@DimenRes int targetWidthResId, @