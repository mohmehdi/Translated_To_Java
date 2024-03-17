

package leakcanary;

import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.text.format.Formatter;
import com.squareup.leakcanary.R;
import leakcanary.internal.DisplayLeakActivity;
import leakcanary.internal.LeakCanaryInternals;
import leakcanary.internal.LeakCanaryInternals.LeakInfo;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DisplayLeakService extends AbstractAnalysisResultService {

  @Override
  public void onHeapAnalyzed(AnalyzedHeap analyzedHeap) {
    HeapDump heapDump = analyzedHeap.getHeapDump();
    AnalysisResult result = analyzedHeap.getResult();

    LeakInfo leakInfo = LeakCanary.leakInfo(this, heapDump, result, true);
    CanaryLog.d("%s", leakInfo);

    heapDump = renameHeapdump(heapDump);
    boolean resultSaved = saveResult(heapDump, result);

    String contentTitle;
    if (resultSaved) {
      PendingIntent pendingIntent = DisplayLeakActivity.createPendingIntent(this, result.getReferenceKey());
      if (result.getFailure() != null) {
        contentTitle = getString(R.string.leak_canary_analysis_failed);
      } else {
        String className = LeakCanaryInternals.classSimpleName(result.getClassName());
        if (result.isLeakFound()) {
          long retainedHeapSize = result.getRetainedHeapSize();
          if (retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED) {
            if (result.isExcludedLeak()) {
              contentTitle = getString(R.string.leak_canary_leak_excluded, className);
            } else {
              contentTitle = getString(R.string.leak_canary_class_has_leaked, className);
            }
          } else {
            String size = Formatter.formatShortFileSize(this, retainedHeapSize);
            if (result.isExcludedLeak()) {
              contentTitle = getString(R.string.leak_canary_leak_excluded_retaining, className, size);
            } else {
              contentTitle = getString(R.string.leak_canary_class_has_leaked_retaining, className, size);
            }
          }
        } else {
          contentTitle = getString(R.string.leak_canary_class_no_leak, className);
        }
      }
      String contentText = getString(R.string.leak_canary_notification_message);
      showNotification(pendingIntent, contentTitle, contentText);
    } else {
      onAnalysisResultFailure(getString(R.string.leak_canary_could_not_save_text));
    }

    afterDefaultHandling(heapDump, result, leakInfo);
  }

  @Override
  public void onAnalysisResultFailure(String failureMessage) {
    super.onAnalysisResultFailure(failureMessage);
    String failureTitle = getString(R.string.leak_canary_result_failure_title);
    showNotification(null, failureTitle, failureMessage);
  }

  private void showNotification(PendingIntent pendingIntent, String contentTitle, String contentText) {
    int notificationId = (int) (SystemClock.uptimeMillis() / 1000);
    LeakCanaryInternals.showNotification(this, contentTitle, contentText, pendingIntent, notificationId);
  }

  private AnalyzedHeap saveResult(HeapDump heapDump, AnalysisResult result) {
    File resultFile = AnalyzedHeap.save(heapDump, result);
    return resultFile != null ? analyzedHeap.buildUpon().heapDumpFile(resultFile).build() : null;
  }

  private HeapDump renameHeapdump(HeapDump heapDump) {
    String fileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS'.hprof'", Locale.US).format(new Date());

    File newFile = new File(heapDump.getHeapDumpFile().getParent(), fileName);
    boolean renamed = heapDump.getHeapDumpFile().renameTo(newFile);
    if (!renamed) {
      CanaryLog.d("Could not rename heap dump file %s to %s", heapDump.getHeapDumpFile().getPath(), newFile.getPath());
    }
    return heapDump.buildUpon().heapDumpFile(newFile).build();
  }

  protected void afterDefaultHandling(HeapDump heapDump, AnalysisResult result, String leakInfo) {
  }
}