package com.squareup.picasso3;

import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Looper;
import android.view.Gravity;
import androidx.annotation.DrawableRes;
import androidx.annotation.Px;
import com.squareup.picasso3.Picasso.Priority;
import com.squareup.picasso3.Picasso.Priority.NORMAL;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Request {
  
  public int id = 0;
  public long started = 0;
  public final int memoryPolicy;
  public final int networkPolicy;
  public final Uri uri;
  public final int resourceId;
  public final String stableKey;
  public List<Transformation> transformations;
  public final int targetWidth;
  public final int targetHeight;
  public final boolean centerCrop;
  public final int centerCropGravity;
  public final boolean centerInside;
  public final boolean onlyScaleDown;
  public final float rotationDegrees;
  public final float rotationPivotX;
  public final float rotationPivotY;
  public final boolean hasRotationPivot;
  public final Config config;
  public final Priority priority;
  public String key;
  public final Object tag;

  public Request(Builder builder) {
    this.id = 0;
    this.started = 0;
    this.memoryPolicy = builder.memoryPolicy;
    this.networkPolicy = builder.networkPolicy;
    this.uri = builder.uri;
    this.resourceId = builder.resourceId;
    this.stableKey = builder.stableKey;
    this.transformations = (builder.transformations == null) ? new ArrayList<>() : new ArrayList<>(builder.transformations);
    this.targetWidth = builder.targetWidth;
    this.targetHeight = builder.targetHeight;
    this.centerCrop = builder.centerCrop;
    this.centerCropGravity = builder.centerCropGravity;
    this.centerInside = builder.centerInside;
    this.onlyScaleDown = builder.onlyScaleDown;
    this.rotationDegrees = builder.rotationDegrees;
    this.rotationPivotX = builder.rotationPivotX;
    this.rotationPivotY = builder.rotationPivotY;
    this.hasRotationPivot = builder.hasRotationPivot;
    this.config = builder.config;
    this.priority = checkNotNull(builder.priority);
    this.key = (Looper.myLooper() == Looper.getMainLooper()) ? createKey() : createKey(new StringBuilder());
    this.tag = builder.tag;
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("Request{");
    if (resourceId > 0) {
      result.append(resourceId);
    } else {
      result.append(uri);
    }
    for (Transformation transformation : transformations) {
      result.append(' ');
      result.append(transformation.key());
    }
    if (stableKey != null) {
      result.append(" stableKey(");
      result.append(stableKey);
      result.append(')');
    }
    if (targetWidth > 0) {
      result.append(" resize(");
      result.append(targetWidth);
      result.append(',');
      result.append(targetHeight);
      result.append(')');
    }
    if (centerCrop) {
      result.append(" centerCrop");
    }
    if (centerInside) {
      result.append(" centerInside");
    }
    if (rotationDegrees != 0f) {
      result.append(" rotation(");
      result.append(rotationDegrees);
      if (hasRotationPivot) {
        result.append(" @ ");
        result.append(rotationPivotX);
        result.append(',');
        result.append(rotationPivotY);
      }
      result.append(')');
    }
    if (config != null) {
      result.append(' ');
      result.append(config);
    }
    result.append('}');
    return result.toString();
  }

  public String logId() {
    long delta = System.nanoTime() - started;
    return (delta > TOO_LONG_LOG) ? plainId() + '+' + TimeUnit.NANOSECONDS.toSeconds(delta) + 's' :
            plainId() + '+' + TimeUnit.NANOSECONDS.toMillis(delta) + "ms";
  }

  public String plainId() {
    return "[R" + id + "]";
  }

  public String name() {
    return (uri != null) ? uri.getPath() : Integer.toHexString(resourceId);
  }

  public boolean hasSize() {
    return targetWidth != 0 || targetHeight != 0;
  }

  public boolean needsMatrixTransform() {
    return hasSize() || rotationDegrees != 0f;
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  private String createKey() {
    String result = createKey(Utils.MAIN_THREAD_KEY_BUILDER);
    Utils.MAIN_THREAD_KEY_BUILDER.setLength(0);
    return result;
  }

  private String createKey(StringBuilder builder) {
    Request data = this;
    if (data.stableKey != null) {
      builder.ensureCapacity(data.stableKey.length() + KEY_PADDING);
      builder.append(data.stableKey);
    } else if (data.uri != null) {
      String path = data.uri.toString();
      builder.ensureCapacity(path.length() + KEY_PADDING);
      builder.append(path);
    } else {
      builder.ensureCapacity(KEY_PADDING);
      builder.append(data.resourceId);
    }

    builder.append(KEY_SEPARATOR);

    if (data.rotationDegrees != 0f) {
      builder.append("rotation:").append(data.rotationDegrees);

      if (data.hasRotationPivot) {
        builder.append('@').append(data.rotationPivotX).append('x').append(data.rotationPivotY);
      }

      builder.append(KEY_SEPARATOR);
    }

    if (data.hasSize()) {
      builder.append("resize:").append(data.targetWidth).append('x').append(data.targetHeight);
      builder.append(KEY_SEPARATOR);
    }

    if (data.centerCrop) {
      builder.append("centerCrop:").append(data.centerCropGravity).append(KEY_SEPARATOR);
    } else if (data.centerInside) {
      builder.append("centerInside").append(KEY_SEPARATOR);
    }

    for (int i = 0; i < data.transformations.size(); i++) {
      builder.append(data.transformations.get(i).key());
      builder.append(KEY_SEPARATOR);
    }

    return builder.toString();
  }

  public class Builder {

    public Uri uri;
    public int resourceId;
    public String stableKey;
    public int targetWidth;
    public int targetHeight;
    public boolean centerCrop;
    public int centerCropGravity;
    public boolean centerInside;
    public boolean onlyScaleDown;
    public float rotationDegrees;
    public float rotationPivotX;
    public float rotationPivotY;
    public boolean hasRotationPivot;
    public List<Transformation> transformations;
    public Config config;
    public Priority priority;
    public Object tag;
    public int memoryPolicy;
    public int networkPolicy;

    public Builder(Uri uri) {
        setUri(uri);
    }

    public Builder(@DrawableRes int resourceId) {
        setResourceId(resourceId);
    }

    internal Builder(
            Uri uri,
            int resourceId,
            Config bitmapConfig
    ) {
        this.uri = uri;
        this.resourceId = resourceId;
        config = bitmapConfig;
    }

    internal Builder(Request request) {
        uri = request.uri;
        resourceId = request.resourceId;
        stableKey = request.stableKey;
        targetWidth = request.targetWidth;
        targetHeight = request.targetHeight;
        centerCrop = request.centerCrop;
        centerInside = request.centerInside;
        centerCropGravity = request.centerCropGravity;
        rotationDegrees = request.rotationDegrees;
        rotationPivotX = request.rotationPivotX;
        rotationPivotY = request.rotationPivotY;
        hasRotationPivot = request.hasRotationPivot;
        onlyScaleDown = request.onlyScaleDown;
        transformations = new ArrayList<>(request.transformations);
        config = request.config;
        priority = request.priority;
        memoryPolicy = request.memoryPolicy;
        networkPolicy = request.networkPolicy;
    }

    public boolean hasImage() {
        return uri != null || resourceId != 0;
    }

    public boolean hasSize() {
        return targetWidth != 0 || targetHeight != 0;
    }

    public boolean hasPriority() {
        return priority != null;
    }

    public Builder setUri(Uri uri) {
        this.uri = uri;
        resourceId = 0;
        return this;
    }

    public Builder setResourceId(@DrawableRes int resourceId) {
        if (resourceId == 0) {
            throw new IllegalArgumentException("Image resource ID may not be 0.");
        }
        this.resourceId = resourceId;
        uri = null;
        return this;
    }

    public Builder stableKey(String stableKey) {
        this.stableKey = stableKey;
        return this;
    }

    public Builder tag(Object tag) {
        if (this.tag != null) {
            throw new IllegalStateException("Tag already set.");
        }
        this.tag = tag;
        return this;
    }

    public Builder clearTag() {
        tag = null;
        return this;
    }

    public Builder resize(@Px int targetWidth, @Px int targetHeight) {
        if (targetWidth < 0 || targetHeight < 0 || (targetHeight == 0 && targetWidth == 0)) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        return this;
    }

    public Builder clearResize() {
        targetWidth = 0;
        targetHeight = 0;
        centerCrop = false;
        centerInside = false;
        return this;
    }

    public Builder centerCrop(int alignGravity) {
        if (centerInside) {
            throw new IllegalStateException("Center crop can not be used after calling centerInside");
        }
        centerCrop = true;
        centerCropGravity = alignGravity;
        return this;
    }

    public Builder clearCenterCrop() {
        centerCrop = false;
        centerCropGravity = Gravity.CENTER;
        return this;
    }

    public Builder centerInside() {
        if (centerCrop) {
            throw new IllegalStateException("Center inside can not be used after calling centerCrop");
        }
        centerInside = true;
        return this;
    }

    public Builder clearCenterInside() {
        centerInside = false;
        return this;
    }

    public Builder onlyScaleDown() {
        if (targetHeight == 0 && targetWidth == 0) {
            throw new IllegalStateException("onlyScaleDown can not be applied without resize");
        }
        onlyScaleDown = true;
        return this;
    }

    public Builder clearOnlyScaleDown() {
        onlyScaleDown = false;
        return this;
    }

    public Builder rotate(float degrees) {
        rotationDegrees = degrees;
        return this;
    }

    public Builder rotate(float degrees, float pivotX, float pivotY) {
        rotationDegrees = degrees;
        rotationPivotX = pivotX;
        rotationPivotY = pivotY;
        hasRotationPivot = true;
        return this;
    }

    public Builder clearRotation() {
        rotationDegrees = 0f;
        rotationPivotX = 0f;
        rotationPivotY = 0f;
        hasRotationPivot = false;
        return this;
    }

    public Builder config(Config config) {
        this.config = config;
        return this;
    }

    public Builder priority(Priority priority) {
        if (this.priority != null) {
            throw new IllegalStateException("Priority already set.");
        }
        this.priority = priority;
        return this;
    }

    public Builder transform(Transformation transformation) {
        if (transformation.key() == null) {
            throw new IllegalArgumentException("Transformation key must not be null.");
        }
        if (transformations == null) {
            transformations = new ArrayList<>(2);
        }
        transformations.add(transformation);
        return this;
    }

    public Builder transform(List<Transformation> transformations) {
        for (Transformation transformation : transformations) {
            transform(transformation);
        }
        return this;
    }

    public Builder memoryPolicy(MemoryPolicy policy, MemoryPolicy... additional) {
        memoryPolicy |= policy.index;

        for (MemoryPolicy memPolicy : additional) {
            memoryPolicy |= memPolicy.index;
        }
        return this;
    }

    public Builder networkPolicy(NetworkPolicy policy, NetworkPolicy... additional) {
        networkPolicy |= policy.index;

        for (NetworkPolicy netPolicy : additional) {
            networkPolicy |= netPolicy.index;
        }
        return this;
    }

    public Request build() {
        if (centerInside && centerCrop) {
            throw new IllegalStateException("Center crop and center inside can not be used together.");
        }
        if (centerCrop && targetWidth == 0 && targetHeight == 0) {
            throw new IllegalStateException("Center crop requires calling resize with positive width and height.");
        }
        if (centerInside && targetWidth == 0 && targetHeight == 0) {
            throw new IllegalStateException("Center inside requires calling resize with positive width and height.");
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        return new Request(this);
    }
}

  internal static final long TOO_LONG_LOG = TimeUnit.SECONDS.toNanos(5);
  private static final int KEY_PADDING = 50; // Determined by exact science.
  public static final char KEY_SEPARATOR = '\n';
}