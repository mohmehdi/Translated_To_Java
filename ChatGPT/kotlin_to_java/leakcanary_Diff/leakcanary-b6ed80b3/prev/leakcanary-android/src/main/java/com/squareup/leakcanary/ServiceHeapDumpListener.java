
package com.squareup.leakcanary;

import android.app.Application;
import com.squareup.leakcanary.internal.HeapAnalyzerService;

public class ServiceHeapDumpListener implements HeapDump.Listener {

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