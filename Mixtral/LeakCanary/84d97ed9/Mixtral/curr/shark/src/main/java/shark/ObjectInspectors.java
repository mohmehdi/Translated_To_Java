

package shark;

import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.internal.KeyedWeakReferenceMirror;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import shark.ObjectReporter;

public enum ObjectInspectors implements ObjectInspector {
  KEYED_WEAK_REFERENCE {
    @Override
    public void inspect(ObjectReporter reporter) {
      HeapObject graph = reporter.getHeapObject();
      List<KeyedWeakReferenceMirror> references =
          graph.getGraph().getContext().getOrPut(KEYED_WEAK_REFERENCE.name(), () -> {
            Class keyedWeakReferenceClass = graph.getGraph().findClassByName("leakcanary.KeyedWeakReference");

            Long heapDumpUptimeMillis = keyedWeakReferenceClass == null
                ? null
                : keyedWeakReferenceClass.getFieldValue("heapDumpUptimeMillis", Long.class);

            List<KeyedWeakReferenceMirror> addedToContext = new ArrayList<>();
            if (heapDumpUptimeMillis == null) {
              SharkLog.d(
                  "leakcanary.KeyedWeakReference.heapDumpUptimeMillis field not found, " +
                      "this must be a heap dump from an older version of LeakCanary."
              );
            }

            for (HeapInstance instance : graph.getInstances()) {
              String className = instance.getInstanceClassName();
              if (className.equals("leakcanary.KeyedWeakReference")
                  || className.equals("com.squareup.leakcanary.KeyedWeakReference")) {
                addedToContext.add(KeyedWeakReferenceMirror.fromInstance(instance, heapDumpUptimeMillis));
              }
            }

            addedToContext.removeIf(mirror -> !mirror.hasReferent());
            graph.getContext().put(KEYED_WEAK_REFERENCE.name(), addedToContext);
            return addedToContext;
          });

      Long objectId = graph.getObjectId();
      for (KeyedWeakReferenceMirror ref : references) {
        if (Objects.equals(ref.getReferent().getValue(), objectId)) {
          reporter.reportLeaking("ObjectWatcher was watching this");
          reporter.addLabel("key = " + ref.getKey());
          if (!ref.getName().isEmpty()) {
            reporter.addLabel("name = " + ref.getName());
          }
          if (ref.getWatchDurationMillis() != null) {
            reporter.addLabel("watchDurationMillis = " + ref.getWatchDurationMillis());
          }
          if (ref.getRetainedDurationMillis() != null) {
            reporter.addLabel("retainedDurationMillis = " + ref.getRetainedDurationMillis());
          }
        }
      }
    }
  },
  CLASSLOADER {
    @Override
    public void inspect(ObjectReporter reporter) {
      reporter.whenInstanceOf(ClassLoader.class, instance -> {
        reporter.reportNotLeaking("A ClassLoader is never leaking");
      });
    }
  },
  CLASS {
    @Override
    public void inspect(ObjectReporter reporter) {
      if (reporter.getHeapObject() instanceof HeapClass) {
        reporter.reportNotLeaking("a class is never leaking");
      }
    }
  },
  ANONYMOUS_CLASS {
    @Override
    public void inspect(ObjectReporter reporter) {
      HeapObject heapObject = reporter.getHeapObject();
      if (heapObject instanceof HeapInstance) {
        HeapClass instanceClass = ((HeapInstance) heapObject).getInstanceClass();
        String className = instanceClass.getName();
        Pattern pattern = Pattern.compile(ANONYMOUS_CLASS_NAME_PATTERN);
        Matcher matcher = pattern.matcher(className);
        if (matcher.matches()) {
          HeapClass parentClassRecord = instanceClass.getSuperclass();
          if (parentClassRecord.getName().equals("java.lang.Object")) {
            try {
              Class<?> actualClass = Class.forName(className);
              Class<?>[] interfaces = actualClass.getInterfaces();
              String label = interfaces.length > 0
                  ? "Anonymous class implementing " + interfaces[0].getName()
                  : "Anonymous subclass of java.lang.Object";
              reporter.addLabel(label);
            } catch (ClassNotFoundException e) {
              // Ignore
            }
          } else {
            reporter.addLabel("Anonymous subclass of " + parentClassRecord.getName());
          }
        }
      }
    }
  },
  THREAD {
    @Override
    public void inspect(ObjectReporter reporter) {
      reporter.whenInstanceOf(Thread.class, instance -> {
        String threadName = Objects.requireNonNull(instance.getFieldValue("name", String.class));
        reporter.addLabel("Thread name: '" + threadName + "'");
      });
    }
  };

  public static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";
  private static final Pattern ANONYMOUS_CLASS_NAME_PATTERN_REGEX = Pattern.compile(ANONYMOUS_CLASS_NAME_PATTERN);

  public static List<ObjectInspector> getJdkDefaults() {
    return java.util.Arrays.asList(values());
  }
}