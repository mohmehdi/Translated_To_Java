package com.github.shadowsocks.database;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class KeyValuePair {
    public static final int TYPE_UNINITIALIZED = 0;
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_INT = 3;
    public static final int TYPE_LONG = 4;
    public static final int TYPE_STRING = 5;
    public static final int TYPE_STRING_SET = 6;

    @DatabaseField(id = true)
    public String key = "";
    @DatabaseField
    public int valueType = TYPE_UNINITIALIZED;
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    public byte[] value = new byte[0];

    public Boolean getBoolean() {
        if (valueType == TYPE_BOOLEAN) {
            return ByteBuffer.wrap(value).get() != 0;
        } else {
            return null;
        }
    }

    public Float getFloat() {
        if (valueType == TYPE_FLOAT) {
            return ByteBuffer.wrap(value).getFloat();
        } else {
            return null;
        }
    }

    public Integer getInt() {
        if (valueType == TYPE_INT) {
            return ByteBuffer.wrap(value).getInt();
        } else {
            return null;
        }
    }

    public Long getLong() {
        if (valueType == TYPE_LONG) {
            return ByteBuffer.wrap(value).getLong();
        } else {
            return null;
        }
    }

    public String getString() {
        if (valueType == TYPE_STRING) {
            return new String(value);
        } else {
            return null;
        }
    }

    public Set<String> getStringSet() {
        if (valueType == TYPE_STRING_SET) {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            Set<String> result = new HashSet<>();
            while (buffer.hasRemaining()) {
                byte[] chArr = new byte[buffer.getInt()];
                buffer.get(chArr);
                result.add(new String(chArr));
            }
            return result;
        } else {
            return null;
        }
    }

    public KeyValuePair() {
    }

    public KeyValuePair(String key) {
        this.key = key;
    }

    public KeyValuePair put(boolean value) {
        valueType = TYPE_BOOLEAN;
        this.value = ByteBuffer.allocate(1).put((byte) (value ? 1 : 0)).array();
        return this;
    }

    public KeyValuePair put(float value) {
        valueType = TYPE_FLOAT;
        this.value = ByteBuffer.allocate(4).putFloat(value).array();
        return this;
    }

    public KeyValuePair put(int value) {
        valueType = TYPE_INT;
        this.value = ByteBuffer.allocate(4).putInt(value).array();
        return this;
    }

    public KeyValuePair put(long value) {
        valueType = TYPE_LONG;
        this.value = ByteBuffer.allocate(8).putLong(value).array();
        return this;
    }

    public KeyValuePair put(String value) {
        valueType = TYPE_STRING;
        this.value = value.getBytes();
        return this;
    }

    public KeyValuePair put(Set<String> value) {
        valueType = TYPE_STRING_SET;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (String v : value) {
            stream.write(ByteBuffer.allocate(4).putInt(v.length()).array());
            stream.write(v.getBytes());
        }
        this.value = stream.toByteArray();
        return this;
    }
}