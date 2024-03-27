

package com.squareup.leakcanary;

import leaksentry.LeakSentry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.Date;

public class InstrumentationLeakDetectorTest {

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
  public void detectsLeak() {
    leaking = new Date();
    Object refWatcher = LeakSentry.refWatcher;
    ((LeakSentry.RefWatcher) refWatcher).watch(leaking);

    InstrumentationLeakDetector leakDetector = new InstrumentationLeakDetector();
    LeakDetector.Results results = leakDetector.detectLeaks();

    if (results.detectedLeaks.size() != 1) {
      throw new AssertionError("Expected exactly one leak, not " + results.detectedLeaks.size());
    }

    LeakDetector.Result firstResult = results.detectedLeaks.get(0);

    String leakingClassName = firstResult.analysisResult.className;

    if (!leakingClassName.equals(Date.class.getName())) {
      throw new AssertionError("Expected a leak of Date, not " + leakingClassName);
    }
  }

  private static Object leaking;
}