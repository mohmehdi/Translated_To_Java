

package shark;

import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
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
import java.io.File;
import java.lang.reflect.KClass;
import java.util.List;

public class HprofHeapGraph implements CloseableHeapGraph {

  private final HprofHeader header;
  private final RandomAccessHprofReader reader;
  private final HprofInMemoryIndex index;

  private final int identifierByteSize;
  private final GraphContext context;
  private final List<GcRoot> gcRoots;
  private final LruCache<Long, ObjectRecord> objectCache;
  private final HeapClass javaLangObjectClass;

  public HprofHeapGraph(HprofHeader header, RandomAccessHprofReader reader, HprofInMemoryIndex index) {
    this.header = header;
    this.reader = reader;
    this.index = index;
    this.identifierByteSize = header.getIdentifierByteSize();
    this.context = new GraphContext();
    this.gcRoots = index.gcRoots();
    this.objectCache = new LruCache<Long, ObjectRecord>(INTERNAL_LRU_CACHE_SIZE);
    this.javaLangObjectClass = findClassByName("java.lang.Object");
  }

public static HeapGraph indexHprof(Hprof hprof, Mapping proguardMapping, Set<Class<GcRoot>> indexedGcRootTypes) {
    FileSourceProvider fileSourceProvider = new FileSourceProvider(new File(hprof.getFile()));
    HprofIndex index =
        HprofIndex.indexRecordsOf(
            fileSourceProvider, hprof.getHeader(), proguardMapping, indexedGcRootTypes);
    HeapGraph graph = index.openHeapGraph();
    hprof.attachClosable(graph);
    return graph;
  }
    public static CloseableHeapGraph openHeapGraph(Path file, ProguardMapping proguardMapping, Set<Class<? extends GcRoot>> indexedGcRootTypes) throws IOException {
        return new FileSourceProvider(file).openHeapGraph(proguardMapping, indexedGcRootTypes);
    }

    public static CloseableHeapGraph openHeapGraph(DualSourceProvider provider, ProguardMapping proguardMapping, Set<Class<? extends GcRoot>> indexedGcRootTypes) throws IOException {
        HprofHeader header = openStreamingSource(provider).use(source -> HprofHeader.parseHeaderOf(source));
        HprofIndex index = HprofIndex.indexRecordsOf(provider, header, proguardMapping, indexedGcRootTypes);
        return index.openHeapGraph();
    }

  @Override
  public Sequence<HeapObject> objects() {
    return index.indexedObjectSequence()
        .map(indexedObject -> wrapIndexedObject(indexedObject.second, indexedObject.first));
  }

  @Override
  public Sequence<HeapClass> classes() {
    return index.indexedClassSequence()
        .map(indexedObject -> new HeapClass(this, indexedObject.second, indexedObject.first));
  }

  @Override
  public Sequence<HeapInstance> instances() {
    return index.indexedInstanceSequence()
        .map(indexedObject -> new HeapInstance(this, indexedObject.second, indexedObject.first,
            index.primitiveWrapperTypes.contains(indexedObject.second.classId)));
  }

  @Override
  public Sequence<HeapObjectArray> objectArrays() {
    return index.indexedObjectArraySequence()
        .map(indexedObject -> new HeapObjectArray(this, indexedObject.second, indexedObject.first,
            index.primitiveWrapperTypes.contains(indexedObject.second.arrayClassId)));
  }

  @Override
  public Sequence<HeapPrimitiveArray> primitiveArrays() {
    return index.indexedPrimitiveArraySequence()
        .map(indexedObject -> new HeapPrimitiveArray(this, indexedObject.second, indexedObject.first));
  }

  private HeapObject findObjectByIdOrNull(long objectId) {
    if (objectId == javaLangObjectClass.getObjectId()) {
      return javaLangObjectClass;
    }

    IndexedObject indexedObject = index.indexedObjectOrNull(objectId);
    if (indexedObject == null) {
      return null;
    }
    return wrapIndexedObject(indexedObject, objectId);
  }

  @Override
  public HeapObject findObjectById(long objectId) {
    HeapObject heapObject = findObjectByIdOrNull(objectId);
    if (heapObject == null) {
      throw new IllegalArgumentException("Object id " + objectId + " not found in heap dump.");
    }
    return heapObject;
  }

  @Override
  public HeapClass findClassByName(String className) {
    Long classId = index.classId(className);
    if (classId == null) {
      return null;
    }
    return (HeapClass) findObjectById(classId);
  }

  @Override
  public boolean objectExists(long objectId) {
    return index.objectIdIsIndexed(objectId);
  }

  @Override
  public void close() {
    try {
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String fieldName(long classId, FieldRecord fieldRecord) {
    return index.fieldName(classId, fieldRecord.nameStringId);
  }

  private String staticFieldName(long classId, StaticFieldRecord fieldRecord) {
    return index.fieldName(classId, fieldRecord.nameStringId);
  }

  private FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
    return new FieldValuesReader(record, identifierByteSize);
  }

  private String className(long classId) {
    return index.className(classId);
  }

  private ObjectArrayDumpRecord readObjectArrayDumpRecord(long objectId,
      IndexedObjectArray indexedObject) {
    return readObjectRecord(objectId, indexedObject, this::readObjectArrayDumpRecord);
  }

  private int readObjectArrayByteSize(long objectId, IndexedObjectArray indexedObject) {
    ObjectRecord cachedRecord = objectCache.get(objectId);
    if (cachedRecord != null) {
      return cachedRecord.getElementIds().size * identifierByteSize;
    }
    int position = (int) (indexedObject.getPosition() + identifierByteSize + PrimitiveType.INT.byteSize);
    long size = PrimitiveType.INT.byteSize;
    int thinRecordSize = reader.readRecord(position, size, this::readInt) * identifierByteSize;
    return thinRecordSize;
  }

  private PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(long objectId,
      IndexedPrimitiveArray indexedObject) {
    return readObjectRecord(objectId, indexedObject, this::readPrimitiveArrayDumpRecord);
  }

public int readPrimitiveArrayByteSize(long objectId, IndexedPrimitiveArray indexedObject) throws IOException {
    PrimitiveArrayDumpRecord cachedRecord = (PrimitiveArrayDumpRecord) objectCache.get(objectId);
    if (cachedRecord != null) {
        switch (cachedRecord.getClass().getSimpleName()) {
            case "BooleanArrayDump":
                return ((BooleanArrayDump) cachedRecord).array.length * PrimitiveType.BOOLEAN.byteSize;
            case "CharArrayDump":
                return ((CharArrayDump) cachedRecord).array.length * PrimitiveType.CHAR.byteSize;
            case "FloatArrayDump":
                return ((FloatArrayDump) cachedRecord).array.length * PrimitiveType.FLOAT.byteSize;
            case "DoubleArrayDump":
                return ((DoubleArrayDump) cachedRecord).array.length * PrimitiveType.DOUBLE.byteSize;
            case "ByteArrayDump":
                return ((ByteArrayDump) cachedRecord).array.length * PrimitiveType.BYTE.byteSize;
            case "ShortArrayDump":
                return ((ShortArrayDump) cachedRecord).array.length * PrimitiveType.SHORT.byteSize;
            case "IntArrayDump":
                return ((IntArrayDump) cachedRecord).array.length * PrimitiveType.INT.byteSize;
            case "LongArrayDump":
                return ((LongArrayDump) cachedRecord).array.length * PrimitiveType.LONG.byteSize;
            default:
                throw new IOException("Unsupported primitive array type");
        }
    }
    long position = indexedObject.position + identifierByteSize + PrimitiveType.INT.byteSize;
    int size = reader.readRecord(position, PrimitiveType.INT.byteSize);
    return size * indexedObject.primitiveType.byteSize;
}

  private ClassDumpRecord readClassDumpRecord(long objectId, IndexedClass indexedObject) {
    return readObjectRecord(objectId, indexedObject, this::readClassDumpRecord);
  }

  private InstanceDumpRecord readInstanceDumpRecord(long objectId, IndexedInstance indexedObject) {
    return readObjectRecord(objectId, indexedObject, this::readInstanceDumpRecord);
  }

  private <T extends ObjectRecord> T readObjectRecord(long objectId, IndexedObject indexedObject,
      Function<HprofRecordReader, T> readBlock) {
    ObjectRecord objectRecordOrNull = objectCache.get(objectId);
    if (objectRecordOrNull != null) {
      return objectRecordOrNull;
    }
    return reader.readRecord(indexedObject.getPosition(), indexedObject.getRecordSize(), readBlock);
  }

    public HeapObject wrapIndexedObject(IndexedObject indexedObject, long objectId) {
        if (indexedObject instanceof IndexedClass) {
            return new HeapClass(this, (IndexedClass) indexedObject, objectId);
        } else if (indexedObject instanceof IndexedInstance) {
            boolean isPrimitiveWrapper = isPrimitiveWrapperType(((IndexedInstance) indexedObject).getClassId());
            return new HeapInstance(this, (IndexedInstance) indexedObject, objectId, isPrimitiveWrapper);
        } else if (indexedObject instanceof IndexedObjectArray) {
            boolean isPrimitiveWrapperArray = isPrimitiveWrapperType(((IndexedObjectArray) indexedObject).getArrayClassId());
            return new HeapObjectArray(this, (IndexedObjectArray) indexedObject, objectId, isPrimitiveWrapperArray);
        } else if (indexedObject instanceof IndexedPrimitiveArray) {
            return new HeapPrimitiveArray(this, (IndexedPrimitiveArray) indexedObject, objectId);
        }
        return null;
    }

}