

package com.squareup.leakcanary.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FutureResult<T> {

  private final AtomicReference<T> resultHolder = new AtomicReference<>();
  private final CountDownLatch latch = new CountDownLatch(1);

  public boolean wait(long timeout, TimeUnit unit) {
    try {
      return latch.await(timeout, unit);
    } catch (InterruptedException e) {
      throw new RuntimeException("Did not expect thread to be interrupted", e);
    }
  }

  public T get() {
    if (latch.getCount() > 0) {
      throw new IllegalStateException("Call wait() and check its result");
    }
    return resultHolder.get();
  }

  public void set(T result) {
    resultHolder.set(result);
    latch.countDown();
  }
}