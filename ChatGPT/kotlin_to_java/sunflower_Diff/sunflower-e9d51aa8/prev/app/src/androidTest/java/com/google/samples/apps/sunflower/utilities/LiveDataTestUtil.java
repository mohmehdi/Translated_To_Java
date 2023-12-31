package com.google.samples.apps.sunflower.utilities;

import android.arch.lifecycle.LiveData;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LiveDataTestUtil {
    public static <T> T getValue(LiveData<T> liveData) throws InterruptedException {
        final Object[] data = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        liveData.observeForever(new Observer<T>() {
            @Override
            public void onChanged(T o) {
                data[0] = o;
                latch.countDown();
            }
        });
        latch.await(2, TimeUnit.SECONDS);

        return (T) data[0];
    }
}