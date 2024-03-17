

package leakcanary;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.internal.ForegroundService;
import java.io.File;

public abstract class AbstractAnalysisResultService extends ForegroundService {

  public AbstractAnalysisResultService() {
    super(AbstractAnalysisResultService.class.getName(), R.string.leak_canary_notification_reporting);
  }

  @Override
  protected void onHandleIntentInForeground(Intent intent) {
    if (intent == null) {
      CanaryLog.d("AbstractAnalysisResultService received a null intent, ignoring.");
      return;
    }
    if (!intent.hasExtra(ANALYZED_HEAP_PATH_EXTRA)) {
      onAnalysisResultFailure(getString(R.string.leak_canary_result_failure_no_disk_space));
      return;
    }
    File analyzedHeapFile = new File(intent.getStringExtra(ANALYZED_HEAP_PATH_EXTRA));
    AnalyzedHeap analyzedHeap = AnalyzedHeap.load(analyzedHeapFile);
    if (analyzedHeap == null) {
      onAnalysisResultFailure(getString(R.string.leak_canary_result_failure_no_file));
      return;
    }
    try {
      onHeapAnalyzed(analyzedHeap);
    } finally {
      analyzedHeap.heapDump.heapDumpFile.delete();
      analyzedHeap.selfFile.delete();
    }
  }

  protected abstract void onHeapAnalyzed(AnalyzedHeap analyzedHeap);

  protected void onAnalysisResultFailure(String failureMessage) {
    CanaryLog.d(failureMessage);
  }

  public static void sendResultToListener(
      Context context,
      String listenerServiceClassName,
      HeapDump heapDump,
      AnalysisResult result) {
    try {
      Class listenerServiceClass = Class.forName(listenerServiceClassName);
      Intent intent = new Intent(context, listenerServiceClass);
      File analyzedHeapFile = AnalyzedHeap.save(heapDump, result);
      if (analyzedHeapFile != null) {
        intent.putExtra(ANALYZED_HEAP_PATH_EXTRA, analyzedHeapFile.getAbsolutePath());
      }
      context.startForegroundService(intent);
    } catch (ClassNotFoundException e) {
      CanaryLog.e(e);
    }
  }

  private static final String ANALYZED_HEAP_PATH_EXTRA = "analyzed_heap_path_extra";
}