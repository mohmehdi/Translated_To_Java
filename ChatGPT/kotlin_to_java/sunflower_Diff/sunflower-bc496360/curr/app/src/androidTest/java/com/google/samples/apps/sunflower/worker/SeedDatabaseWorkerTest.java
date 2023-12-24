
package com.google.samples.apps.sunflower.worker;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;
import androidx.work.Data;

import com.google.samples.apps.sunflower.utilities.PLANT_DATA_FILENAME;
import com.google.samples.apps.sunflower.workers.SeedDatabaseWorker;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RefreshMainDataWorkTest {
    private WorkManager workManager;
    private Context context;
    private Configuration configuration;

    @Before
    public void setup() {
        configuration = new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();

        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);
        workManager = WorkManager.getInstance(context);
    }

    @Test
    public void testRefreshMainDataWork() {
        Data inputData = new Data.Builder()
                .putString(SeedDatabaseWorker.KEY_FILENAME, PLANT_DATA_FILENAME)
                .build();

        SeedDatabaseWorker worker = new TestListenableWorkerBuilder<>(
                context,
                SeedDatabaseWorker.class,
                inputData
        ).build();

        Result result = worker.startWork().get();

        Assert.assertThat(result, CoreMatchers.is(Result.success()));
    }
}
