package com.google.samples.apps.iosched.test.util;

import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import com.google.samples.apps.iosched.shared.domain.internal.SyncScheduler;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class SyncTaskExecutorRule extends TestWatcher {

    @Override
    protected void starting(Description description) {
        super.starting(description);
        DefaultScheduler.setDelegate(SyncScheduler.INSTANCE);
    }

    @Override
    protected void finished(Description description) {
        super.finished(description);
        DefaultScheduler.setDelegate(null);
    }
}