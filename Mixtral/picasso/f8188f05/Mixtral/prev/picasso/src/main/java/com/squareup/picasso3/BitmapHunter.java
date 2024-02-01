package com.squareup.picasso3;

import android.net.NetworkInfo;
import com.squareup.picasso3.MemoryPolicy;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Utils;
import com.squareup.picasso3.Utils.OWNER_HUNTER;
import com.squareup.picasso3.Utils.THREAD_PREFIX;
import com.squareup.picasso3.Utils.VERB_DECODED;
import com.squareup.picasso3.Utils.VERB_EXECUTING;
import com.squareup.picasso3.Utils.VERB_JOINED;
import com.squareup.picasso3.Utils.VERB_REMOVED;
import com.squareup.picasso3.Utils.VERB_TRANSFORMED;
import com.squareup.picasso3.Utils.getLogIdsForHunter;
import com.squareup.picasso3.Utils.log;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.concurrent.AtomicInteger;
import java.util.concurrent.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
  private Action currentAction;
  private List<Action> actions;
  private Future<?> future;
  private Result<?> result;
  private Exception exception;

  public BitmapHunter(
      Picasso picasso, Dispatcher dispatcher, PlatformLruCache cache, Action action,
      RequestHandler requestHandler) {
    this.picasso = picasso;
    this.dispatcher = dispatcher;
    this.cache = cache;
    this.action = action;
    this.requestHandler = requestHandler;
    this.sequence = Utils.SEQUENCE_GENERATOR.incrementAndGet();
    this.priority = action.request.priority;
    this.data = action.request;
    this.key = action.request.key;
    this.retryCount = requestHandler.retryCount;
  }

  @Override
  public void run() {
    String originalName = Thread.currentThread().getName();
    try {
      updateThreadName(data);

      if (picasso.isLoggingEnabled) {
        log(OWNER_HUNTER, VERB_EXECUTING, getLogIdsForHunter(this));
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

  public Result<?> hunt() {
    if (MemoryPolicy.shouldReadFromMemoryCache(data.memoryPolicy)) {
      Bitmap bitmap = cache.get(key);
      if (bitmap != null) {
        picasso.cacheHit();
        if (picasso.isLoggingEnabled) {
          log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache");
        }

        return new Result.Bitmap(bitmap, LoadedFrom.MEMORY);
      }
    }

    if (retryCount == 0) {
      data = data.newBuilder().networkPolicy(NetworkPolicy.OFFLINE).build();
    }

    AtomicReference<Result<?>> resultReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();

    CountDownLatch latch = new CountDownLatch(1);
    try {
      requestHandler.load(
          picasso,
          data,
          new RequestHandler.Callback() {
            @Override
            public void onSuccess(Result<?> result) {
              resultReference.set(result);
              latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
              exceptionReference.set(t);
              latch.countDown();
            }
          });

      latch.await();
    } catch (InterruptedException e) {
      InterruptedIOException interruptedIoException = new InterruptedIOException();
      interruptedIoException.initCause(e);
      throw interruptedIoException;
    }

    Throwable throwable = exceptionReference.get();
    if (throwable != null) {
      if (throwable instanceof IOException
          || throwable instanceof Error
          || throwable instanceof RuntimeException) {
        throw (RuntimeException) throwable;
      } else {
        throw new RuntimeException(throwable);
      }
    }

    Result<?> result = resultReference.get();
    if (result == null || !(result.getData() instanceof Bitmap)) {
      return null;
    }
    Bitmap bitmap = (Bitmap) result.getData();
    if (picasso.isLoggingEnabled) {
      log(OWNER_HUNTER, VERB_DECODED, data.logId());
    }
    picasso.bitmapDecoded(bitmap);

    List<Transformation> transformations = new ArrayList<>(data.transformations.size() + 1);
    if (data.needsMatrixTransform() || result.exifRotation != 0) {
      transformations.add(new MatrixTransformation(data));
    }
    transformations.addAll(data.transformations);

    Result<?> transformedResult =
        applyTransformations(picasso, data, transformations, result);
    if (transformedResult == null) {
      return null;
    }
    Bitmap transformedBitmap = (Bitmap) transformedResult.getData();
    picasso.bitmapTransformed(transformedBitmap);

    return transformedResult;
  }

  public void attach(Action action) {
    boolean loggingEnabled = picasso.isLoggingEnabled();
    Request request = action.request;
    if (this.action == null) {
      this.action = action;
      if (loggingEnabled) {
        if (actions == null) {
          log(OWNER_HUNTER, VERB_JOINED, request.logId(), "to empty hunter");
        } else {
          log(
              OWNER_HUNTER,
              VERB_JOINED,
              request.logId(),
              getLogIdsForHunter(this, "to "));
        }
      }

      return;
    }

    if (actions == null) {
      actions = new CopyOnWriteArrayList<>();
    }
    actions.add(action);

    if (loggingEnabled) {
      log(
          OWNER_HUNTER,
          VERB_JOINED,
          request.logId(),
          getLogIdsForHunter(this, "to "));
    }

    Picasso.Priority actionPriority = action.request.priority;
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
      detached = actions != null && actions.remove(action);
    }

    if (detached && action.request.priority == priority) {
      priority = computeNewPriority();
    }

    if (picasso.isLoggingEnabled) {
      log(
          OWNER_HUNTER,
          VERB_REMOVED,
          action.request.logId(),
          getLogIdsForHunter(this, "from "));
    }
  }

  public boolean cancel() {
    if (action == null && actions == null) {
      if (future != null) {
        return future.cancel(false);
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
    boolean hasMultiple = actions != null && !actions.isEmpty();
    boolean hasAny = action != null || hasMultiple;

    if (!hasAny) {
      return Picasso.Priority.LOW;
    }

    Picasso.Priority newPriority =
        action != null ? action.request.priority : Picasso.Priority.LOW;

    if (actions != null) {
      for (int i = 0; i < actions.size(); i++) {
        Action a = actions.get(i);
        Picasso.Priority priority = a.request.priority;
        if (priority.ordinal > newPriority.ordinal) {
          newPriority = priority;
        }
      }
    }

    return newPriority;
  }





private static final ThreadLocal NAME_BUILDER = new ThreadLocal() {
  @Override
  protected StringBuilder initialValue() {
    return new StringBuilder(THREAD_PREFIX);
  }
};
public static final AtomicInteger SEQUENCE_GENERATOR = new AtomicInteger();
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

public static BitmapHunter forRequest(
  Picasso picasso,
  Dispatcher dispatcher,
  PlatformLruCache cache,
  Action action) {
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





  public static void updateThreadName(Request data) {
    String name = data.name;
    StringBuilder builder = Utils.NAME_BUILDER.get();
    builder.setLength(THREAD_PREFIX.length);
    builder.append(name);

    Thread.currentThread().setName(builder.toString());
  }

  public static <T> Result<T> applyTransformations(
      Picasso picasso,
      Request data,
      List<Transformation> transformations,
      Result<T> result) {
    T res = result.getData();

    for (int i = 0; i < transformations.size(); i++) {
      Transformation transformation = transformations.get(i);
      Result<T> newResult = null;
      try {
        newResult =
            new Result<>(transformation.transform(res), result.source, result.exifRotation);
        if (picasso.isLoggingEnabled) {
          log(
              OWNER_HUNTER,
              VERB_TRANSFORMED,
              data.logId(),
              "from transformations");
        }
      } catch (RuntimeException e) {
        picasso.handler.post(
            () -> {
              throw new RuntimeException(
                  "Transformation "
                      + transformation.key()
                      + " crashed with exception.",
                  e);
            });

        return null;
      }

      if (newResult == null) {
        return null;
      }

      res = newResult.getData();
    }

    return newResult;
  }
}