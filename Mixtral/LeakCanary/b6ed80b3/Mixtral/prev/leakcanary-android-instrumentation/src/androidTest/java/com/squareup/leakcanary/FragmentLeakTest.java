

package com.squareup.leakcanary;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownLatch;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ActivityTestRule;
import leaksentry.LeakSentry;
import leaksentry.internal.ActivityLifecycleCallbacksAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FragmentLeakTest {

  @Rule
  public ActivityTestRule<TestActivity> activityRule = new ActivityTestRule<>(TestActivity.class, !TOUCH_MODE, !LAUNCH_ACTIVITY);

  @Before
  public void setUp() {
    LeakSentry.refWatcher.clearWatchedReferences();
  }

  @After
  public void tearDown() {
    LeakSentry.refWatcher.clearWatchedReferences();
  }

  @Test
  public void fragmentShouldLeak() throws InterruptedException, ExecutionException, TimeoutException {
    startActivityAndWaitForCreate();

    LeakingFragment.add(activityRule.getActivity());

    CountDownLatch waitForFragmentDetach = activityRule.getActivity().waitForFragmentDetached();
    CountDownLatch waitForActivityDestroy = waitForActivityDestroy();
    activityRule.finishActivity();
    waitForFragmentDetach.await(5, TimeUnit.SECONDS);
    waitForActivityDestroy.await(5, TimeUnit.SECONDS);

    assertLeak(LeakingFragment.class);
  }

  @Test
  public void fragmentViewShouldLeak() throws InterruptedException, ExecutionException, TimeoutException {
    startActivityAndWaitForCreate();
    Activity activity = activityRule.getActivity();

    CountDownLatch waitForFragmentViewDestroyed = activity.waitForFragmentViewDestroyed();

    ViewLeakingFragment.addToBackstack(activity);

    waitForFragmentViewDestroyed.await(5, TimeUnit.SECONDS);

    assertLeak(View.class);
  }

  private void startActivityAndWaitForCreate() throws InterruptedException {
    final CountDownLatch waitForActivityOnCreate = new CountDownLatch(1);
    Application app = ApplicationProvider.getApplicationContext();
    app.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksAdapter() {
      @Override
      public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        app.unregisterActivityLifecycleCallbacks(this);
        waitForActivityOnCreate.countDown();
      }
    });

    activityRule.launchActivity(null);

    waitForActivityOnCreate.await(5, TimeUnit.SECONDS);
  }

  private void assertLeak(Class<?> expectedLeakClass) throws InterruptedException, ExecutionException, TimeoutException {
    InstrumentationLeakDetector leakDetector = new InstrumentationLeakDetector();
    List<InstrumentationLeakResults.Result> results = leakDetector.detectLeaks();

    if (results.size() != 1) {
      throw new AssertionError(
          "Expected exactly one leak, not " + results.size() + resultsAsString(results));
    }

    InstrumentationLeakResults.Result firstResult = results.get(0);

    String leakingClassName = firstResult.analysisResult.className;

    if (!leakingClassName.equals(expectedLeakClass.getName())) {
      throw new AssertionError(
          "Expected a leak of " + expectedLeakClass.getName() + ", not " + leakingClassName + resultsAsString(results));
    }
  }

  private String resultsAsString(List<InstrumentationLeakResults.Result> results) {
    Context context = ApplicationProvider.getApplicationContext();
    StringBuilder message = new StringBuilder();
    message.append("\nLeaks found:\n##################\n");
    for (InstrumentationLeakResults.Result detectedLeak : results) {
      message.append(LeakCanary.leakInfo(context, detectedLeak.heapDump, detectedLeak.analysisResult, false));
    }
    message.append("\n##################\n");
    return message.toString();
  }

  private CountDownLatch waitForActivityDestroy() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    MessageQueue.IdleHandler countDownOnIdle = new MessageQueue.IdleHandler() {

    };
    Activity testActivity = activityRule.getActivity();
    testActivity.getApplication().registerActivityLifecycleCallbacks(
        new ActivityLifecycleCallbacksAdapter() {
          @Override
          public void onActivityDestroyed(Activity activity) {
            if (activity == testActivity) {
              testActivity.getApplication().unregisterActivityLifecycleCallbacks(this);
              Looper.myQueue().addIdleHandler(countDownOnIdle);
            }
          }
        });
    return latch;
  }

  private static final boolean TOUCH_MODE = true;
  private static final boolean LAUNCH_ACTIVITY = true;
}