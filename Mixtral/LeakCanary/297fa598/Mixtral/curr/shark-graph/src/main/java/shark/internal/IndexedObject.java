

package shark.internal;

import shark.PrimitiveType;

public sealed class IndexedObject abstract {
    protected long position;
    protected long recordSize;

  public static class IndexedClass extends IndexedObject {
    private final long superclassId;
    private final int instanceSize;

    public IndexedClass(
      long position,
      long superclassId,
      int instanceSize,
      long recordSize
    ) {
      super(position, recordSize);
      this.superclassId = superclassId;
      this.instanceSize = instanceSize;
    }

  }

  public static class IndexedInstance extends IndexedObject {
    private final long classId;

    public IndexedInstance(
      long position,
      long classId,
      long recordSize
    ) {
      super(position, recordSize);
      this.classId = classId;
    }

  }

  public static class IndexedObjectArray extends IndexedObject {
    private final long arrayClassId;

    public IndexedObjectArray(
      long position,
      long arrayClassId,
      long recordSize
    ) {
      super(position, recordSize);
      this.arrayClassId = arrayClassId;
    }


  }

  public static class IndexedPrimitiveArray extends IndexedObject {
    private final byte primitiveTypeOrdinal;
    private final PrimitiveType primitiveType;

    public IndexedPrimitiveArray(
      long position,
      PrimitiveType primitiveType,
      long recordSize
    ) {
      super(position, recordSize);
      this.primitiveTypeOrdinal = (byte) primitiveType.ordinal();
      this.primitiveType = primitiveType;
    }

 
  }
}