

package com.squareup.leakcanary;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.widget.Toast;
import com.squareup.leakcanary.internal.DisplayLeakActivity;
import com.squareup.leakcanary.internal.LeakCanaryInternals;
import com.squareup.leakcanary.internal.LeakCanaryInternals.Companion;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DisplayLeakService extends AbstractAnalysisResultService {

  @Override
  public void onHeapAnalyzed(AnalyzedHeap analyzedHeap) {
    HeapDump heapDump = analyzedHeap.heapDump;
    AnalysisResult result = analyzedHeap.result;

    String leakInfo = LeakCanary.leakInfo(this, heapDump, result, true);
    CanaryLog.d("%s", leakInfo);

    heapDump = renameHeapdump(heapDump);
    boolean resultSaved = saveResult(heapDump, result);

    String contentTitle;
    if (resultSaved) {
      PendingIntent pendingIntent = DisplayLeakActivity.createPendingIntent(this, result.referenceKey);
      if (result.failure != null) {
        contentTitle = getResources().getString(R.string.leak_canary_analysis_failed);
      } else {
        String className = Companion.classSimpleName(result.className);
        if (result.leakFound) {
          if (result.retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED) {
            if (result.excludedLeak) {
              contentTitle = getResources().getString(R.string.leak_canary_leak_excluded, className);
            } else {
              contentTitle = getResources().getString(R.string.leak_canary_class_has_leaked, className);
            }
          } else {
            String size = Formatter.formatShortFileSize(this, result.retainedHeapSize);
            if (result.excludedLeak) {
              contentTitle = getResources().getString(R.string.leak_canary_leak_excluded_retaining, className, size);
            } else {
              contentTitle = getResources().getString(R.string.leak_canary_class_has_leaked_retaining, className, size);
            }
          }
        } else {
          contentTitle = getResources().getString(R.string.leak_canary_class_no_leak, className);
        }
      }
      String contentText = getResources().getString(R.string.leak_canary_notification_message);
      showNotification(pendingIntent, contentTitle, contentText);
    } else {
      onAnalysisResultFailure(getResources().getString(R.string.leak_canary_could_not_save_text));
    }

    afterDefaultHandling(heapDump, result, leakInfo);
  }

  @Override
  public void onAnalysisResultFailure(String failureMessage) {
    super.onAnalysisResultFailure(failureMessage);
    String failureTitle = getResources().getString(R.string.leak_canary_result_failure_title);
    showNotification(null, failureTitle, failureMessage);
  }

  private void showNotification(PendingIntent pendingIntent, String contentTitle, String contentText) {
    int notificationId = (int) (SystemClock.uptimeMillis() / 1000);
    LeakCanaryInternals.showNotification(
        this, contentTitle, contentText, pendingIntent, notificationId);
  }

  private AnalysisResult saveResult(HeapDump heapDump, AnalysisResult result) {
    File resultFile = AnalyzedHeap.save(heapDump, result);
    return resultFile != null;
  }

  private HeapDump renameHeapdump(HeapDump heapDump) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS'.hprof'", Locale.US);
    String fileName = dateFormat.format(new Date());

    File newFile = new File(heapDump.heapDumpFile.getParent(), fileName);
    boolean renamed = heapDump.heapDumpFile.renameTo(newFile);
    if (!renamed) {
      CanaryLog.d("Could not rename heap dump file %s to %s", heapDump.heapDumpFile.getPath(),
          newFile.getPath());
    }
    return heapDump.toBuilder()
        .heapDumpFile(newFile)
        .build();
  }

  protected void afterDefaultHandling(HeapDump heapDump, AnalysisResult result, String leakInfo) {
  }
}