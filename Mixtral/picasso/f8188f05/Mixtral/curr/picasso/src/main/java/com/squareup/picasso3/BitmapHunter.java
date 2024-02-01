package com.squareup.picasso3;

import android.net.NetworkInfo;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Utils;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import kotlinx.coroutines.Job;

public class BitmapHunter implements Runnable {
  private final Picasso picasso;
  private final Dispatcher dispatcher;
  private final PlatformLruCache cache;
  private final Action action;
  private final RequestHandler requestHandler;
  private int sequence;
  private Picasso.Priority priority;
  private Request data;
  private String key;
  private int retryCount;
  private Action actionRef;
  private List < Action > actions;
  private Future < ? > future;
  private Job job;
  private Result result;
  private Exception exception;

  public BitmapHunter(Picasso picasso, Dispatcher dispatcher, PlatformLruCache cache, Action action, RequestHandler requestHandler) {
    this.picasso = picasso;
    this.dispatcher = dispatcher;
    this.cache = cache;
    this.action = action;
    this.requestHandler = requestHandler;
    this.sequence = SEQUENCE_GENERATOR.incrementAndGet();
    this.priority = action.getRequest().getPriority();
    this.data = action.getRequest();
    this.key = action.getRequest().getKey();
    this.retryCount = requestHandler.getRetryCount();
    this.actionRef = action;
  }

  @Override
  public void run() {
    String originalName = Thread.currentThread().getName();
    try {
      Thread.currentThread().setName(getName());

      if (picasso.isLoggingEnabled()) {
        Utils.log(Utils.OWNER_HUNTER, Utils.VERB_EXECUTING, getLogIdsForHunter(this));
      }

      result = hunt();
      dispatcher.dispatchComplete(this);
    } catch (IOException e) {
      exception = e;
      if (retryCount > 0) {
        dispatcher.dispatchRetry(this);
      } else {
        dispatcher.dispatchFailed(this);
      }
    } catch (Exception e) {
      exception = e;
      dispatcher.dispatchFailed(this);
    } finally {
      Thread.currentThread().setName(originalName);
    }
  }

  public String getName() {
    StringBuilder sb = NAME_BUILDER.get();
    String name = data.getName();
    sb.ensureCapacity(Utils.THREAD_PREFIX.length() + name.length());
    sb.replace(Utils.THREAD_PREFIX.length(), sb.length(), name);
    return sb.toString();
  }

  public Result hunt() {
    if (MemoryPolicy.shouldReadFromMemoryCache(data.getMemoryPolicy())) {
      Bitmap bitmap = cache.get(key);
      if (bitmap != null) {
        picasso.cacheHit();
        if (picasso.isLoggingEnabled()) {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_DECODED, data.logId(), "from cache");
        }

        return new Bitmap(bitmap, LoadedFrom.MEMORY);
      }
    }

    if (retryCount == 0) {
      data = data.newBuilder().networkPolicy(NetworkPolicy.OFFLINE).build();
    }

    AtomicReference < Result > resultReference = new AtomicReference < > ();
    AtomicReference < Throwable > exceptionReference = new AtomicReference < > ();

    CountDownLatch latch = new CountDownLatch(1);
    try {
      requestHandler.load(
        picasso,
        data,
        new RequestHandler.Callback() {
          @Override
          public void onSuccess(Result result) {
            resultReference.set(result);
            latch.countDown();
          }

          @Override
          public void onError(Throwable t) {
            exceptionReference.set(t);
            latch.countDown();
          }
        }
      );

      latch.await();
    } catch (InterruptedException e) {
      InterruptedIOException interruptedIoException = new InterruptedIOException();
      interruptedIoException.initCause(e);
      throw interruptedIoException;
    }

    Throwable throwable = exceptionReference.get();
    if (throwable != null) {
      if (throwable instanceof IOException || throwable instanceof Error || throwable instanceof RuntimeException) {
        throw (RuntimeException) throwable;
      } else {
        throw new RuntimeException(throwable);
      }
    }

    Result result = resultReference.get();
    Bitmap bitmap = result.getBitmap();
    if (picasso.isLoggingEnabled()) {
      Utils.log(Utils.OWNER_HUNTER, Utils.VERB_DECODED, data.logId());
    }
    picasso.bitmapDecoded(bitmap);

    List < Transformation > transformations = new ArrayList < > (data.getTransformations().size() + 1);
    if (data.needsMatrixTransform() || result.getExifRotation() != 0) {
      transformations.add(new MatrixTransformation(data));
    }
    transformations.addAll(data.getTransformations());

    Result transformedResult = applyTransformations(picasso, data, transformations, result);
    Bitmap transformedBitmap = transformedResult.getBitmap();
    picasso.bitmapTransformed(transformedBitmap);

    return transformedResult;
  }

  public void attach(Action action) {
    boolean loggingEnabled = picasso.isLoggingEnabled();
    Request request = action.getRequest();
    if (this.action == null) {
      this.action = action;
      if (loggingEnabled) {
        if (actions == null) {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_JOINED, request.logId(), "to empty hunter");
        } else {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
        }
      }

      return;
    }

    if (actions == null) {
      actions = new ArrayList < > (3);
    }
    actions.add(action);

    if (loggingEnabled) {
      Utils.log(Utils.OWNER_HUNTER, Utils.VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
    }

    Picasso.Priority actionPriority = action.getRequest().getPriority();
    if (actionPriority.ordinal > priority.ordinal) {
      priority = actionPriority;
    }
  }

  public void detach(Action action) {
    boolean detached = false;
    if (this.action == action) {
      this.action = null;
      detached = true;
    } else {
      if (actions != null) {
        detached = actions.remove(action);
      }
    }

    if (detached && action.getRequest().getPriority() == priority) {
      priority = computeNewPriority();
    }

    if (picasso.isLoggingEnabled()) {
      Utils.log(Utils.OWNER_HUNTER, Utils.VERB_REMOVED, action.getRequest().logId(), getLogIdsForHunter(this, "from "));
    }
  }

  public boolean cancel() {
    if (action == null && (actions == null || actions.isEmpty())) {
      if (future != null) {
        return future.cancel(false);
      }
      if (job != null) {
        job.cancel();
        return true;
      }
    }
    return false;
  }

  public boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
    boolean hasRetries = retryCount > 0;
    if (!hasRetries) {
      return false;
    }
    retryCount--;

    return requestHandler.shouldRetry(airplaneMode, info);
  }

  public boolean supportsReplay() {
    return requestHandler.supportsReplay();
  }

  private Picasso.Priority computeNewPriority() {
    boolean hasMultiple = (actions != null && !actions.isEmpty());
    boolean hasAny = (action != null || hasMultiple);

    if (!hasAny) {
      return Picasso.Priority.LOW;
    }

    Picasso.Priority newPriority = (action != null) ? action.getRequest().getPriority() : Picasso.Priority.LOW;

    if (actions != null) {
      for (int i = 0; i < actions.size(); i++) {
        Action a = actions.get(i);
        Picasso.Priority priority = a.getRequest().getPriority();
        if (priority.ordinal > newPriority.ordinal) {
          newPriority = priority;
        }
      }
    }

    return newPriority;
  }

  private static final String THREAD_PREFIX = "Thread-";

  private static final ThreadLocal NAME_BUILDER = new ThreadLocal() {
    @Override
    protected StringBuilder initialValue() {
      return new StringBuilder(THREAD_PREFIX);
    }
  };

  private static final AtomicInteger SEQUENCE_GENERATOR = new AtomicInteger();

  private static final RequestHandler ERRORING_HANDLER = new RequestHandler() {
    @Override
    public boolean canHandleRequest(Request data) {
      return true;
    }

    @Override
    public void load(Picasso picasso, Request request, Callback callback) {
      callback.onError(new IllegalStateException("Unrecognized type of request: " + request));
    }
  };

  public static BitmapHunter forRequest(Picasso picasso, Dispatcher dispatcher, PlatformLruCache cache, Action action) {
    Request request = action.getRequest();
    List requestHandlers = picasso.getRequestHandlers();

    // Index-based loop to avoid allocating an iterator.
    for (int i = 0; i < requestHandlers.size(); i++) {
      RequestHandler requestHandler = requestHandlers.get(i);
      if (requestHandler.canHandleRequest(request)) {
        return new BitmapHunter(picasso, dispatcher, cache, action, requestHandler);
      }
    }

    return new BitmapHunter(picasso, dispatcher, cache, action, ERRORING_HANDLER);
  }

  public static Result applyTransformations(
    Picasso picasso,
    Request data,
    List < Transformation > transformations,
    Bitmap result
  ) {
    Bitmap bitmap = result;

    for (int i = 0; i < transformations.size(); i++) {
      Transformation transformation = transformations.get(i);
      Result newResult = null;
      try {
        newResult = new Result(transformation.transform(bitmap), result.getWidth(), result.getHeight());
        if (picasso.isLoggingEnabled()) {
          Utils.log(Utils.OWNER_HUNTER, Utils.VERB_TRANSFORMED, data.logId(), "from transformations");
        }
      } catch (RuntimeException e) {
        picasso.getHandler().post(() -> {
          throw new RuntimeException("Transformation " + transformation.key() + " crashed with exception.", e);
        });

        return null;
      }

      bitmap = newResult.getBitmap();
      if (bitmap.isRecycled()) {
        picasso.getHandler().post(() -> {
          throw new IllegalStateException("Transformation " + transformation.key() + " returned a recycled Bitmap.");
        });

        return null;
      }
    }

    return new Result(bitmap, result.getWidth(), result.getHeight());
  }
}