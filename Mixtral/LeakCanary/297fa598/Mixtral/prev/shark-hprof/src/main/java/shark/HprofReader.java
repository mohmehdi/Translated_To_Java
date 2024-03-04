package shark;

sealed class HprofRecord {
    static class StringRecord extends HprofRecord {
        final long id;
        final String string;

        StringRecord(long id, String string) {
            this.id = id;
            this.string = string;
        }
    }

    static class LoadClassRecord extends HprofRecord {
        final int classSerialNumber;
        final long id;
        final int stackTraceSerialNumber;
        final long classNameStringId;

        LoadClassRecord(int classSerialNumber, long id, int stackTraceSerialNumber, long classNameStringId) {
            this.classSerialNumber = classSerialNumber;
            this.id = id;
            this.stackTraceSerialNumber = stackTraceSerialNumber;
            this.classNameStringId = classNameStringId;
        }
    }

    static class HeapDumpEndRecord extends HprofRecord {
    }

    static class StackFrameRecord extends HprofRecord {
        final long id;
        final long methodNameStringId;
        final long methodSignatureStringId;
        final long sourceFileNameStringId;
        final int classSerialNumber;
        final int lineNumber;

        StackFrameRecord(long id, long methodNameStringId, long methodSignatureStringId, long sourceFileNameStringId, int classSerialNumber, int lineNumber) {
            this.id = id;
            this.methodNameStringId = methodNameStringId;
            this.methodSignatureStringId = methodSignatureStringId;
            this.sourceFileNameStringId = sourceFileNameStringId;
            this.classSerialNumber = classSerialNumber;
            this.lineNumber = lineNumber;
        }
    }

    static class StackTraceRecord extends HprofRecord {
        final int stackTraceSerialNumber;
        final int threadSerialNumber;
        final long[] stackFrameIds;

        StackTraceRecord(int stackTraceSerialNumber, int threadSerialNumber, long[] stackFrameIds) {
            this.stackTraceSerialNumber = stackTraceSerialNumber;
            this.threadSerialNumber = threadSerialNumber;
            this.stackFrameIds = stackFrameIds;
        }
    }

    static class HeapDumpRecord extends HprofRecord {
        static class GcRootRecord extends HeapDumpRecord {
            final GcRoot gcRoot;

            GcRootRecord(GcRoot gcRoot) {
                this.gcRoot = gcRoot;
            }
        }

        static abstract class ObjectRecord extends HeapDumpRecord {
            static class ClassDumpRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final long superclassId;
                final long classLoaderId;
                final long signersId;
                final long protectionDomainId;
                final int instanceSize;
                final List<StaticFieldRecord> staticFields;
                final List<FieldRecord> fields;

                static class StaticFieldRecord {
                    final long nameStringId;
                    final int type;
                    final ValueHolder value;

                    StaticFieldRecord(long nameStringId, int type, ValueHolder value) {
                        this.nameStringId = nameStringId;
                        this.type = type;
                        this.value = value;
                    }
                }

                static class FieldRecord {
                    final long nameStringId;
                    final int type;

                    FieldRecord(long nameStringId, int type) {
                        this.nameStringId = nameStringId;
                        this.type = type;
                    }
                }

                ClassDumpRecord(long id, int stackTraceSerialNumber, long superclassId, long classLoaderId, long signersId, long protectionDomainId, int instanceSize, List<StaticFieldRecord> staticFields, List<FieldRecord> fields) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.superclassId = superclassId;
                    this.classLoaderId = classLoaderId;
                    this.signersId = signersId;
                    this.protectionDomainId = protectionDomainId;
                    this.instanceSize = instanceSize;
                    this.staticFields = staticFields;
                    this.fields = fields;
                }
            }

            static class ClassSkipContentRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final long superclassId;
                final long classLoaderId;
                final long signersId;
                final long protectionDomainId;
                final int instanceSize;
                final int staticFieldCount;
                final int fieldCount;

                ClassSkipContentRecord(long id, int stackTraceSerialNumber, long superclassId, long classLoaderId, long signersId, long protectionDomainId, int instanceSize, int staticFieldCount, int fieldCount) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.superclassId = superclassId;
                    this.classLoaderId = classLoaderId;
                    this.signersId = signersId;
                    this.protectionDomainId = protectionDomainId;
                    this.instanceSize = instanceSize;
                    this.staticFieldCount = staticFieldCount;
                    this.fieldCount = fieldCount;
                }
            }

            static class InstanceDumpRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final long classId;
                final byte[] fieldValues;

                InstanceDumpRecord(long id, int stackTraceSerialNumber, long classId, byte[] fieldValues) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.classId = classId;
                    this.fieldValues = fieldValues;
                }
            }

            static class InstanceSkipContentRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final long classId;

                InstanceSkipContentRecord(long id, int stackTraceSerialNumber, long classId) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.classId = classId;
                }
            }

            static class ObjectArrayDumpRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final long arrayClassId;
                final long[] elementIds;

                ObjectArrayDumpRecord(long id, int stackTraceSerialNumber, long arrayClassId, long[] elementIds) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.arrayClassId = arrayClassId;
                    this.elementIds = elementIds;
                }
            }

            static class ObjectArraySkipContentRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final long arrayClassId;
                final int size;

                ObjectArraySkipContentRecord(long id, int stackTraceSerialNumber, long arrayClassId, int size) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.arrayClassId = arrayClassId;
                    this.size = size;
                }
            }

            static class PrimitiveArrayDumpRecord extends ObjectRecord {
                abstract long id();

                abstract int stackTraceSerialNumber();

                abstract int size();

                static class BooleanArrayDump extends PrimitiveArrayDumpRecord {
                    final boolean[] array;

                    BooleanArrayDump(long id, int stackTraceSerialNumber, boolean[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }

                static class CharArrayDump extends PrimitiveArrayDumpRecord {
                    final char[] array;

                    CharArrayDump(long id, int stackTraceSerialNumber, char[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }

                static class FloatArrayDump extends PrimitiveArrayDumpRecord {
                    final float[] array;

                    FloatArrayDump(long id, int stackTraceSerialNumber, float[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }

                static class DoubleArrayDump extends PrimitiveArrayDumpRecord {
                    final double[] array;

                    DoubleArrayDump(long id, int stackTraceSerialNumber, double[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
               
                }

                static class ByteArrayDump extends PrimitiveArrayDumpRecord {
                    final byte[] array;

                    ByteArrayDump(long id, int stackTraceSerialNumber, byte[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }

                static class ShortArrayDump extends PrimitiveArrayDumpRecord {
                    final short[] array;

                    ShortArrayDump(long id, int stackTraceSerialNumber, short[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }

                static class IntArrayDump extends PrimitiveArrayDumpRecord {
                    final int[] array;

                    IntArrayDump(long id, int stackTraceSerialNumber, int[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }

                static class LongArrayDump extends PrimitiveArrayDumpRecord {
                    final long[] array;

                    LongArrayDump(long id, int stackTraceSerialNumber, long[] array) {
                        super(id, stackTraceSerialNumber, array.length);
                        this.array = array;
                    }

                    @Override
                    long id() {
                        return super.id();
                    }

                    @Override
                    int stackTraceSerialNumber() {
                        return super.stackTraceSerialNumber();
                    }

                    @Override
                    int size() {
                        return super.size();
                    }
                }
            }

            static class PrimitiveArraySkipContentRecord extends ObjectRecord {
                final long id;
                final int stackTraceSerialNumber;
                final int size;
                final PrimitiveType type;

                PrimitiveArraySkipContentRecord(long id, int stackTraceSerialNumber, int size, PrimitiveType type) {
                    this.id = id;
                    this.stackTraceSerialNumber = stackTraceSerialNumber;
                    this.size = size;
                    this.type = type;
                }
            }
        }

        static class HeapDumpInfoRecord extends HeapDumpRecord {
            final int heapId;
            final long heapNameStringId;

            HeapDumpInfoRecord(int heapId, long heapNameStringId) {
                this.heapId = heapId;
                this.heapNameStringId = heapNameStringId;
            }
        }
    }
}