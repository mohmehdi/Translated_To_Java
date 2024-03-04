

package shark.internal;

import shark.PrimitiveType;

public sealed class IndexedObject abstract {
    protected long position;

  public static final class IndexedClass extends IndexedObject {
    private final long superclassId;
    private final int instanceSize;

    public IndexedClass(long position, long superclassId, int instanceSize) {
      super(position);
      this.superclassId = superclassId;
      this.instanceSize = instanceSize;
    }

    public long superclassId() {
      return superclassId;
    }

    public int instanceSize() {
      return instanceSize;
    }
  }

  public static final class IndexedInstance extends IndexedObject {
    private final long classId;

    public IndexedInstance(long position, long classId) {
      super(position);
      this.classId = classId;
    }


  }

  public static final class IndexedObjectArray extends IndexedObject {
    private final long arrayClassId;

    public IndexedObjectArray(long position, long arrayClassId) {
      super(position);
      this.arrayClassId = arrayClassId;
    }


  }

  public static final class IndexedPrimitiveArray extends IndexedObject {
    private final byte primitiveTypeOrdinal;
    private final PrimitiveType primitiveType;

    public IndexedPrimitiveArray(long position, PrimitiveType primitiveType) {
      super(position);
      this.primitiveTypeOrdinal = (byte) primitiveType.ordinal();
      this.primitiveType = primitiveType;
    }


  }
}