package leakcanary.internal.haha;

sealed class Record {
  public static class StringRecord extends Record {
    public final long id;
    public final String string;

    public StringRecord(long id, String string) {
      this.id = id;
      this.string = string;
    }
  }

  public static class LoadClassRecord extends Record {
    public final int classSerialNumber;
    public final long id;
    public final int stackTraceSerialNumber;
    public final long classNameStringId;

    public LoadClassRecord(int classSerialNumber, long id, int stackTraceSerialNumber, long classNameStringId) {
      this.classSerialNumber = classSerialNumber;
      this.id = id;
      this.stackTraceSerialNumber = stackTraceSerialNumber;
      this.classNameStringId = classNameStringId;
    }
  }

  public sealed class HeapDumpRecord extends Record {
    public static class GcRootRecord extends HeapDumpRecord {
      public final GcRoot gcRoot;

      public GcRootRecord(GcRoot gcRoot) {
        this.gcRoot = gcRoot;
      }
    }

    public sealed class ObjectRecord extends HeapDumpRecord {
      public static class ClassDumpRecord extends ObjectRecord {
        public final long id;
        public final int stackTraceSerialNumber;
        public final long superClassId;
        public final long classLoaderId;
        public final long signersId;
        public final long protectionDomainId;
        public final int instanceSize;
        public final List < StaticFieldRecord > staticFields;
        public final List < FieldRecord > fields;

        public static class StaticFieldRecord {
          public final long nameStringId;
          public final int type;
          public final HeapValue value;

          public StaticFieldRecord(long nameStringId, int type, HeapValue value) {
            this.nameStringId = nameStringId;
            this.type = type;
            this.value = value;
          }
        }

        public static class FieldRecord {
          public final long nameStringId;
          public final int type;

          public FieldRecord(long nameStringId, int type) {
            this.nameStringId = nameStringId;
            this.type = type;
          }
        }

        public ClassDumpRecord(long id, int stackTraceSerialNumber, long superClassId, long classLoaderId, long signersId, long protectionDomainId, int instanceSize, List < StaticFieldRecord > staticFields, List < FieldRecord > fields) {
          this.id = id;
          this.stackTraceSerialNumber = stackTraceSerialNumber;
          this.superClassId = superClassId;
          this.classLoaderId = classLoaderId;
          this.signersId = signersId;
          this.protectionDomainId = protectionDomainId;
          this.instanceSize = instanceSize;
          this.staticFields = staticFields;
          this.fields = fields;
        }
      }

      public static class InstanceDumpRecord extends ObjectRecord {
        public final long id;
        public final int stackTraceSerialNumber;
        public final long classId;
        public final byte[] fieldValues;

        public InstanceDumpRecord(long id, int stackTraceSerialNumber, long classId, byte[] fieldValues) {
          this.id = id;
          this.stackTraceSerialNumber = stackTraceSerialNumber;
          this.classId = classId;
          this.fieldValues = fieldValues;
        }
      }

      public static class ObjectArrayDumpRecord extends ObjectRecord {
        public final long id;
        public final int stackTraceSerialNumber;
        public final long arrayClassId;
        public final long[] elementIds;

        public ObjectArrayDumpRecord(long id, int stackTraceSerialNumber, long arrayClassId, long[] elementIds) {
          this.id = id;
          this.stackTraceSerialNumber = stackTraceSerialNumber;
          this.arrayClassId = arrayClassId;
          this.elementIds = elementIds;
        }
      }

      public sealed class PrimitiveArrayDumpRecord extends ObjectRecord {
        public final long id;
        public final int stackTraceSerialNumber;

        public PrimitiveArrayDumpRecord(long id, int stackTraceSerialNumber) {
          this.id = id;
          this.stackTraceSerialNumber = stackTraceSerialNumber;
        }

        public static class BooleanArrayDump extends PrimitiveArrayDumpRecord {
          public final boolean[] array;

          public BooleanArrayDump(long id, int stackTraceSerialNumber, boolean[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class CharArrayDump extends PrimitiveArrayDumpRecord {
          public final char[] array;

          public CharArrayDump(long id, int stackTraceSerialNumber, char[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class FloatArrayDump extends PrimitiveArrayDumpRecord {
          public final float[] array;

          public FloatArrayDump(long id, int stackTraceSerialNumber, float[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class DoubleArrayDump extends PrimitiveArrayDumpRecord {
          public final double[] array;

          public DoubleArrayDump(long id, int stackTraceSerialNumber, double[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class ByteArrayDump extends PrimitiveArrayDumpRecord {
          public final byte[] array;

          public ByteArrayDump(long id, int stackTraceSerialNumber, byte[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class ShortArrayDump extends PrimitiveArrayDumpRecord {
          public final short[] array;

          public ShortArrayDump(long id, int stackTraceSerialNumber, short[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class IntArrayDump extends PrimitiveArrayDumpRecord {
          public final int[] array;

          public IntArrayDump(long id, int stackTraceSerialNumber, int[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }

        public static class LongArrayDump extends PrimitiveArrayDumpRecord {
          public final long[] array;

          public LongArrayDump(long id, int stackTraceSerialNumber, long[] array) {
            super(id, stackTraceSerialNumber);
            this.array = array;
          }
        }
      }
    }

    public static class HeapDumpInfoRecord extends HeapDumpRecord {
      public final int heapId;
      public final long heapNameStringId;

      public HeapDumpInfoRecord(int heapId, long heapNameStringId) {
        this.heapId = heapId;
        this.heapNameStringId = heapNameStringId;
      }
    }
  }
}