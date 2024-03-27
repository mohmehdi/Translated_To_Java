package leakcanary;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ActivityTestRule;
import leakcanary.internal.ActivityLifecycleCallbacksAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;

public class FragmentLeakTest {

  @Rule
  public ActivityTestRule<TestActivity> activityRule = new ActivityTestRule<>(
      TestActivity.class, !TOUCH_MODE, !LAUNCH_ACTIVITY
  );

  @Before
  public void setUp() {
    LeakSentry.refWatcher
        .clearWatchedReferences();
  }

  @After
  public void tearDown() {
    LeakSentry.refWatcher
        .clearWatchedReferences();
  }

  @Test
  public void fragmentShouldLeak() {
    startActivityAndWaitForCreate();

    LeakingFragment.add(activityRule.getActivity());

    CountDownLatch waitForFragmentDetach = activityRule.getActivity().waitForFragmentDetached();
    CountDownLatch waitForActivityDestroy = waitForActivityDestroy();
    activityRule.finishActivity();
    try {
      waitForFragmentDetach.await();
      waitForActivityDestroy.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertLeak(LeakingFragment.class);
  }

  @Test
  public void fragmentViewShouldLeak() {
    startActivityAndWaitForCreate();
    Activity activity = activityRule.getActivity();

    CountDownLatch waitForFragmentViewDestroyed = activity.waitForFragmentViewDestroyed();

    ViewLeakingFragment.addToBackstack(activity);


    ViewLeakingFragment.addToBackstack(activity);
    try {
      waitForFragmentViewDestroyed.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assertLeak(View.class);
  }

  private void startActivityAndWaitForCreate() {
    CountDownLatch waitForActivityOnCreate = new CountDownLatch(1);
    Application app = ApplicationProvider.getApplicationContext();
    app.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksAdapter() {
      @Override
      public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        app.unregisterActivityLifecycleCallbacks(this);
        waitForActivityOnCreate.countDown();
      }
    });

    activityRule.launchActivity(null);

    try {
      waitForActivityOnCreate.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void assertLeak(Class<?> expectedLeakClass) {
    InstrumentationLeakDetector leakDetector = new InstrumentationLeakDetector();
    InstrumentationLeakResults results = leakDetector.detectLeaks();

    if (results.detectedLeaks.size() != 1) {
      throw new AssertionError(
          "Expected exactly one leak, not " + results.detectedLeaks.size() + resultsAsString(
              results.detectedLeaks
          )
      );
    }

    InstrumentationLeakResults.Result firstResult = results.detectedLeaks.get(0);

    String leakingClassName = firstResult.analysisResult.className;

    if (!leakingClassName.equals(expectedLeakClass.getName())) {
      throw new AssertionError(
          "Expected a leak of " + expectedLeakClass + ", not " + leakingClassName + resultsAsString(
              results.detectedLeaks
          )
      );
    }
  }

  private String resultsAsString(List<InstrumentationLeakResults.Result> results) {
    Context context = ApplicationProvider.getApplicationContext();
    StringBuilder message = new StringBuilder();
    message.append("\nLeaks found:\n##################\n");
    for (InstrumentationLeakResults.Result detectedLeak : results) {
      message.append(
          LeakCanary.leakInfo(context, detectedLeak.heapDump, detectedLeak.analysisResult, false)
      );
    }
    message.append("\n##################\n");
    return message.toString();
  }

  private CountDownLatch waitForActivityDestroy() {
    CountDownLatch latch = new CountDownLatch(1);
    MessageQueue.IdleHandler countDownOnIdle = new MessageQueue.IdleHandler() {

    };
    Activity testActivity = activityRule.getActivity();
    testActivity.getApplication().registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksAdapter() {
      @Override
      public void onActivityDestroyed(Activity activity) {
        if (activity == testActivity) {
          activity.getApplication().unregisterActivityLifecycleCallbacks(this);
          Looper.myQueue()
              .addIdleHandler(countDownOnIdle);
        }
      }
    });
    return latch;
  }

  private static final boolean TOUCH_MODE = true;
  private static final boolean LAUNCH_ACTIVITY = true;
}