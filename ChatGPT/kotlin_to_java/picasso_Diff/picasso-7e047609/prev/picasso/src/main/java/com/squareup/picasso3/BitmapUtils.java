
package com.squareup.picasso3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.TypedValue;
import androidx.annotation.DrawableRes;
import androidx.annotation.RequiresApi;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Source;
import okio.buffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import kotlin.math.max;
import kotlin.math.min;

public class BitmapUtils {

  public static BitmapFactory.Options createBitmapOptions(Request data) {
    boolean justBounds = data.hasSize();
    if (justBounds || data.getConfig() != null || data.isPurgeable()) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = justBounds;
      options.inInputShareable = data.isPurgeable();
      options.inPurgeable = data.isPurgeable();
      if (data.getConfig() != null) {
        options.inPreferredConfig = data.getConfig();
      }
      return options;
    } else {
      return null;
    }
  }

  public static boolean requiresInSampleSize(BitmapFactory.Options options) {
    return options != null && options.inJustDecodeBounds;
  }

  public static void calculateInSampleSize(
      int reqWidth,
      int reqHeight,
      BitmapFactory.Options options,
      Request request) {
    calculateInSampleSize(
        reqWidth, reqHeight, options.outWidth, options.outHeight, options, request);
  }

  public static boolean shouldResize(
      boolean onlyScaleDown,
      int inWidth,
      int inHeight,
      int targetWidth,
      int targetHeight) {
    return (!onlyScaleDown
        || (targetWidth != 0 && inWidth > targetWidth)
        || (targetHeight != 0 && inHeight > targetHeight));
  }

  public static void calculateInSampleSize(
      int requestWidth,
      int requestHeight,
      int width,
      int height,
      BitmapFactory.Options options,
      Request request) {
    options.inSampleSize =
        ratio(requestWidth, requestHeight, width, height, request);
    options.inJustDecodeBounds = false;
  }

  public static Bitmap decodeStream(Source source, Request request) {
    ExceptionCatchingSource exceptionCatchingSource = new ExceptionCatchingSource(source);
    BufferedSource bufferedSource = exceptionCatchingSource.buffer();
    Bitmap bitmap;
    if (VERSION.SDK_INT >= 28) {
      bitmap = decodeStreamP(request, bufferedSource);
    } else {
      bitmap = decodeStreamPreP(request, bufferedSource);
    }
    exceptionCatchingSource.throwIfCaught();
    return bitmap;
  }

  @RequiresApi(28)
  @SuppressLint("Override")
  private static Bitmap decodeStreamP(Request request, BufferedSource bufferedSource) {
    ImageDecoder.Source imageSource =
        ImageDecoder.createSource(ByteBuffer.wrap(bufferedSource.readByteArray()));
    return decodeImageSource(imageSource, request);
  }

  private static Bitmap decodeStreamPreP(Request request, BufferedSource bufferedSource) {
    boolean isWebPFile = Utils.isWebPFile(bufferedSource);
    boolean isPurgeable =
        request.isPurgeable() && VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    BitmapFactory.Options options = createBitmapOptions(request);
    boolean calculateSize = requiresInSampleSize(options);

    Bitmap bitmap;
    if (isWebPFile || isPurgeable) {
      byte[] bytes = bufferedSource.readByteArray();
      if (calculateSize) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        calculateInSampleSize(
            request.getTargetWidth(),
            request.getTargetHeight(),
            options,
            request);
      }
      bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    } else {
      if (calculateSize) {
        BitmapFactory.decodeStream(bufferedSource.peek().inputStream(), null, options);
        calculateInSampleSize(
            request.getTargetWidth(),
            request.getTargetHeight(),
            options,
            request);
      }
      bitmap = BitmapFactory.decodeStream(bufferedSource.inputStream(), null, options);
    }
    if (bitmap == null) {
      throw new IOException("Failed to decode bitmap.");
    }
    return bitmap;
  }

  public static Bitmap decodeResource(Context context, Request request) {
    if (VERSION.SDK_INT >= 28) {
      return decodeResourceP(context, request);
    }
    Resources resources = Utils.getResources(context, request);
    int id = Utils.getResourceId(resources, request);
    return decodeResourcePreP(resources, id, request);
  }

  @RequiresApi(28)
  private static Bitmap decodeResourceP(Context context, Request request) {
    ImageDecoder.Source imageSource =
        ImageDecoder.createSource(context.getResources(), request.getResourceId());
    return decodeImageSource(imageSource, request);
  }

  private static Bitmap decodeResourcePreP(Resources resources, int id, Request request) {
    BitmapFactory.Options options = createBitmapOptions(request);
    if (requiresInSampleSize(options)) {
      BitmapFactory.decodeResource(resources, id, options);
      calculateInSampleSize(
          request.getTargetWidth(),
          request.getTargetHeight(),
          options,
          request);
    }
    return BitmapFactory.decodeResource(resources, id, options);
  }

  @RequiresApi(28)
  private static Bitmap decodeImageSource(ImageDecoder.Source imageSource, Request request) {
    return ImageDecoder.decodeBitmap(
        imageSource,
        (imageDecoder, imageInfo, source) -> {
          imageDecoder.setMutableRequired(true);
          if (request.hasSize()) {
            ImageDecoder.Size size = imageInfo.getSize();
            int width = size.getWidth();
            int height = size.getHeight();
            int targetWidth = request.getTargetWidth();
            int targetHeight = request.getTargetHeight();
            if (shouldResize(
                request.isOnlyScaleDown(),
                width,
                height,
                targetWidth,
                targetHeight)) {
              int ratio = ratio(targetWidth, targetHeight, width, height, request);
              imageDecoder.setTargetSize(width / ratio, height / ratio);
            }
          }
        });
  }

  private static int ratio(
      int requestWidth,
      int requestHeight,
      int width,
      int height,
      Request request) {
    if (height > requestHeight || width > requestWidth) {
      int ratio;
      if (requestHeight == 0) {
        ratio = width / requestWidth;
      } else if (requestWidth == 0) {
        ratio = height / requestHeight;
      } else {
        int heightRatio = height / requestHeight;
        int widthRatio = width / requestWidth;
        if (request.isCenterInside()) {
          ratio = max(heightRatio, widthRatio);
        } else {
          ratio = min(heightRatio, widthRatio);
        }
      }
      if (ratio != 0) {
        return ratio;
      } else {
        return 1;
      }
    } else {
      return 1;
    }
  }

  public static boolean isXmlResource(Resources resources, @DrawableRes int drawableId) {
    TypedValue typedValue = new TypedValue();
    resources.getValue(drawableId, typedValue, true);
    CharSequence file = typedValue.string;
    return file != null && file.toString().endsWith(".xml");
  }

  public static class ExceptionCatchingSource extends ForwardingSource {
    private IOException thrownException;

    public ExceptionCatchingSource(Source delegate) {
      super(delegate);
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      try {
        return super.read(sink, byteCount);
      } catch (IOException e) {
        thrownException = e;
        throw e;
      }
    }

    public void throwIfCaught() throws IOException {
      if (thrownException instanceof IOException) {
        throw thrownException;
      }
    }
  }
}