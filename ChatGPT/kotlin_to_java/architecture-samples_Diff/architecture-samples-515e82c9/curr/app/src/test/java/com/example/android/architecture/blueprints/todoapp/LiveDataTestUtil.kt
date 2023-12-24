
package com.example.android.architecture.blueprints.todoapp;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
public class LiveDataUtils {

    public static <T> T awaitNextValue(LiveData<T> liveData, long time, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
        T data = null;
        CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T o) {
                data = o;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };
        liveData.observeForever(observer);

        if (!latch.await(time, timeUnit)) {
            throw new TimeoutException("LiveData value was never set.");
        }

        return data;
    }
}
