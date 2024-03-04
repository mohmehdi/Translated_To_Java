package shark.internal;

import shark.GcRoot;
import shark.Hprof;
import shark.HprofRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArraySkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArraySkipContentRecord;
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StringRecord;
import shark.OnHprofRecordListener;
import shark.PrimitiveType;
import shark.ProguardMapping;
import shark.ValueHolder;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import shark.internal.hppc.LongLongScatterMap;
import shark.internal.hppc.LongObjectScatterMap;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class HprofInMemoryIndex {

  private final int positionSize;
  private final LongObjectScatterMap hprofStringCache;
  private final LongLongScatterMap classNames;
  private final SortedMap < Long, byte[] > classIndex;
  private final SortedMap < Long, byte[] > instanceIndex;
  private final SortedMap < Long, byte[] > objectArrayIndex;
  private final SortedMap < Long, byte[] > primitiveArrayIndex;
  private final List < GcRoot > gcRoots;
  private final ProguardMapping proguardMapping;
  private final Set < PrimitiveType > primitiveWrapperTypes;

  private HprofInMemoryIndex(int positionSize, LongObjectScatterMap hprofStringCache, LongLongScatterMap classNames,
    SortedMap < Long, byte[] > classIndex, SortedMap < Long, byte[] > instanceIndex,
    SortedMap < Long, byte[] > objectArrayIndex, SortedMap < Long, byte[] > primitiveArrayIndex,
    List < GcRoot > gcRoots, ProguardMapping proguardMapping, Set < PrimitiveType > primitiveWrapperTypes) {
    this.positionSize = positionSize;
    this.hprofStringCache = hprofStringCache;
    this.classNames = classNames;
    this.classIndex = classIndex;
    this.instanceIndex = instanceIndex;
    this.objectArrayIndex = objectArrayIndex;
    this.primitiveArrayIndex = primitiveArrayIndex;
    this.gcRoots = gcRoots;
    this.proguardMapping = proguardMapping;
    this.primitiveWrapperTypes = primitiveWrapperTypes;
  }

  public String fieldName(long classId, long id) {
    String fieldNameString = getHprofStringById(id);
    if (proguardMapping != null) {
      long classNameStringId = classNames.get(classId);
      String classNameString = getHprofStringById(classNameStringId);
      fieldNameString = proguardMapping.deobfuscateFieldName(classNameString, fieldNameString);
    }
    return fieldNameString;
  }

  public String className(long classId) {
    long classNameStringId = classNames.get(classId);
    String classNameString = getHprofStringById(classNameStringId);
    return proguardMapping != null ? proguardMapping.deobfuscateClassName(classNameString) : classNameString;
  }

  public Long classId(String className) {
    long hprofStringId = hprofStringCache.entrySet().stream()
      .filter(entry -> entry.getValue().equals(className))
      .findFirst()
      .map(Map.Entry::getKey)
      .orElse(null);
    if (hprofStringId != null) {
      Long classNameStringId = classNames.entrySet().stream()
        .filter(entry -> entry.getValue() == hprofStringId)
        .findFirst()
        .map(Map.Entry::getKey)
        .orElse(null);
      return classNameStringId;
    }
    return null;
  }

  public Sequence < Pair < Long, IndexedClass >> indexedClassSequence() {
    return classIndex.entrySet().stream()
      .map(entry -> Pair.create(entry.getKey(),
        new IndexedClass(
          entry.getValue()[0],
          entry.getValue()[entry.getValue().length - 8],
          ByteBuffer.wrap(entry.getValue()).getInt()
        )
      ));
  }

  public static Stream < Pair < Long, IndexedInstance >> indexedInstanceSequence() {
    return StreamSupport.stream(instanceIndex.spliterator(), false)
      .map(entry -> {
        long id = entry.getKey();
        byte[] array = entry.getValue();
        IndexedInstance instance = new IndexedInstance(
          getTruncatedLong(array, positionSize),
          getId(array)
        );
        return Pair.create(id, instance);
      });
  }

  static Stream < Entry < Long, IndexedObjectArray >> indexedObjectArraySequence() {
    return objectArrayIndex.entrySet().stream()
      .map(entry -> {
        long id = entry.getKey();
        byte[] array = entry.getValue();
        IndexedObjectArray objectArray = new IndexedObjectArray(
          readTruncatedLong(array, positionSize),
          readId(array)
        );
        return Entry.of(id, objectArray);
      });
  }
  public static Stream < Map.Entry < Long, IndexedPrimitiveArray >> indexedPrimitiveArraySequence() {
    return primitiveArrayIndex.entrySet().stream()
      .map(entry -> {
        long id = entry.getKey();
        byte[] array = entry.getValue();

        IndexedPrimitiveArray indexedPrimitiveArray = new IndexedPrimitiveArray(
          position: array[0],
          primitiveType: PrimitiveType.values()[array[1]]
        );

        return Map.entry(id, indexedPrimitiveArray);
      });
  }

  public static Stream < Pair < Long, IndexedObject >> indexedObjectSequence() {
    return Stream.concat(indexedClassSequence(),
      Stream.concat(indexedInstanceSequence(),
        Stream.concat(indexedObjectArraySequence(), indexedPrimitiveArraySequence())));
  }

  public static List < GcRoot > gcRoots() {
    return gcRoots;
  }

  @SuppressWarnings("ConstantConditions")
  public IndexedObject indexedObjectOrNull(long objectId) {
    ByteSubArray array = classIndex.get(objectId);
    if (array != null) {
      return new IndexedClass(
        array.readTruncatedLong(positionSize),
        array.readId(),
        array.readInt()
      );
    }
    array = instanceIndex.get(objectId);
    if (array != null) {
      return new IndexedInstance(
        array.readTruncatedLong(positionSize),
        array.readId()
      );
    }
    array = objectArrayIndex.get(objectId);
    if (array != null) {
      return new IndexedObjectArray(
        array.readTruncatedLong(positionSize),
        array.readId()
      );
    }
    array = primitiveArrayIndex.get(objectId);
    if (array != null) {
      return new IndexedPrimitiveArray(
        array.readTruncatedLong(positionSize),
        PrimitiveType.values()[array.readByte()]
      );
    }
    return null;
  }

  @SuppressWarnings("MethodReturnCount")
  public boolean objectIdIsIndexed(long objectId) {
    return Optional.ofNullable(classIndex.get(objectId)).isPresent() ||
      Optional.ofNullable(instanceIndex.get(objectId)).isPresent() ||
      Optional.ofNullable(objectArrayIndex.get(objectId)).isPresent() ||
      Optional.ofNullable(primitiveArrayIndex.get(objectId)).isPresent();
  }

  private String hprofStringById(long id) {
    String hprofString = hprofStringCache.get(id);
    if (hprofString == null) {
      throw new IllegalArgumentException("Hprof string " + id + " not in cache");
    }
    return hprofString;
  }

  public static class Builder implements OnHprofRecordListener {

    private final int identifierSize;
    private final int positionSize;

    private final LongObjectScatterMap hprofStringCache;
    private final LongLongScatterMap classNames;
    private final SortedMap < Long, byte[] > classIndex;
    private final SortedMap < Long, byte[] > instanceIndex;
    private final SortedMap < Long, byte[] > objectArrayIndex;
    private final SortedMap < Long, byte[] > primitiveArrayIndex;

    private final Set < PrimitiveType > primitiveWrapperTypes;
    private final Set < Long > primitiveWrapperClassNames;
    private final List < GcRoot > gcRoots;

    public Builder(boolean longIdentifiers, long fileLength, int classCount, int instanceCount,
      int objectArrayCount, int primitiveArrayCount, Set < Class < ? >> indexedGcRootsTypes) {
      this.identifierSize = longIdentifiers ? 8 : 4;
      this.positionSize = byteSizeForUnsigned(fileLength);

      this.hprofStringCache = new LongObjectScatterMap();
      this.classNames = new LongLongScatterMap(expectedElements = classCount);
      this.classIndex = new TreeMap < > ();
      this.instanceIndex = new TreeMap < > ();
      this.objectArrayIndex = new TreeMap < > ();
      this.primitiveArrayIndex = new TreeMap < > ();

      this.primitiveWrapperTypes = new HashSet < > ();
      this.primitiveWrapperClassNames = new HashSet < > ();
      this.gcRoots = new ArrayList < > ();
    }

    @Override
    public void onHprofRecord(long position, HprofRecord record) {
      if (record instanceof StringRecord) {
        StringRecord stringRecord = (StringRecord) record;
        if (PRIMITIVE_WRAPPER_TYPES.contains(stringRecord.string)) {
          primitiveWrapperClassNames.add(stringRecord.id);
        }

        hprofStringCache.put(stringRecord.id, stringRecord.string.replace('/', '.'));
      } else if (record instanceof LoadClassRecord) {
        LoadClassRecord loadClassRecord = (LoadClassRecord) record;
        classNames.put(loadClassRecord.id, loadClassRecord.classNameStringId);
        if (primitiveWrapperClassNames.contains(loadClassRecord.classNameStringId)) {
          primitiveWrapperTypes.add(PrimitiveType.getById(loadClassRecord.id));
        }
      } else if (record instanceof GcRootRecord) {
        GcRootRecord gcRootRecord = (GcRootRecord) record;
        GcRoot gcRoot = gcRootRecord.gcRoot;
        if (gcRoot.id != ValueHolder.NULL_REFERENCE && indexedGcRootsTypes.contains(gcRoot.getClass())) {
          gcRoots.add(gcRoot);
        }
      } else if (record instanceof ClassSkipContentRecord) {
        ClassSkipContentRecord classSkipContentRecord = (ClassSkipContentRecord) record;
        byte[] entry = new byte[positionSize + identifierSize + 4];
        writeTruncatedLong(entry, 0, position, positionSize);
        writeId(entry, positionSize, classSkipContentRecord.superclassId);
        writeInt(entry, positionSize + identifierSize, classSkipContentRecord.instanceSize);
        classIndex.put(classSkipContentRecord.id, entry);
      }
      // Implement other cases similarly
    }

    public HprofInMemoryIndex buildIndex(ProguardMapping proguardMapping) {
      SortedMap < Long, byte[] > sortedInstanceIndex = moveToSortedMap(instanceIndex,
        (id, value) -> value);
      SortedMap < Long, byte[] > sortedObjectArrayIndex = moveToSortedMap(objectArrayIndex,
        (id, value) -> value);
      SortedMap < Long, byte[] > sortedPrimitiveArrayIndex = moveToSortedMap(primitiveArrayIndex,
        (id, value) -> value);
      SortedMap < Long, byte[] > sortedClassIndex = moveToSortedMap(classIndex,
        (id, value) -> value);

      return new HprofInMemoryIndex(positionSize, hprofStringCache, classNames, sortedClassIndex,
        sortedInstanceIndex, sortedObjectArrayIndex, sortedPrimitiveArrayIndex, gcRoots,
        proguardMapping, primitiveWrapperTypes);
    }

    // Implement helper methods like moveToSortedMap, writeTruncatedLong, writeId, and writeInt

  }

  private static int byteSizeForUnsigned(long maxValue) {
    int byteCount = 0;
    long value = maxValue;
    while (value != 0) {
      value = value >> 8;
      byteCount++;
    }
    return byteCount;
  }

  public static HprofInMemoryIndex createReadingHprof(Hprof hprof, ProguardMapping proguardMapping,
    Set < Class < ? >> indexedGcRootTypes) {
    Set < Class < ? >> recordTypes = new HashSet < > ();
    recordTypes.add(StringRecord.class);
    recordTypes.add(LoadClassRecord.class);
    recordTypes.add(ClassSkipContentRecord.class);
    recordTypes.add(InstanceSkipContentRecord.class);
    recordTypes.add(ObjectArraySkipContentRecord.class);
    recordTypes.add(PrimitiveArraySkipContentRecord.class);
    recordTypes.add(GcRootRecord.class);

    Hprof.Reader reader = hprof.getReader();

    int classCount = 0;
    int instanceCount = 0;
    int objectArrayCount = 0;
    int primitiveArrayCount = 0;
    reader.readHprofRecords(
      EnumSet.of(LoadClassRecord.class, InstanceSkipContentRecord.class,
        ObjectArraySkipContentRecord.class, PrimitiveArraySkipContentRecord.class),
      new OnHprofRecordListener() {
        @Override
        public void onHprofRecord(long position, HprofRecord record) {
          if (record instanceof LoadClassRecord) {
            classCount++;
          } else if (record instanceof InstanceSkipContentRecord) {
            instanceCount++;
          } else if (record instanceof ObjectArraySkipContentRecord) {
            objectArrayCount++;
          } else if (record instanceof PrimitiveArraySkipContentRecord) {
            primitiveArrayCount++;
          }
        }
      }
    );

    hprof.getReader().moveReaderTo(reader.getStartPosition());

    Builder indexBuilderListener = new Builder(
      reader.getIdentifierByteSize() == 8, hprof.getFileLength(), classCount, instanceCount,
      objectArrayCount, primitiveArrayCount, indexedGcRootTypes);

    reader.readHprofRecords(recordTypes, indexBuilderListener);

    return indexBuilderListener.buildIndex(proguardMapping);
  }
}