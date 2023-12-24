package com.google.samples.apps.sunflower.worker;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkManager;
import androidx.work.testing.TestListenableWorkerBuilder;
import com.google.samples.apps.sunflower.workers.SeedDatabaseWorker;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RefreshMainDataWorkTest {
    private Context context;
    private WorkManager workManager;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        workManager = WorkManager.getInstance(context);
    }

    @Test
    public void testRefreshMainDataWork() {
        TestListenableWorkerBuilder<SeedDatabaseWorker> builder = new TestListenableWorkerBuilder<>(context);
        SeedDatabaseWorker worker = builder.build();

        Result result = worker.startWork().get();

        Assert.assertThat(result, CoreMatchers.is(Result.success()));
    }
}