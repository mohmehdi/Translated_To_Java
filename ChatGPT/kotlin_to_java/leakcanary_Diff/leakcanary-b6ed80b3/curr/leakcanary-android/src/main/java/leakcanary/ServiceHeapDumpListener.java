package leakcanary;

import android.app.Application;
import leakcanary.internal.HeapAnalyzerService;
import leakcanary.HeapDump.Listener;

public class ServiceHeapDumpListener implements Listener {

  private final Application application;
  private final Class<? extends AbstractAnalysisResultService> listenerServiceClass;

  public ServiceHeapDumpListener(Application application, Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
    this.application = application;
    this.listenerServiceClass = listenerServiceClass;
  }

  @Override
  public void analyze(HeapDump heapDump) {
    HeapAnalyzerService.runAnalysis(application, heapDump, listenerServiceClass);
  }
}