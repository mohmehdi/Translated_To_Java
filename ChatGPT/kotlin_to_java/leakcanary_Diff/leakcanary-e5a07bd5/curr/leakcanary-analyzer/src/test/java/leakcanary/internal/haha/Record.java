package leakcanary.internal.haha;

public abstract class Record {

    public static final class StringRecord extends Record {
        public final long id;
        public final String string;

        public StringRecord(long id, String string) {
            this.id = id;
            this.string = string;
        }
    }

    public static final class LoadClassRecord extends Record {
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

    public static abstract class HeapDumpRecord extends Record {

        public static final class GcRootRecord extends HeapDumpRecord {
            public final GcRoot gcRoot;

            public GcRootRecord(GcRoot gcRoot) {
                this.gcRoot = gcRoot;
            }
        }

        public static abstract class ObjectRecord extends HeapDumpRecord {

            public static final class ClassDumpRecord extends ObjectRecord {
                public final long id;
                public final int stackTraceSerialNumber;
                public final long superClassId;
                public final long classLoaderId;
                public final long signersId;
                public final long protectionDomainId;
                public final int instanceSize;
                public final List<StaticFieldRecord> staticFields;
                public final List<FieldRecord> fields;

                public ClassDumpRecord(long id, int stackTraceSerialNumber, long superClassId, long classLoaderId, long signersId, long protectionDomainId, int instanceSize, List<StaticFieldRecord> staticFields, List<FieldRecord> fields) {
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

                public static final class StaticFieldRecord {
                    public final long nameStringId;
                    public final int type;
                    public final HeapValue value;

                    public StaticFieldRecord(long nameStringId, int type, HeapValue value) {
                        this.nameStringId = nameStringId;
                        this.type = type;
                        this.value = value;
                    }
                }

                public static final class FieldRecord {
                    public final long nameStringId;
                    public final int type;

                    public FieldRecord(long nameStringId, int type) {
                        this.nameStringId = nameStringId;
                        this.type = type;
                    }
                }
            }

            public static final class InstanceDumpRecord extends ObjectRecord {
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

            public static final class ObjectArrayDumpRecord extends ObjectRecord {
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

            public static abstract class PrimitiveArrayDumpRecord extends ObjectRecord {
                public abstract long id;
                public abstract int stackTraceSerialNumber;

                public static final class BooleanArrayDump extends PrimitiveArrayDumpRecord {
                    public final boolean[] array;

                    public BooleanArrayDump(long id, int stackTraceSerialNumber, boolean[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class CharArrayDump extends PrimitiveArrayDumpRecord {
                    public final char[] array;

                    public CharArrayDump(long id, int stackTraceSerialNumber, char[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class FloatArrayDump extends PrimitiveArrayDumpRecord {
                    public final float[] array;

                    public FloatArrayDump(long id, int stackTraceSerialNumber, float[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class DoubleArrayDump extends PrimitiveArrayDumpRecord {
                    public final double[] array;

                    public DoubleArrayDump(long id, int stackTraceSerialNumber, double[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class ByteArrayDump extends PrimitiveArrayDumpRecord {
                    public final byte[] array;

                    public ByteArrayDump(long id, int stackTraceSerialNumber, byte[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class ShortArrayDump extends PrimitiveArrayDumpRecord {
                    public final short[] array;

                    public ShortArrayDump(long id, int stackTraceSerialNumber, short[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class IntArrayDump extends PrimitiveArrayDumpRecord {
                    public final int[] array;

                    public IntArrayDump(long id, int stackTraceSerialNumber, int[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }

                public static final class LongArrayDump extends PrimitiveArrayDumpRecord {
                    public final long[] array;

                    public LongArrayDump(long id, int stackTraceSerialNumber, long[] array) {
                        this.id = id;
                        this.stackTraceSerialNumber = stackTraceSerialNumber;
                        this.array = array;
                    }
                }
            }
        }

        public static final class HeapDumpInfoRecord extends HeapDumpRecord {
            public final int heapId;
            public final long heapNameStringId;

            public HeapDumpInfoRecord(int heapId, long heapNameStringId) {
                this.heapId = heapId;
                this.heapNameStringId = heapNameStringId;
            }
        }
    }
}