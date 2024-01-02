package com.squareup.picasso3;

import android.net.NetworkInfo;

import com.squareup.picasso3.MemoryPolicy.Companion.shouldReadFromMemoryCache;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.RequestHandler.Result.Bitmap;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import kotlinx.coroutines.Job;

internal open class BitmapHunter implements Runnable {
    private static final ThreadLocal<StringBuilder> NAME_BUILDER = new ThreadLocal<StringBuilder>() {
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

    private final Picasso picasso;
    private final Dispatcher dispatcher;
    private final PlatformLruCache cache;
    private final Action action;
    private final RequestHandler requestHandler;

    private final int sequence = SEQUENCE_GENERATOR.incrementAndGet();
    private Picasso.Priority priority = action.request.priority;
    private Request data = action.request;
    private final String key = action.request.key;
    private int retryCount = requestHandler.retryCount;

    private Action action;
    private List<Action> actions;

    private Future<?> future;
    private Job job;

    private RequestHandler.Result result;
    private Exception exception;

    BitmapHunter(Picasso picasso, Dispatcher dispatcher, PlatformLruCache cache, Action action, RequestHandler requestHandler) {
        this.picasso = picasso;
        this.dispatcher = dispatcher;
        this.cache = cache;
        this.action = action;
        this.requestHandler = requestHandler;
    }

    @Override
    public void run() {
        String originalName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(getName());

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

    String getName() {
        return NAME_BUILDER.get().also(it -> {
            String name = data.name;
            it.ensureCapacity(THREAD_PREFIX.length() + name.length());
            it.replace(THREAD_PREFIX.length(), it.length(), name);
        }).toString();
    }

    Bitmap hunt() {
        if (shouldReadFromMemoryCache(data.memoryPolicy)) {
            Bitmap bitmap = cache.get(key);
            if (bitmap != null) {
                picasso.cacheHit();
                if (picasso.isLoggingEnabled) {
                    log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache");
                }
                return new Bitmap(bitmap, LoadedFrom.MEMORY);
            }
        }

        if (retryCount == 0) {
            data = data.newBuilder().networkPolicy(NetworkPolicy.OFFLINE).build();
        }

        AtomicReference<RequestHandler.Result> resultReference = new AtomicReference<>();
        AtomicReference<Throwable> exceptionReference = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        try {
            requestHandler.load(picasso, data, new RequestHandler.Callback() {
                @Override
                public void onSuccess(RequestHandler.Result result) {
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
        } catch (InterruptedException ie) {
            InterruptedIOException interruptedIoException = new InterruptedIOException();
            interruptedIoException.initCause(ie);
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

        RequestHandler.Result result = resultReference.get();
        if (result instanceof Bitmap) {
            Bitmap bitmap = ((Bitmap) result).bitmap;
            if (picasso.isLoggingEnabled) {
                log(OWNER_HUNTER, VERB_DECODED, data.logId());
            }
            picasso.bitmapDecoded(bitmap);

            List<Transformation> transformations = new ArrayList<>(data.transformations.size() + 1);
            if (data.needsMatrixTransform() || result.exifRotation != 0) {
                transformations.add(new MatrixTransformation(data));
            }
            transformations.addAll(data.transformations);

            RequestHandler.Result transformedResult = applyTransformations(picasso, data, transformations, result);
            if (transformedResult == null) {
                return null;
            }

            Bitmap transformedBitmap = transformedResult.bitmap;
            picasso.bitmapTransformed(transformedBitmap);

            return transformedResult;
        } else {
            return null;
        }
    }

    void attach(Action action) {
        boolean loggingEnabled = picasso.isLoggingEnabled;
        Request request = action.request;
        if (this.action == null) {
            this.action = action;
            if (loggingEnabled) {
                if (actions == null || actions.isEmpty()) {
                    log(OWNER_HUNTER, VERB_JOINED, request.logId(), "to empty hunter");
                } else {
                    log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
                }
            }
            return;
        }

        if (actions == null) {
            actions = new ArrayList<>(3);
        }
        actions.add(action);

        if (loggingEnabled) {
            log(OWNER_HUNTER, VERB_JOINED, request.logId(), getLogIdsForHunter(this, "to "));
        }

        Picasso.Priority actionPriority = action.request.priority;
        if (actionPriority.ordinal() > priority.ordinal()) {
            priority = actionPriority;
        }
    }

    void detach(Action action) {
        boolean detached;
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
            log(OWNER_HUNTER, VERB_REMOVED, action.request.logId(), getLogIdsForHunter(this, "from "));
        }
    }

    boolean cancel() {
        if (action == null && (actions == null || actions.isEmpty())) {
            return future != null && future.cancel(false);
        } else if (job != null) {
            job.cancel();
            return true;
        } else {
            return false;
        }
    }

    boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
        boolean hasRetries = retryCount > 0;
        if (!hasRetries) {
            return false;
        }
        retryCount--;

        return requestHandler.shouldRetry(airplaneMode, info);
    }

    boolean supportsReplay() {
        return requestHandler.supportsReplay();
    }

    private Picasso.Priority computeNewPriority() {
        boolean hasMultiple = actions != null && !actions.isEmpty();
        boolean hasAny = action != null || hasMultiple;

        if (!hasAny) {
            return Picasso.Priority.LOW;
        }

        Picasso.Priority newPriority = action != null ? action.request.priority : Picasso.Priority.LOW;

        if (actions != null) {
            for (Action a : actions) {
                Picasso.Priority priority = a.request.priority;
                if (priority.ordinal() > newPriority.ordinal()) {
                    newPriority = priority;
                }
            }
        }

        return newPriority;
    }

    static BitmapHunter forRequest(Picasso picasso, Dispatcher dispatcher, PlatformLruCache cache, Action action) {
        Request request = action.request;
        List<RequestHandler> requestHandlers = picasso.requestHandlers;

        for (RequestHandler requestHandler : requestHandlers) {
            if (requestHandler.canHandleRequest(request)) {
                return new BitmapHunter(picasso, dispatcher, cache, action, requestHandler);
            }
        }

        return new BitmapHunter(picasso, dispatcher, cache, action, ERRORING_HANDLER);
    }

    static RequestHandler.Result applyTransformations(Picasso picasso, Request data, List<Transformation> transformations, RequestHandler.Result result) {
        RequestHandler.Result res = result;

        for (Transformation transformation : transformations) {
            try {
                RequestHandler.Result transformedResult = transformation.transform(res);
                if (picasso.isLoggingEnabled) {
                    log(OWNER_HUNTER, VERB_TRANSFORMED, data.logId(), "from transformations");
                }
                res = transformedResult;
            } catch (RuntimeException e) {
                Picasso.HANDLER.post(() -> {
                    throw new RuntimeException("Transformation " + transformation.key() + " crashed with exception.", e);
                });
                return null;
            }

            Bitmap bitmap = res.bitmap;
            if (bitmap.isRecycled()) {
                Picasso.HANDLER.post(() -> {
                    throw new IllegalStateException("Transformation " + transformation.key() + " returned a recycled Bitmap.");
                });
                return null;
            }
        }

        return res;
    }
}
