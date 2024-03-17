

package leakcanary;

import android.app.Instrumentation;
import android.os.Bundle;
import androidx.test.internal.runner.listener.InstrumentationResultPrinter;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class FailTestOnLeakRunListener extends RunListener {
    private Bundle bundle;
    private String skipLeakDetectionReason;

    @Override
    public void testStarted(Description description) {
        skipLeakDetectionReason = skipLeakDetectionReason(description);
        if (skipLeakDetectionReason != null) {
            return;
        }
        String testClass = description.getClassName();
        String testName = description.getMethodName();

        bundle = new Bundle();
        bundle.putString(
                Instrumentation.REPORT_KEY_IDENTIFIER, FailTestOnLeakRunListener.class.getName()
        );
        bundle.putString(InstrumentationResultPrinter.REPORT_KEY_NAME_CLASS, testClass);
        bundle.putString(InstrumentationResultPrinter.REPORT_KEY_NAME_TEST, testName);
    }

    protected String skipLeakDetectionReason(Description description) {
        return null;
    }

    @Override
    public void testFailure(Failure failure) {
        skipLeakDetectionReason = "failed";
    }

    @Override
    public void testIgnored(Description description) {
        skipLeakDetectionReason = "was ignored";
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        skipLeakDetectionReason = "had an assumption failure";
    }

    @Override
    public void testFinished(Description description) {
        detectLeaks();
        InstrumentationLeakDetector.refWatcher.clearWatchedReferences();
    }

    @Override
    public void testRunStarted(Description description) {
        InstrumentationLeakDetector.updateConfig();
    }

    @Override
    public void testRunFinished(Result result) {}

    private void detectLeaks() {
        if (skipLeakDetectionReason != null) {
            CanaryLog.d("Skipping leak detection because the test %s", skipLeakDetectionReason);
            skipLeakDetectionReason = null;
            return;
        }

        InstrumentationLeakDetector leakDetector = new InstrumentationLeakDetector();
        InstrumentationLeakResults results = leakDetector.detectLeaks();

        reportLeaks(results);
    }

    protected void reportLeaks(InstrumentationLeakResults results) {
        if (results.detectedLeaks.size() > 0) {
            String message = buildLeakDetectedMessage(results.detectedLeaks);

            bundle.putString(InstrumentationResultPrinter.REPORT_KEY_STACK, message);
            InstrumentationRegistry.getInstrumentation().sendStatus(
                    InstrumentationResultPrinter.REPORT_VALUE_RESULT_FAILURE, bundle);
        }
    }

    protected String buildLeakDetectedMessage(
            List<InstrumentationLeakResults.Result> detectedLeaks) {
        StringBuilder failureMessage = new StringBuilder();
        failureMessage.append(
                "Test failed because memory leaks were detected, see leak traces below.\n"
        );
        failureMessage.append(SEPARATOR);

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getContext();
        for (InstrumentationLeakResults.Result detectedLeak : detectedLeaks) {
            failureMessage.append(
                    LeakCanary.leakInfo(
                            context, detectedLeak.heapDump, detectedLeak.analysisResult, false
                    )
            );
            failureMessage.append(SEPARATOR);
        }

        return failureMessage.toString();
    }

    private static final String SEPARATOR = "######################################\n";
}