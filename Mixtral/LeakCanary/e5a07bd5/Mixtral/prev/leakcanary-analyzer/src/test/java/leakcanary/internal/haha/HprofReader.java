

package leakcanary.internal.haha;

import leakcanary.internal.haha.HeapValue.BooleanValue;
import leakcanary.internal.haha.HeapValue.ByteValue;
import leakcanary.internal.haha.HeapValue.CharValue;
import leakcanary.internal.haha.HeapValue.DoubleValue;
import leakcanary.internal.haha.HeapValue.FloatValue;
import leakcanary.internal.haha.HeapValue.IntValue;
import leakcanary.internal.haha.HeapValue.LongValue;
import leakcanary.internal.haha.HeapValue.ObjectReference;
import leakcanary.internal.haha.HeapValue.ShortValue;
import leakcanary.internal.haha.Record.HeapDumpRecord.ClassDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ClassDumpRecord.FieldRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ClassDumpRecord.StaticFieldRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.InstanceDumpRecord;
import okio.BufferedSource;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class HprofReader implements Closeable {

    protected BufferedSource source;
    protected long position;
    protected final int idSize;
    private final Map<Integer, Integer> typeSizes;

    public HprofReader(BufferedSource source, long startPosition, int idSize) {
        this.source = source;
        this.position = startPosition;
        this.idSize = idSize;
        this.typeSizes = new HashMap<>();
        typeSizes.put(OBJECT_TYPE, idSize);
        typeSizes.put(BOOLEAN_TYPE, BOOLEAN_SIZE);
        typeSizes.put(CHAR_TYPE, CHAR_SIZE);
        typeSizes.put(FLOAT_TYPE, FLOAT_SIZE);
        typeSizes.put(DOUBLE_TYPE, DOUBLE_SIZE);
        typeSizes.put(BYTE_TYPE, BYTE_SIZE);
        typeSizes.put(SHORT_TYPE, SHORT_SIZE);
        typeSizes.put(INT_TYPE, INT_SIZE);
        typeSizes.put(LONG_TYPE, LONG_SIZE);
    }

    public boolean isOpen() {
        return source.isOpen();
    }

    public HeapValue readValue(int type) {
        return switch (type) {
            case OBJECT_TYPE -> new ObjectReference(readId());
            case BOOLEAN_TYPE -> new BooleanValue(readBoolean());
            case CHAR_TYPE -> new CharValue(readChar());
            case FLOAT_TYPE -> new FloatValue(readFloat());
            case DOUBLE_TYPE -> new DoubleValue(readDouble());
            case BYTE_TYPE -> new ByteValue(readByte());
            case SHORT_TYPE -> new ShortValue(readShort());
            case INT_TYPE -> new IntValue(readInt());
            case LONG_TYPE -> new LongValue(readLong());
            default -> throw new IllegalStateException("Unknown type " + type);
        };
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

    public LongArray readIdArray(int arrayLength) {
        LongArray array = new LongArray(arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readId();
        }
        return array;
    }

    public BooleanArray readBooleanArray(int arrayLength) {
        BooleanArray array = new BooleanArray(arrayLength);
        byte[] byteArray = readByteArray(BOOLEAN_SIZE * arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            array[i] = byteArray[i] != 0;
        }
        return array;
    }

    public char[] readCharArray(int arrayLength) {
        byte[] byteArray = readByteArray(CHAR_SIZE * arrayLength);
        return ByteBuffer.wrap(byteArray).asCharBuffer().array();
    }

    public float[] readFloatArray(int arrayLength) {
        byte[] byteArray = readByteArray(FLOAT_SIZE * arrayLength);
        return ByteBuffer.wrap(byteArray).asFloatBuffer().array();
    }

    public double[] readDoubleArray(int arrayLength) {
        byte[] byteArray = readByteArray(DOUBLE_SIZE * arrayLength);
        return ByteBuffer.wrap(byteArray).asDoubleBuffer().array();
    }

    public short[] readShortArray(int arrayLength) {
        byte[] byteArray = readByteArray(SHORT_SIZE * arrayLength);
        return ByteBuffer.wrap(byteArray).asShortBuffer().array();
    }

    public int[] readIntArray(int arrayLength) {
        byte[] byteArray = readByteArray(INT_SIZE * arrayLength);
        return ByteBuffer.wrap(byteArray).asIntBuffer().array();
    }

    public LongArray readLongArray(int arrayLength) {
        byte[] byteArray = readByteArray(LONG_SIZE * arrayLength);
        return ByteBuffer.wrap(byteArray).asLongBuffer().array();
    }

    public void skipShort() {
        skip(SHORT_SIZE);
    }

    public void skipInt() {
        skip(INT_SIZE);
    }

    public void skipLong() {
        skip(LONG_SIZE);
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
        byte[] byteArray = readByteArray(CHAR_SIZE);
        return ByteBuffer.wrap(byteArray).getChar();
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
        return (long) readInt() & INT_MASK;
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

        return new InstanceDumpRecord(
                id,
                stackTraceSerialNumber,
                classId,
                fieldValues
        );
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

        short constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skipShort();
            skip(typeSize(readUnsignedByte()));
        }

        List<StaticFieldRecord> staticFields = new ArrayList<>();
        short staticFieldCount = readUnsignedShort();
        for (int i = 0; i < staticFieldCount; i++) {
            long nameStringId = readId();
            int type = readUnsignedByte();
            HeapValue value = readValue(type);

            staticFields.add(
                    new StaticFieldRecord(
                            nameStringId,
                            type,
                            value
                    )
            );
        }

        List<FieldRecord> fields = new ArrayList<>();
        short fieldCount = readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new FieldRecord(readId(), readUnsignedByte()));
        }

        return new ClassDumpRecord(
                id,
                stackTraceSerialNumber,
                superClassId,
                classLoaderId,
                signersId,
                protectionDomainId,
                instanceSize,
                staticFields,
                fields
        );
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