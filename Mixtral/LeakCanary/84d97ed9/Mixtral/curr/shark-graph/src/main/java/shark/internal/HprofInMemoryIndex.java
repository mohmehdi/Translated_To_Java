

package shark.internal;

import shark.GcRoot;
import shark.GcRoot.JavaFrame;
import shark.GcRoot.JniGlobal;
import shark.GcRoot.JniLocal;
import shark.GcRoot.JniMonitor;
import shark.GcRoot.MonitorUsed;
import shark.GcRoot.NativeStack;
import shark.GcRoot.StickyClass;
import shark.GcRoot.ThreadBlock;
import shark.GcRoot.ThreadObject;
import shark.HprofReader;
import shark.HprofRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.BooleanArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.DoubleArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.FloatArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.IntArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.LongArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ShortArrayDump;
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StringRecord;
import shark.OnHprofRecordListener;
import shark.PrimitiveType;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SparseArray;

public class HprofInMemoryIndex {

  private final SparseArray<String> hprofStringCache;
  private final Map<Long, Long> classNames;
  private final Map<Long, IndexedObject> objectIndex;
  private final List<GcRoot> gcRoots;
  private final Set<Long> primitiveWrapperTypes;

  public HprofInMemoryIndex(
      SparseArray<String> hprofStringCache,
      Map<Long, Long> classNames,
      Map<Long, IndexedObject> objectIndex,
      List<GcRoot> gcRoots,
      Set<Long> primitiveWrapperTypes) {
    this.hprofStringCache = hprofStringCache;
    this.classNames = classNames;
    this.objectIndex = objectIndex;
    this.gcRoots = gcRoots;
    this.primitiveWrapperTypes = primitiveWrapperTypes;
  }

  public String hprofStringById(long id) {
    String string = hprofStringCache.get(id);
    if (string == null) {
      throw new IllegalArgumentException("Hprof string " + id + " not in cache");
    }
    return string;
  }

  public String className(long classId) {
    return hprofStringById(classNames.get(classId));
  }

  public Long classId(String className) {
    Long stringId = hprofStringCache.keyAt(hprofStringCache.indexOfValue(className.replace('/', '.')));
    if (stringId == null) {
      return null;
    }
    return classNames.get(stringId);
  }

  public Iterable<Map.Entry<Long, IndexedClass>> indexedClassSequence() {
    return new Iterable<Map.Entry<Long, IndexedClass>>() {

    };
  }

  public Iterable<Map.Entry<Long, IndexedInstance>> indexedInstanceSequence() {
    return new Iterable<Map.Entry<Long, IndexedInstance>>() {

    };
  }

  public Iterable<Map.Entry<Long, IndexedObject>> indexedObjectSequence() {
    return objectIndex.entrySet();
  }

  public List<GcRoot> gcRoots() {
    return gcRoots;
  }

  public IndexedObject indexedObject(long objectId) {
    IndexedObject indexedObject = objectIndex.get(objectId);
    if (indexedObject == null) {
      throw new IllegalArgumentException("Object id " + objectId + " not found in heap dump.");
    }
    return indexedObject;
  }

  public boolean objectIdIsIndexed(long objectId) {
    return objectIndex.containsKey(objectId);
  }

  public static class Builder implements OnHprofRecordListener {

    private final Set<Class<? extends GcRoot>> indexedGcRootsTypes;
    private final SparseArray<String> hprofStringCache;
    private final Map<Long, Long> classNames;
    private final Map<Long, IndexedObject> objectIndex;
    private final Set<Long> primitiveWrapperTypes;
    private final Set<Long> primitiveWrapperClassNames;
    private final List<GcRoot> gcRoots;

    public Builder(Set<Class<? extends GcRoot>> indexedGcRootsTypes) {
      this.indexedGcRootsTypes = indexedGcRootsTypes;
      this.hprofStringCache = new SparseArray<>(60000);
      this.classNames = new HashMap<>(20000);
      this.objectIndex = new HashMap<>(250000);
      this.primitiveWrapperTypes = new HashSet<>();
      this.primitiveWrapperClassNames = new HashSet<>();
      this.gcRoots = new ArrayList<>();
    }

    @Override
    public void onHprofRecord(long position, HprofRecord record) {
      if (record instanceof StringRecord) {
        String string = ((StringRecord) record).string;
        if (PRIMITIVE_WRAPPER_TYPES.contains(string)) {
          primitiveWrapperClassNames.add(record.id);
        }
        hprofStringCache.put(record.id, string.replace('/', '.'));
      } else if (record instanceof LoadClassRecord) {
        classNames.put(record.id, ((LoadClassRecord) record).classNameStringId);
        if (primitiveWrapperClassNames.contains(record.classNameStringId)) {
          primitiveWrapperTypes.add(record.id);
        }
      } else if (record instanceof GcRootRecord) {
        GcRoot gcRoot = ((GcRootRecord) record).gcRoot;
        if (gcRoot.id != 0 && indexedGcRootsTypes.contains(gcRoot.getClass())) {
          gcRoots.add(gcRoot);
        }
      } else if (record instanceof ClassDumpRecord) {
        objectIndex.put(record.id, new IndexedClass(position, record.superclassId, record.instanceSize));
      } else if (record instanceof InstanceDumpRecord) {
        objectIndex.put(record.id, new IndexedInstance(position, record.classId));
      } else if (record instanceof ObjectArrayDumpRecord) {
        objectIndex.put(record.id, new IndexedObjectArray(position, record.arrayClassId));
      } else if (record instanceof PrimitiveArrayDumpRecord) {
        PrimitiveType primitiveType = null;
        if (record instanceof BooleanArrayDump) {
          primitiveType = PrimitiveType.BOOLEAN;
        } else if (record instanceof ByteArrayDump) {
          primitiveType = PrimitiveType.BYTE;
        } else if (record instanceof CharArrayDump) {
          primitiveType = PrimitiveType.CHAR;
        } else if (record instanceof DoubleArrayDump) {
          primitiveType = PrimitiveType.DOUBLE;
        } else if (record instanceof FloatArrayDump) {
          primitiveType = PrimitiveType.FLOAT;
        } else if (record instanceof IntArrayDump) {
          primitiveType = PrimitiveType.INT;
        } else if (record instanceof LongArrayDump) {
          primitiveType = PrimitiveType.LONG;
        } else if (record instanceof ShortArrayDump) {
          primitiveType = PrimitiveType.SHORT;
        }
        objectIndex.put(record.id, new IndexedPrimitiveArray(position, primitiveType));
      }
    }

    public HprofInMemoryIndex buildIndex() {
      return new HprofInMemoryIndex(hprofStringCache, classNames, objectIndex, gcRoots, primitiveWrapperTypes);
    }

  }

  public static HprofInMemoryIndex createReadingHprof(HprofReader reader, Set<Class<? extends GcRoot>> indexedGcRootTypes) {
    Set<Class<? extends HprofRecord>> recordTypes = new HashSet<>();
    recordTypes.add(StringRecord.class);
    recordTypes.add(LoadClassRecord.class);
    recordTypes.add(ClassDumpRecord.class);
    recordTypes.add(InstanceDumpRecord.class);
    recordTypes.add(ObjectArrayDumpRecord.class);
    recordTypes.add(PrimitiveArrayDumpRecord.class);
    recordTypes.add(GcRootRecord.class);
    Builder indexBuilderListener = new Builder(indexedGcRootTypes);
    reader.readHprofRecords(recordTypes, indexBuilderListener);
    return indexBuilderListener.buildIndex();
  }

  private static final Set<String> PRIMITIVE_WRAPPER_TYPES;

  static {
    PRIMITIVE_WRAPPER_TYPES = new HashSet<>();
    PRIMITIVE_WRAPPER_TYPES.add(Boolean.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Character.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Float.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Double.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Byte.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Short.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Integer.class.getName());
    PRIMITIVE_WRAPPER_TYPES.add(Long.class.getName());
  }
}