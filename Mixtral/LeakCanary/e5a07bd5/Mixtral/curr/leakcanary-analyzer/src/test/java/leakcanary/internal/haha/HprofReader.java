

package leakcanary.internal.haha;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import okio.BufferedSource;

public class HprofReader implements Closeable {
    protected BufferedSource source;
    protected long position;
    protected final int startPosition;
    protected final int idSize;
    private final Map<Integer, Integer> typeSizes;

    public HprofReader(BufferedSource source, long startPosition, int idSize) {
        this.source = source;
        this.startPosition = startPosition;
        this.idSize = idSize;
        this.typeSizes = new HashMap<>();
        this.typeSizes.put(OBJECT_TYPE, idSize);
        this.typeSizes.put(BOOLEAN_TYPE, BOOLEAN_SIZE);
        this.typeSizes.put(CHAR_TYPE, CHAR_SIZE);
        this.typeSizes.put(FLOAT_TYPE, FLOAT_SIZE);
        this.typeSizes.put(DOUBLE_TYPE, DOUBLE_SIZE);
        this.typeSizes.put(BYTE_TYPE, BYTE_SIZE);
        this.typeSizes.put(SHORT_TYPE, SHORT_SIZE);
        this.typeSizes.put(INT_TYPE, INT_SIZE);
        this.typeSizes.put(LONG_TYPE, LONG_SIZE);
        this.position = startPosition;
    }

    public int typeSize(int type) {
        return typeSizes.get(type);
    }

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

    public short readShort() {
        position += SHORT_SIZE;
        try {
            return source.readShort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int readInt() {
        position += INT_SIZE;
        try {
            return source.readInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public LongArray readIdArray(int arrayLength) {
        LongArray array = new LongArray(arrayLength);
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
        byte[] bytes = readByteArray(CHAR_SIZE * arrayLength);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().array();
    }

    public float[] readFloatArray(int arrayLength) {
        byte[] bytes = readByteArray(FLOAT_SIZE * arrayLength);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().array();
    }

    public double[] readDoubleArray(int arrayLength) {
        byte[] bytes = readByteArray(DOUBLE_SIZE * arrayLength);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().array();
    }

    public short[] readShortArray(int arrayLength) {
        byte[] bytes = readByteArray(SHORT_SIZE * arrayLength);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().array();
    }

    public int[] readIntArray(int arrayLength) {
        byte[] bytes = readByteArray(INT_SIZE * arrayLength);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().array();
    }

    public long[] readLongArray(int arrayLength) {
        byte[] bytes = readByteArray(LONG_SIZE * arrayLength);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().array();
    }

    public long readLong() {
        position += LONG_SIZE;
        try {
            return source.readLong();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean exhausted() {
        return source.exhausted();
    }

    public void skip(long byteCount) {
        position += byteCount;
        try {
            source.skip(byteCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte readByte() {
        position += BYTE_SIZE;
        try {
            return source.readByte();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean readBoolean() {
        position += BOOLEAN_SIZE;
        try {
            return source.readByte() != 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] readByteArray(int byteCount) {
        position += byteCount;
        try {
            return source.readByteArray(byteCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public char readChar() {
        byte[] bytes = readByteArray(CHAR_SIZE);
        return (char) ByteBuffer.wrap(bytes).get();
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public long readId() {
        long id;
        switch (idSize) {
            case 1:
                id = readByte();
                break;
            case 2:
                id = readShort();
                break;
            case 4:
                id = readInt();
                break;
            case 8:
                id = readLong();
                break;
            default:
                throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
        }
        return id;
    }

    public String readUtf8(long byteCount) {
        position += byteCount;
        try {
            return source.readUtf8(byteCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    void skip(int byteCount) {
        position += byteCount;
        try {
            source.skip(byteCount << 3); // skip(long) method in Java takes long argument, so we shift the bits to convert int to long
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    @Override
    public void close() {
        try {
            source.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InstanceDumpRecord readInstanceDumpRecord(
            long id) {
        int stackTraceSerialNumber = readInt();
        long classId = readId();
        int remainingBytesInInstance = readInt();
        byte[] fieldValues = readByteArray(remainingBytesInInstance);

        return new InstanceDumpRecord(
                id,
                stackTraceSerialNumber,
                classId,
                fieldValues);
    }

    public ClassDumpRecord readClassDumpRecord(
            long id) {
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

        List<StaticFieldRecord> staticFields = new ArrayList<>();
        int staticFieldCount = readUnsignedShort();
        for (int i = 0; i < staticFieldCount; i++) {
            long nameStringId = readId();
            int type = readUnsignedByte();
            HeapValue value = readValue(type);

            staticFields.add(
                    new StaticFieldRecord(
                            nameStringId,
                            type,
                            value));
        }

        List<FieldRecord> fields = new ArrayList<>();
        int fieldCount = readUnsignedShort();
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
                fields);
    }

    public ObjectArrayDumpRecord readObjectArrayDumpRecord(
            long id) {
        int stackTraceSerialNumber = readInt();
        int arrayLength = readInt();
        long arrayClassId = readId();
        long[] elementIds = readIdArray(arrayLength);
        return new ObjectArrayDumpRecord(
                id,
                stackTraceSerialNumber,
                arrayClassId,
                elementIds);
    }

    public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(
            long id) {
        int stackTraceSerialNumber = readInt();

        int arrayLength = readInt();
        int type = readUnsignedByte();
        switch (type) {
            case BOOLEAN_TYPE:
                return new BooleanArrayDump(
                        id, stackTraceSerialNumber, readBooleanArray(arrayLength));
            case CHAR_TYPE:
                return new CharArrayDump(
                        id, stackTraceSerialNumber, readCharArray(arrayLength));
            case FLOAT_TYPE:
                return new FloatArrayDump(
                        id, stackTraceSerialNumber, readFloatArray(arrayLength));
            case DOUBLE_TYPE:
                return new DoubleArrayDump(
                        id, stackTraceSerialNumber, readDoubleArray(arrayLength));
            case BYTE_TYPE:
                return new ByteArrayDump(
                        id, stackTraceSerialNumber, readByteArray(arrayLength));
            case SHORT_TYPE:
                return new ShortArrayDump(
                        id, stackTraceSerialNumber, readShortArray(arrayLength));
            case INT_TYPE:
                return new IntArrayDump(
                        id, stackTraceSerialNumber, readIntArray(arrayLength));
            case LONG_TYPE:
                return new LongArrayDump(
                        id, stackTraceSerialNumber, readLongArray(arrayLength));
            default:
                throw new IllegalStateException("Unexpected type " + type);
        }
    }

    public long tagPositionAfterReadingId() {
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