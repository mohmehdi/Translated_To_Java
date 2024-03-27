
package leaksentry;

import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executor;

public class RefWatcher {
  
  private Clock clock;
  private Executor checkRetainedExecutor;
  private Runnable onReferenceRetained;
  
  private Map<String, KeyedWeakReference> watchedReferences = new HashMap<>();
  private Map<String, KeyedWeakReference> retainedReferences = new HashMap<>();
  private ReferenceQueue<Object> queue = new ReferenceQueue<>();
  
  public boolean hasRetainedReferences() {
    synchronized (this) {
      removeWeaklyReachableReferences();
      return !retainedReferences.isEmpty();
    }
  }
  
  public boolean hasWatchedReferences() {
    synchronized (this) {
      removeWeaklyReachableReferences();
      return !retainedReferences.isEmpty() || !watchedReferences.isEmpty();
    }
  }
  
  public Set<String> retainedKeys() {
    synchronized (this) {
      removeWeaklyReachableReferences();
      return new HashSet<>(retainedReferences.keySet());
    }
  }
  
  public synchronized void watch(Object watchedReference) {
    watch(watchedReference, "");
  }
  
  public synchronized void watch(Object watchedReference, String referenceName) {
    removeWeaklyReachableReferences();
    String key = UUID.randomUUID().toString();
    long watchUptimeMillis = clock.uptimeMillis();
    KeyedWeakReference reference = new KeyedWeakReference(watchedReference, key, referenceName, watchUptimeMillis, queue);
    watchedReferences.put(key, reference);
    checkRetainedExecutor.execute(() -> moveToRetained(key));
  }
  
  private synchronized void moveToRetained(String key) {
    removeWeaklyReachableReferences();
    KeyedWeakReference retainedRef = watchedReferences.remove(key);
    if (retainedRef != null) {
      retainedReferences.put(key, retainedRef);
      onReferenceRetained.run();
    }
  }
  
  public synchronized void removeRetainedKeys(Set<String> keysToRemove) {
    retainedReferences.keySet().removeAll(keysToRemove);
  }
  
  public synchronized void clearWatchedReferences() {
    watchedReferences.clear();
    retainedReferences.clear();
  }
  
  private synchronized void removeWeaklyReachableReferences() {
    KeyedWeakReference ref;
    do {
      ref = (KeyedWeakReference) queue.poll();
      if (ref != null) {
        KeyedWeakReference removedRef = watchedReferences.remove(ref.getKey());
        if (removedRef == null) {
          retainedReferences.remove(ref.getKey());
        }
      }
    } while (ref != null);
  }
}