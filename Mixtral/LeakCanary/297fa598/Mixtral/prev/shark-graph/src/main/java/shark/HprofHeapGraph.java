package shark;

import shark.GcRoot.JavaFrame;
import shark.GcRoot.JniGlobal;
import shark.GcRoot.JniLocal;
import shark.GcRoot.JniMonitor;
import shark.GcRoot.MonitorUsed;
import shark.GcRoot.NativeStack;
import shark.GcRoot.StickyClass;
import shark.GcRoot.ThreadBlock;
import shark.GcRoot.ThreadObject;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofHeapGraph.HprofHeapGraphIndex;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.FieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.StaticFieldRecord;
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
import shark.internal.FieldValuesReader;
import shark.internal.HprofInMemoryIndex;
import shark.internal.IndexedObject;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import shark.internal.LruCache;
import java.util.function.Function;

public class HprofHeapGraph implements HeapGraph {
  private final Hprof hprof;
  private final HprofHeapGraphIndex index;

  private final LruCache < Long, ObjectRecord > objectCache = new LruCache < > (3000);
  private HeapClass javaLangObjectClass;

  public HprofHeapGraph(Hprof hprof, HprofHeapGraphIndex index) {
    this.hprof = hprof;
    this.index = index;
  }

  @Override
  public int identifierByteSize() {
      return hprof.getReader().identifierByteSize();
  }

  @Override
  public GraphContext context() {
      return new GraphContext();
  }

  @Override
  public List<GcRoot> gcRoots() {
      return index.gcRoots();
  }

  @Override
  public Sequence<HeapObject> objects() {
      return index.indexedObjectSequence().map(it -> wrapIndexedObject(it.second, it.first));
  }


  @Override
  public Sequence<HeapClass> classes() {
    return index.indexedClassSequence().stream()
        .map(it -> {
          int objectId = it[0];
          IndexedObject indexedObject = it[1];
          return new HeapClass(this, indexedObject, objectId);
        })
        .collect(Collector.toSequence());
  }

  @Override
  public Sequence<Instance> instances() {
      return index.indexedInstanceSequence().map(it -> {
          long objectId = it.first;
          IndexedInstance indexedObject = it.second;
          boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedObject.classId());
          return new HeapInstance(this, indexedObject, objectId, isPrimitiveWrapper);
      });
  }

  @Override
  public Sequence<ObjectArray> objectArrays() {
      return index.indexedObjectArraySequence().map(it -> {
          long objectId = it.first;
          IndexedInstance indexedObject = it.second;
          boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedObject.arrayClassId());
          return new HeapObjectArray(this, indexedObject, objectId, isPrimitiveWrapper);
      });
  }

  @Override
  public Sequence<PrimitiveArray> primitiveArrays() {
      return index.indexedPrimitiveArraySequence().map(it -> {
          long objectId = it.first;
          IndexedInstance indexedObject = it.second;
          return new HeapPrimitiveArray(this, indexedObject, objectId);
      });
  }

  private LruCache<Long, ObjectRecord> objectCache = new LruCache<>(3000);

  private HeapClass javaLangObjectClass = findClassByName("java.lang.Object");


  private HeapObject findObjectByIdOrNull(Long objectId) {
    if (objectId == javaLangObjectClass.getObjectId()) {
      return javaLangObjectClass;
    }

    IndexedObject indexedObject = index.getIndexedObjectOrNull(objectId);
    if (indexedObject == null) {
      return null;
    }
    return wrapIndexedObject(indexedObject, objectId);
  }

  @Override
  public HeapObject findObjectById(Long objectId) {
    HeapObject heapObject = findObjectByIdOrNull(objectId);
    if (heapObject == null) {
      throw new IllegalArgumentException("Object id " + objectId + " not found in heap dump.");
    }
    return heapObject;
  }

  @Override
  public HeapClass findClassByName(String className) {
    Long classId = index.getClassId(className);
    if (classId == null) {
      return null;
    }
    return (HeapClass) findObjectById(classId);
  }

  @Override
  public boolean objectExists(Long objectId) {
    return index.objectIdIsIndexed(objectId);
  }

  private String fieldName(Long classId, FieldRecord fieldRecord) {
    return index.fieldName(classId, fieldRecord.getNameStringId());
  }

  private String staticFieldName(Long classId, StaticFieldRecord fieldRecord) {
    return index.fieldName(classId, fieldRecord.getNameStringId());
  }

  private FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
    return new FieldValuesReader(record, getIdentifierByteSize());
  }

  private String className(Long classId) {
    return index.className(classId);
  }

  private ObjectArrayDumpRecord readObjectArrayDumpRecord(Long objectId, IndexedObjectArray indexedObject) {
    return readObjectRecord(objectId, indexedObject, new Function < HprofReader, ObjectArrayDumpRecord > () {

    });
  }

  private int readObjectArrayByteSize(Long objectId, IndexedObjectArray indexedObject) {
    ObjectRecord cachedRecord = objectCache.get(objectId);
    if (cachedRecord != null) {
      return ((ObjectArrayDumpRecord) cachedRecord).getElementIds().size() * getIdentifierByteSize();
    }
    hprof.moveReaderTo(indexedObject.getPosition());
    HprofReader.ThinObjectArrayDumpRecord thinRecord = hprof.getReader().readObjectArraySkipContentRecord();
    return thinRecord.getSize() * thinRecord.getType().getByteSize();
  }

  private PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(Long objectId, IndexedPrimitiveArray indexedObject) {
    return readObjectRecord(objectId, indexedObject, new Function < HprofReader, PrimitiveArrayDumpRecord > () {

    });
  }

  private int readPrimitiveArrayByteSize(Long objectId, IndexedPrimitiveArray indexedObject) {
    ObjectRecord cachedRecord = objectCache.get(objectId);
    if (cachedRecord != null) {
      return getPrimitiveArrayByteSize((PrimitiveArrayDumpRecord) cachedRecord);
    }
    hprof.moveReaderTo(indexedObject.getPosition());
    HprofReader.ThinPrimitiveArrayDumpRecord thinRecord = hprof.getReader().readPrimitiveArraySkipContentRecord();
    return thinRecord.getSize() * thinRecord.getType().getByteSize();
  }

  private ClassDumpRecord readClassDumpRecord(Long objectId, IndexedClass indexedObject) {
    return readObjectRecord(objectId, indexedObject, new Function < HprofReader, ClassDumpRecord > () {

    });
  }

  private InstanceDumpRecord readInstanceDumpRecord(Long objectId, IndexedInstance indexedObject) {
    return readObjectRecord(objectId, indexedObject, new Function < HprofReader, InstanceDumpRecord > () {

    });
  }

  private T readObjectRecord(Long objectId, IndexedObject indexedObject, Function < HprofReader, T > readBlock) {
    ObjectRecord objectRecordOrNull = objectCache.get(objectId);
    if (objectRecordOrNull != null) {
      return (T) objectRecordOrNull;
    }
    hprof.moveReaderTo(indexedObject.getPosition());
    return readBlock.apply(hprof.getReader());
  }


  private HeapObject wrapIndexedObject(IndexedObject indexedObject, Long objectId) {
    if (indexedObject instanceof IndexedClass) {
      return new HeapClass(this, (IndexedClass) indexedObject, objectId);
    } else if (indexedObject instanceof IndexedInstance) {
      boolean isPrimitiveWrapper = index.getPrimitiveWrapperTypes().contains(((IndexedInstance) indexedObject).getClassId());
      return new HeapInstance(this, (IndexedInstance) indexedObject, objectId, isPrimitiveWrapper);
    } else if (indexedObject instanceof IndexedObjectArray) {
      boolean isPrimitiveWrapperArray = index.getPrimitiveWrapperTypes().contains(((IndexedObjectArray) indexedObject).getArrayClassId());
      return new HeapObjectArray(this, (IndexedObjectArray) indexedObject, objectId, isPrimitiveWrapperArray);
    } else if (indexedObject instanceof IndexedPrimitiveArray) {
      return new HeapPrimitiveArray(this, (IndexedPrimitiveArray) indexedObject, objectId);
    } else {
      throw new IllegalArgumentException("Unsupported indexed object type: " + indexedObject.getClass());
    }
  }

  public static HeapGraph indexHprof(Hprof hprof, ProguardMapping proguardMapping, Set < Class > indexedGcRootTypes) {
    HprofHeapGraphIndex index = HprofInMemoryIndex.createReadingHprof(hprof, proguardMapping, indexedGcRootTypes);
    return new HprofHeapGraph(hprof, index);
  }
}