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

  
  public final boolean purgeable;

  
  public final Config config;

  
  public final Priority priority;

  
  public String key;

  
  public final Object tag;

  public Request(Builder builder) {
    id = 0;
    started = 0;
    memoryPolicy = builder.memoryPolicy;
    networkPolicy = builder.networkPolicy;
    uri = builder.uri;
    resourceId = builder.resourceId;
    stableKey = builder.stableKey;
    transformations = (builder.transformations == null) ? new ArrayList<>() : new ArrayList<>(builder.transformations);
    targetWidth = builder.targetWidth;
    targetHeight = builder.targetHeight;
    centerCrop = builder.centerCrop;
    centerCropGravity = builder.centerCropGravity;
    centerInside = builder.centerInside;
    onlyScaleDown = builder.onlyScaleDown;
    rotationDegrees = builder.rotationDegrees;
    rotationPivotX = builder.rotationPivotX;
    rotationPivotY = builder.rotationPivotY;
    hasRotationPivot = builder.hasRotationPivot;
    purgeable = builder.purgeable;
    config = builder.config;
    priority = checkNotNull(builder.priority);
    key = (Looper.myLooper() == Looper.getMainLooper()) ? createKey() : createKey(new StringBuilder());
    tag = builder.tag;
  }

  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("Request{");
    if (resourceId > 0) {
      stringBuilder.append(resourceId);
    } else {
      stringBuilder.append(uri);
    }
    for (Transformation transformation : transformations) {
      stringBuilder.append(' ');
      stringBuilder.append(transformation.key());
    }
    if (stableKey != null) {
      stringBuilder.append(" stableKey(");
      stringBuilder.append(stableKey);
      stringBuilder.append(')');
    }
    if (targetWidth > 0) {
      stringBuilder.append(" resize(");
      stringBuilder.append(targetWidth);
      stringBuilder.append(',');
      stringBuilder.append(targetHeight);
      stringBuilder.append(')');
    }
    if (centerCrop) {
      stringBuilder.append(" centerCrop");
    }
    if (centerInside) {
      stringBuilder.append(" centerInside");
    }
    if (rotationDegrees != 0f) {
      stringBuilder.append(" rotation(");
      stringBuilder.append(rotationDegrees);
      if (hasRotationPivot) {
        stringBuilder.append(" @ ");
        stringBuilder.append(rotationPivotX);
        stringBuilder.append(',');
        stringBuilder.append(rotationPivotY);
      }
      stringBuilder.append(')');
    }
    if (purgeable) {
      stringBuilder.append(" purgeable");
    }
    if (config != null) {
      stringBuilder.append(' ');
      stringBuilder.append(config);
    }
    stringBuilder.append('}');
    return stringBuilder.toString();
  }

  public String logId() {
    long delta = System.nanoTime() - started;
    if (delta > TOO_LONG_LOG) {
      return plainId() + '+' + TimeUnit.NANOSECONDS.toSeconds(delta) + 's';
    } else {
      return plainId() + '+' + TimeUnit.NANOSECONDS.toMillis(delta) + 'ms';
    }
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
      builder
        .append("rotation:")
        .append(data.rotationDegrees);

      if (data.hasRotationPivot) {
        builder
          .append('@')
          .append(data.rotationPivotX)
          .append('x')
          .append(data.rotationPivotY);
      }

      builder.append(KEY_SEPARATOR);
    }

    if (data.hasSize()) {
      builder
        .append("resize:")
        .append(data.targetWidth)
        .append('x')
        .append(data.targetHeight);

      builder.append(KEY_SEPARATOR);
    }

    if (data.centerCrop) {
      builder
        .append("centerCrop:")
        .append(data.centerCropGravity)
        .append(KEY_SEPARATOR);
    } else if (data.centerInside) {
      builder
        .append("centerInside")
        .append(KEY_SEPARATOR);
    }

    for (int i = 0; i < data.transformations.size(); i++) {
      builder.append(data.transformations.get(i).key());
      builder.append(KEY_SEPARATOR);
    }

    return builder.toString();
  }

  public static class Builder {
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
    public boolean purgeable;
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
      purgeable = request.purgeable;
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
      if (targetWidth < 0) {
        throw new IllegalArgumentException("Width must be a positive number or 0.");
      }
      if (targetHeight < 0) {
        throw new IllegalArgumentException("Height must be a positive number or 0.");
      }
      if (targetHeight == 0 && targetWidth == 0) {
        throw new IllegalArgumentException("At least one dimension has to be a positive number.");
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
        throw new IllegalStateException("Center crop cannot be used after calling centerInside");
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
        throw new IllegalStateException("Center inside cannot be used after calling centerCrop");
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
        throw new IllegalStateException("onlyScaleDown cannot be applied without resize");
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

    public Builder purgeable() {
      purgeable = true;
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
        throw new IllegalStateException("Transformation key must not be null.");
      }
      if (transformations == null) {
        transformations = new ArrayList<>(2);
      }
      transformations.add(transformation);
      return this;
    }

    public Builder transform(List<Transformation> transformations) {
      for (int i = 0; i < transformations.size(); i++) {
        transform(transformations.get(i));
      }
      return this;
    }

    public Builder memoryPolicy(MemoryPolicy policy, MemoryPolicy... additional) {
      memoryPolicy |= policy.index;

      for (int i = 0; i < additional.length; i++) {
        memoryPolicy |= additional[i].index;
      }
      return this;
    }

    public Builder networkPolicy(NetworkPolicy policy, NetworkPolicy... additional) {
      networkPolicy |= policy.index;

      for (int i = 0; i < additional.length; i++) {
        networkPolicy |= additional[i].index;
      }
      return this;
    }

    public Request build() {
      if (centerInside && centerCrop) {
        throw new IllegalStateException("Center crop and center inside cannot be used together.");
      }
      if (centerCrop && targetWidth == 0 && targetHeight == 0) {
        throw new IllegalStateException("Center crop requires calling resize with positive width and height.");
      }
      if (centerInside && targetWidth == 0 && targetHeight == 0) {
        throw new IllegalStateException("Center inside requires calling resize with positive width and height.");
      }
      if (priority == null) {
        priority = NORMAL;
      }
      return new Request(this);
    }
  }

  private static final long TOO_LONG_LOG = TimeUnit.SECONDS.toNanos(5);
  private static final int KEY_PADDING = 50;
  private static final char KEY_SEPARATOR = '\n';
}