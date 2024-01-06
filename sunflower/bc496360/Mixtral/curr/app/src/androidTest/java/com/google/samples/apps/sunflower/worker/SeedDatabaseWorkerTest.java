package com.google.samples.apps.sunflower.worker;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.work.SynchronousExecutor;
import androidx.test.work.TestingOnlyWorkerBuilder;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.workDataOf;
import com.google.samples.apps.sunflower.utilities.PLANT_DATA_FILENAME;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RefreshMainDataWorkTest {

  private Context context;
  private WorkManager workManager;
  private Configuration configuration;

  @Rule
  public WorkManagerTestInitHelper workManagerTestInitHelper = new WorkManagerTestInitHelper(
    InstrumentationRegistry.getInstrumentation().getTargetContext(),
    configuration
  );

  @Before
  public void setup() {
    configuration =
      new Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .setExecutor(new SynchronousExecutor())
        .build();

    context = ApplicationProvider.getApplicationContext();
    workManager = WorkManager.getInstance(context);
  }

  @Test
  public void testRefreshMainDataWork() {
    ListenableWorker worker = TestListenableWorkerBuilder
      .create(
        context,
        workDataOf(SeedDatabaseWorker.KEY_FILENAME, PLANT_DATA_FILENAME)
      )
      .build();

    ListenableWorker.Result result = worker.startWork().get();

    MatcherAssert.assertThat(
      result,
      CoreMatchers.is(ListenableWorker.Result.success())
    );
  }
}
