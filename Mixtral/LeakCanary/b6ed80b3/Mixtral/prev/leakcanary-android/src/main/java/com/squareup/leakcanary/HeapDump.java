

package com.squareup.leakcanary;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HeapDump implements Serializable {

  private final File heapDumpFile;
  private final ExcludedRefs excludedRefs;
  private final long gcDurationMs;
  private final long heapDumpDurationMs;
  private final boolean computeRetainedHeapSize;
  private final List<Class<out Reachability.Inspector>> reachabilityInspectorClasses;

  public static class Listener {

    public void analyze(HeapDump heapDump) {}

    public static final Listener NONE = new Listener() {
      @Override
      public void analyze(HeapDump heapDump) {
        // No-op
      }
    };
  }

  public HeapDump(Builder builder) {
    this.heapDumpFile = builder.heapDumpFile;
    this.excludedRefs = builder.excludedRefs;
    this.computeRetainedHeapSize = builder.computeRetainedHeapSize;
    this.gcDurationMs = builder.gcDurationMs;
    this.heapDumpDurationMs = builder.heapDumpDurationMs;
    this.reachabilityInspectorClasses = builder.reachabilityInspectorClasses;
  }

  public Builder buildUpon() {
    return new Builder(this);
  }

  public static class Builder {
    private File heapDumpFile;
    private ExcludedRefs excludedRefs;
    private long gcDurationMs;
    private long heapDumpDurationMs;
    private boolean computeRetainedHeapSize;
    private List<Class<out Reachability.Inspector>> reachabilityInspectorClasses;

    public Builder() {
      this.heapDumpFile = null;
      this.excludedRefs = null;
      this.gcDurationMs = 0;
      this.heapDumpDurationMs = 0;
      this.computeRetainedHeapSize = false;
      this.reachabilityInspectorClasses = null;
    }

    public Builder(HeapDump heapDump) {
      this.heapDumpFile = heapDump.heapDumpFile;
      this.excludedRefs = heapDump.excludedRefs;
      this.computeRetainedHeapSize = heapDump.computeRetainedHeapSize;
      this.gcDurationMs = heapDump.gcDurationMs;
      this.heapDumpDurationMs = heapDump.heapDumpDurationMs;
      this.reachabilityInspectorClasses = heapDump.reachabilityInspectorClasses;
    }

    public Builder heapDumpFile(File heapDumpFile) {
      this.heapDumpFile = heapDumpFile;
      return this;
    }

    public Builder excludedRefs(ExcludedRefs excludedRefs) {
      this.excludedRefs = excludedRefs;
      return this;
    }

    public Builder gcDurationMs(long gcDurationMs) {
      this.gcDurationMs = gcDurationMs;
      return this;
    }

    public Builder heapDumpDurationMs(long heapDumpDurationMs) {
      this.heapDumpDurationMs = heapDumpDurationMs;
      return this;
    }

    public Builder computeRetainedHeapSize(boolean computeRetainedHeapSize) {
      this.computeRetainedHeapSize = computeRetainedHeapSize;
      return this;
    }

    public Builder reachabilityInspectorClasses(
        List<Class<out Reachability.Inspector>> reachabilityInspectorClasses) {
      this.reachabilityInspectorClasses = Collections.unmodifiableList(
          new ArrayList<Class<out Reachability.Inspector>>(reachabilityInspectorClasses));
      return this;
    }

    public HeapDump build() {
      return new HeapDump(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}