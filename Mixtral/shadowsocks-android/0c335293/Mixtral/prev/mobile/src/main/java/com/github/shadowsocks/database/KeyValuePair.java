

package com.github.shadowsocks.database;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.table.DatabaseTable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

@DatabaseTable(tableName = "key_value_pair")
public class KeyValuePair {
    public static final int TYPE_UNINITIALIZED = 0;
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_INT = 3;
    public static final int TYPE_LONG = 4;
    public static final int TYPE_STRING = 5;
    public static final int TYPE_STRING_SET = 6;

    @DatabaseField(id = true)
    private String key;
    @DatabaseField
    private int valueType = TYPE_UNINITIALIZED;
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] value;

    public KeyValuePair() {
    }

    public KeyValuePair(String key) {
        this();
        this.key = key;
    }

    public Boolean getBoolean() {
        if (valueType == TYPE_BOOLEAN) {
            return ByteBuffer.wrap(value).get() != 0;
        }
        return null;
    }

    public Float getFloat() {
        if (valueType == TYPE_FLOAT) {
            return ByteBuffer.wrap(value).getFloat();
        }
        return null;
    }

    public Integer getInt() {
        if (valueType == TYPE_INT) {
            return ByteBuffer.wrap(value).getInt();
        }
        return null;
    }

    public Long getLong() {
        if (valueType == TYPE_LONG) {
            return ByteBuffer.wrap(value).getLong();
        }
        return null;
    }

    public String getString() {
        if (valueType == TYPE_STRING) {
            return new String(value);
        }
        return null;
    }

    public Set<String> getStringSet() {
        if (valueType == TYPE_STRING_SET) {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            Set<String> result = new HashSet<>();
            while (buffer.hasRemaining()) {
                int length = buffer.getInt();
                byte[] chArr = new byte[length];
                buffer.get(chArr);
                result.add(new String(chArr));
            }
            return result;
        }
        return null;
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