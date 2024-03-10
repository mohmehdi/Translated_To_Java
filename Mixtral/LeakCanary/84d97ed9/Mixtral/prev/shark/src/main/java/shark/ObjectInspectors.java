

package shark;

import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.internal.KeyedWeakReferenceMirror;
import shark.ObjectReporter;
import shark.HeapGraph;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum ObjectInspectors implements ObjectInspector {
  KEYED_WEAK_REFERENCE {
    @Override
    public void inspect(HeapGraph graph, ObjectReporter reporter) {
      List<KeyedWeakReferenceMirror> references =
        graph.getContext().getOrPut(KEYED_WEAK_REFERENCE.name, () -> {
          Class keyedWeakReferenceClass = graph.findClassByName("leakcanary.KeyedWeakReference");

          Long heapDumpUptimeMillis = keyedWeakReferenceClass == null
            ? null
            : keyedWeakReferenceClass.getField("heapDumpUptimeMillis").getLong(null);

          if (heapDumpUptimeMillis == null) {
            SharkLog.d(
              "leakcanary.KeyedWeakReference.heapDumpUptimeMillis field not found, " +
                "this must be a heap dump from an older version of LeakCanary."
            );
          }

          List<KeyedWeakReferenceMirror> addedToContext = new ArrayList<>();
          for (HeapInstance instance : graph.getInstances()) {
            String className = instance.getInstanceClassName();
            if (className.equals("leakcanary.KeyedWeakReference") || className.equals("com.squareup.leakcanary.KeyedWeakReference")) {
              addedToContext.add(KeyedWeakReferenceMirror.fromInstance(instance, heapDumpUptimeMillis));
            }
          }
          addedToContext.removeIf(mirror -> !mirror.hasReferent());
          graph.getContext().put(KEYED_WEAK_REFERENCE.name, addedToContext);
          return addedToContext;
        });

      long objectId = reporter.getHeapObject().getObjectId();
      for (KeyedWeakReferenceMirror ref : references) {
        if (ref.getReferent().getValue() == objectId) {
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
    public void inspect(HeapGraph graph, ObjectReporter reporter) {
      reporter.whenInstanceOf(ClassLoader.class, instance -> {
        reporter.reportNotLeaking("A ClassLoader is never leaking");
      });
    }
  },

  CLASS {
    @Override
    public void inspect(HeapGraph graph, ObjectReporter reporter) {
      if (reporter.getHeapObject() instanceof HeapClass) {
        reporter.reportNotLeaking("a class is never leaking");
      }
    }
  },

  ANONYMOUS_CLASS {
    @Override
    public void inspect(HeapGraph graph, ObjectReporter reporter) {
      Object heapObject = reporter.getHeapObject();
      if (heapObject instanceof HeapInstance) {
        String instanceClassName = ((HeapInstance) heapObject).getInstanceClass().getName();
        Pattern pattern = Pattern.compile(ANONYMOUS_CLASS_NAME_PATTERN);
        Matcher matcher = pattern.matcher(instanceClassName);
        if (matcher.matches()) {
          Class parentClassRecord = ((HeapInstance) heapObject).getInstanceClass().getSuperclass();
          if (parentClassRecord.getName().equals("java.lang.Object")) {
            try {
              Class actualClass = Class.forName(instanceClassName);
              Class[] interfaces = actualClass.getInterfaces();
              reporter.addLabel(
                interfaces.length > 0
                  ? "Anonymous class implementing " + interfaces[0].getName()
                  : "Anonymous subclass of java.lang.Object"
              );
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
    public void inspect(HeapGraph graph, ObjectReporter reporter) {
      reporter.whenInstanceOf(Thread.class, instance -> {
        String threadName = (String) instance.getFieldValue("name");
        reporter.addLabel("Thread name: '" + threadName + "'");
      });
    }
  };

  public static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";
  private static final Pattern ANONYMOUS_CLASS_NAME_PATTERN_REGEX = Pattern.compile(ANONYMOUS_CLASS_NAME_PATTERN);

}