package leakcanary.internal.haha;

import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord.FieldRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord.StaticFieldRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.BooleanArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.DoubleArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.FloatArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.IntArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.LongArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ShortArrayDump;
import okio.BufferedSource;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class HprofReader implements Closeable {

    protected BufferedSource source;
    protected long startPosition;
    protected int idSize;
    protected long position;

    public HprofReader(BufferedSource source, long startPosition, int idSize) {
        this.source = source;
        this.startPosition = startPosition;
        this.idSize = idSize;
        this.position = startPosition;
    }

    public boolean isOpen() {
        return source.isOpen();
    }

    private final Map<Integer, Integer> typeSizes = new HashMap<Integer, Integer>() {{
        put(OBJECT_TYPE, idSize);
        put(BOOLEAN_TYPE, BOOLEAN_SIZE);
        put(CHAR_TYPE, CHAR_SIZE);
        put(FLOAT_TYPE, FLOAT_SIZE);
        put(DOUBLE_TYPE, DOUBLE_SIZE);
        put(BYTE_TYPE, BYTE_SIZE);
        put(SHORT_TYPE, SHORT_SIZE);
        put(INT_TYPE, INT_SIZE);
        put(LONG_TYPE, LONG_SIZE);
    }};

    public HeapValue readValue(int type) {
        switch (type) {
            case OBJECT_TYPE:
                return new ObjectReference(readId());
            case BOOLEAN_TYPE:
                return new BooleanValue(readBoolean());
            case CHAR_TYPE:
                return new CharValue(readChar());
            case FLOAT_TYPE:
                return new FloatValue(readFloat());
            case DOUBLE_TYPE:
                return new DoubleValue(readDouble());
            case BYTE_TYPE:
                return new ByteValue(readByte());
            case SHORT_TYPE:
                return new ShortValue(readShort());
            case INT_TYPE:
                return new IntValue(readInt());
            case LONG_TYPE:
                return new LongValue(readLong());
            default:
                throw new IllegalStateException("Unknown type " + type);
        }
    }

    public int typeSize(int type) {
        return typeSizes.get(type);
    }

    public short readShort() {
        position += SHORT_SIZE;
        return source.readShort();
    }

    public int readInt() {
        position += INT_SIZE;
        return source.readInt();
    }

    public long[] readIdArray(int arrayLength) {
        long[] array = new long[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readId();
        }
        return array;
    }

    public boolean[] readBooleanArray(int arrayLength) {
        boolean[] array = new boolean[arrayLength];
        byte[] bytes = readByteArray(BOOLEAN_SIZE * arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            array[i] = bytes[i] != 0;
        }
        return array;
    }

    public char[] readCharArray(int arrayLength) {
        return ByteBuffer.wrap(readByteArray(CHAR_SIZE * arrayLength))
                .asCharBuffer()
                .array();
    }

    public float[] readFloatArray(int arrayLength) {
        return ByteBuffer.wrap(readByteArray(FLOAT_SIZE * arrayLength))
                .asFloatBuffer()
                .array();
    }

    public double[] readDoubleArray(int arrayLength) {
        return ByteBuffer.wrap(readByteArray(DOUBLE_SIZE * arrayLength))
                .asDoubleBuffer()
                .array();
    }

    public short[] readShortArray(int arrayLength) {
        return ByteBuffer.wrap(readByteArray(SHORT_SIZE * arrayLength))
                .asShortBuffer()
                .array();
    }

    public int[] readIntArray(int arrayLength) {
        return ByteBuffer.wrap(readByteArray(INT_SIZE * arrayLength))
                .asIntBuffer()
                .array();
    }

    public long[] readLongArray(int arrayLength) {
        return ByteBuffer.wrap(readByteArray(LONG_SIZE * arrayLength))
                .asLongBuffer()
                .array();
    }

    public long readLong() {
        position += LONG_SIZE;
        return source.readLong();
    }

    public boolean exhausted() {
        return source.exhausted();
    }

    public void skip(long byteCount) {
        position += byteCount;
        source.skip(byteCount);
    }

    public byte readByte() {
        position += BYTE_SIZE;
        return source.readByte();
    }

    public boolean readBoolean() {
        position += BOOLEAN_SIZE;
        return source.readByte() != 0;
    }

    public byte[] readByteArray(int byteCount) {
        position += byteCount;
        return source.readByteArray(byteCount);
    }

    public char readChar() {
        return ByteBuffer.wrap(readByteArray(CHAR_SIZE))
                .getChar();
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public long readId() {
        switch (idSize) {
            case 1:
                return readByte();
            case 2:
                return readShort();
            case 4:
                return readInt();
            case 8:
                return readLong();
            default:
                throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
        }
    }

    public String readUtf8(long byteCount) {
        position += byteCount;
        return source.readUtf8(byteCount);
    }

    public long readUnsignedInt() {
        return readInt() & INT_MASK;
    }

    public int readUnsignedByte() {
        return readByte() & BYTE_MASK;
    }

    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    public void skip(int byteCount) {
        position += byteCount;
        source.skip(byteCount);
    }

    @Override
    public void close() {
        source.close();
    }

    public InstanceDumpRecord readInstanceDumpRecord(long id) {
        int stackTraceSerialNumber = readInt();
        long classId = readId();
        int remainingBytesInInstance = readInt();
        byte[] fieldValues = readByteArray(remainingBytesInInstance);

        return new InstanceDumpRecord(id, stackTraceSerialNumber, classId, fieldValues);
    }

    public ClassDumpRecord readClassDumpRecord(long id) {
        int stackTraceSerialNumber = readInt();
        long superClassId = readId();
        long classLoaderId = readId();
        long signersId = readId();
        long protectionDomainId = readId();
        readId();
        readId();

        int instanceSize = readInt();

        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSize(readUnsignedByte()));
        }

        StaticFieldRecord[] staticFields = new StaticFieldRecord[readUnsignedShort()];
        for (int i = 0; i < staticFields.length; i++) {
            long nameStringId = readId();
            int type = readUnsignedByte();
            HeapValue value = readValue(type);

            staticFields[i] = new StaticFieldRecord(nameStringId, type, value);
        }

        FieldRecord[] fields = new FieldRecord[readUnsignedShort()];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new FieldRecord(readId(), readUnsignedByte());
        }

        return new ClassDumpRecord(id, stackTraceSerialNumber, superClassId, classLoaderId,
                signersId, protectionDomainId, instanceSize, staticFields, fields);
    }

    public ObjectArrayDumpRecord readObjectArrayDumpRecord(long id) {
        int stackTraceSerialNumber = readInt();
        int arrayLength = readInt();
        long arrayClassId = readId();
        long[] elementIds = readIdArray(arrayLength);
        return new ObjectArrayDumpRecord(id, stackTraceSerialNumber, arrayClassId, elementIds);
    }

    public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(long id) {
        int stackTraceSerialNumber = readInt();
        int arrayLength = readInt();
        int type = readUnsignedByte();
        switch (type) {
            case BOOLEAN_TYPE:
                return new BooleanArrayDump(id, stackTraceSerialNumber, readBooleanArray(arrayLength));
            case CHAR_TYPE:
                return new CharArrayDump(id, stackTraceSerialNumber, readCharArray(arrayLength));
            case FLOAT_TYPE:
                return new FloatArrayDump(id, stackTraceSerialNumber, readFloatArray(arrayLength));
            case DOUBLE_TYPE:
                return new DoubleArrayDump(id, stackTraceSerialNumber, readDoubleArray(arrayLength));
            case BYTE_TYPE:
                return new ByteArrayDump(id, stackTraceSerialNumber, readByteArray(arrayLength));
            case SHORT_TYPE:
                return new ShortArrayDump(id, stackTraceSerialNumber, readShortArray(arrayLength));
            case INT_TYPE:
                return new IntArrayDump(id, stackTraceSerialNumber, readIntArray(arrayLength));
            case LONG_TYPE:
                return new LongArrayDump(id, stackTraceSerialNumber, readLongArray(arrayLength));
            default:
                throw new IllegalStateException("Unexpected type " + type);
        }
    }

    public long getTagPositionAfterReadingId() {
        return position - (idSize + BYTE_SIZE);
    }

    public static final int BOOLEAN_SIZE = 1;
    public static final int CHAR_SIZE = 2;
    public static final int FLOAT_SIZE = 4;
    public static final int DOUBLE_SIZE = 8;
    public static final int BYTE_SIZE = 1;
    public static final int SHORT_SIZE = 2;
    public static final int INT_SIZE = 4;
    public static final int LONG_SIZE = 8;

    public static final int OBJECT_TYPE = 2;
    public static final int BOOLEAN_TYPE = 4;
    public static final int CHAR_TYPE = 5;
    public static final int FLOAT_TYPE = 6;
    public static final int DOUBLE_TYPE = 7;
    public static final int BYTE_TYPE = 8;
    public static final int SHORT_TYPE = 9;
    public static final int INT_TYPE = 10;
    public static final int LONG_TYPE = 11;

    public static final long INT_MASK = 0xffffffffL;
    public static final int BYTE_MASK = 0xFF;
}