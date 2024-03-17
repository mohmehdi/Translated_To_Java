

package leaksentry;

import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

public class RefWatcher {

  private final Clock clock;
  private final Executor checkRetainedExecutor;
  private final Runnable onReferenceRetained;

  private final Map<String, KeyedWeakReference> watchedReferences = new HashMap<>();
  private final Map<String, KeyedWeakReference> retainedReferences = new HashMap<>();
  private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

  public RefWatcher(Clock clock, Executor checkRetainedExecutor, Runnable onReferenceRetained) {
    this.clock = clock;
    this.checkRetainedExecutor = checkRetainedExecutor;
    this.onReferenceRetained = onReferenceRetained;
  }

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

  public void watch(Object watchedReference) {
    watch(watchedReference, "");
  }

  public void watch(Object watchedReference, String referenceName) {
    synchronized (this) {
      removeWeaklyReachableReferences();
      String key = UUID.randomUUID().toString();
      long watchUptimeMillis = clock.uptimeMillis();
      KeyedWeakReference<Object> reference = new KeyedWeakReference<>(watchedReference, key, referenceName, watchUptimeMillis, queue);
      watchedReferences.put(key, reference);
      checkRetainedExecutor.execute(() -> moveToRetained(key));
    }
  }

  private void moveToRetained(String key) {
    synchronized (this) {
      removeWeaklyReachableReferences();
      KeyedWeakReference<Object> retainedRef = watchedReferences.remove(key);
      if (retainedRef != null) {
        retainedReferences.put(key, retainedRef);
        onReferenceRetained.run();
      }
    }
  }

  public void removeRetainedKeys(Set<String> keysToRemove) {
    synchronized (this) {
      retainedReferences.keySet().removeAll(keysToRemove);
    }
  }

  public void clearWatchedReferences() {
    synchronized (this) {
      watchedReferences.clear();
      retainedReferences.clear();
    }
  }

  private void removeWeaklyReachableReferences() {
    KeyedWeakReference<Object> ref;
    do {
      ref = (KeyedWeakReference<Object>) queue.poll();
      if (ref != null) {
        KeyedWeakReference<Object> removedRef = watchedReferences.remove(ref.key);
        if (removedRef == null) {
          retainedReferences.remove(ref.key);
        }
      }
    } while (ref != null);
  }
}