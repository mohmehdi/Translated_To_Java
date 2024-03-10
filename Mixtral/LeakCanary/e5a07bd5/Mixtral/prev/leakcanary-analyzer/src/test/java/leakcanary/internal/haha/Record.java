package leakcanary.internal.haha;

public sealed class Record {
    public static class StringRecord extends Record {
        private final long id;
        private final String string;

        public StringRecord(long id, String string) {
            this.id = id;
            this.string = string;
        }
    }

    public static class LoadClassRecord extends Record {
        private final int classSerialNumber;
        private final long id;
        private final int stackTraceSerialNumber;
        private final long classNameStringId;

        public LoadClassRecord(int classSerialNumber, long id, int stackTraceSerialNumber, long classNameStringId) {
            this.classSerialNumber = classSerialNumber;
            this.id = id;
            this.stackTraceSerialNumber = stackTraceSerialNumber;
            this.classNameStringId = classNameStringId;
        }
    }

    public sealed interface HeapDumpRecord extends Record {
        public static class GcRootRecord extends HeapDumpRecord {
            private final GcRoot gcRoot;

            public GcRootRecord(GcRoot gcRoot) {
                this.gcRoot = gcRoot;
            }
        }

        public static class ClassDumpRecord extends HeapDumpRecord {
            private final long id;
            private final int stackTraceSerialNumber;
            private final long superClassId;
            private final long classLoaderId;
            private final long signersId;
            private final long protectionDomainId;
            private final int instanceSize;
            private final List<StaticFieldRecord> staticFields;
            private final List<FieldRecord> fields;

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

            public static class StaticFieldRecord {
                private final long nameStringId;
                private final int type;
                private final HeapValue value;

                public StaticFieldRecord(long nameStringId, int type, HeapValue value) {
                    this.nameStringId = nameStringId;
                    this.type = type;
                    this.value = value;
                }
            }

            public static class FieldRecord {
                private final long nameStringId;
                private final int type;

                public FieldRecord(long nameStringId, int type) {
                    this.nameStringId = nameStringId;
                    this.type = type;
                }
            }
        }

        public static class InstanceDumpRecord extends HeapDumpRecord {
            private final long id;
            private final int stackTraceSerialNumber;
            private final long classId;
            private final byte[] fieldValues;

            public InstanceDumpRecord(long id, int stackTraceSerialNumber, long classId, byte[] fieldValues) {
                this.id = id;
                this.stackTraceSerialNumber = stackTraceSerialNumber;
                this.classId = classId;
                this.fieldValues = fieldValues;
            }
        }

        public static class ObjectArrayDumpRecord extends HeapDumpRecord {
            private final long id;
            private final int stackTraceSerialNumber;
            private final long arrayClassId;
            private final long[] elementIds;

            public ObjectArrayDumpRecord(long id, int stackTraceSerialNumber, long arrayClassId, long[] elementIds) {
                this.id = id;
                this.stackTraceSerialNumber = stackTraceSerialNumber;
                this.arrayClassId = arrayClassId;
                this.elementIds = elementIds;
            }
        }

        public abstract static class PrimitiveArrayDumpRecord extends HeapDumpRecord {
            protected final long id;
            protected final int stackTraceSerialNumber;

            public PrimitiveArrayDumpRecord(long id, int stackTraceSerialNumber) {
                this.id = id;
                this.stackTraceSerialNumber = stackTraceSerialNumber;
            }

            public static class BooleanArrayDump extends PrimitiveArrayDumpRecord {
                private final boolean[] array;

                public BooleanArrayDump(long id, int stackTraceSerialNumber, boolean[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class CharArrayDump extends PrimitiveArrayDumpRecord {
                private final char[] array;

                public CharArrayDump(long id, int stackTraceSerialNumber, char[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class FloatArrayDump extends PrimitiveArrayDumpRecord {
                private final float[] array;

                public FloatArrayDump(long id, int stackTraceSerialNumber, float[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class DoubleArrayDump extends PrimitiveArrayDumpRecord {
                private final double[] array;

                public DoubleArrayDump(long id, int stackTraceSerialNumber, double[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class ByteArrayDump extends PrimitiveArrayDumpRecord {
                private final byte[] array;

                public ByteArrayDump(long id, int stackTraceSerialNumber, byte[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class ShortArrayDump extends PrimitiveArrayDumpRecord {
                private final short[] array;

                public ShortArrayDump(long id, int stackTraceSerialNumber, short[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class IntArrayDump extends PrimitiveArrayDumpRecord {
                private final int[] array;

                public IntArrayDump(long id, int stackTraceSerialNumber, int[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }

            public static class LongArrayDump extends PrimitiveArrayDumpRecord {
                private final long[] array;

                public LongArrayDump(long id, int stackTraceSerialNumber, long[] array) {
                    super(id, stackTraceSerialNumber);
                    this.array = array;
                }
            }
        }

        public static class HeapDumpInfoRecord extends HeapDumpRecord {
            private final int heapId;
            private final long heapNameStringId;

            public HeapDumpInfoRecord(int heapId, long heapNameStringId) {
                this.heapId = heapId;
                this.heapNameStringId = heapNameStringId;
            }
        }
    }
}