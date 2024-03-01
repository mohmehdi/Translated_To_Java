package okhttp3.tls.internal.der;

import okio.IOException;

public class BasicDerAdapter<T> implements DerAdapter<T> {
    private final String name;
    private final int tagClass;
    private final long tag;
    private final Codec<T> codec;
    private final boolean isOptional;
    private final T defaultValue;
    private final boolean typeHint;

    public BasicDerAdapter(String name, int tagClass, long tag, Codec<T> codec, boolean isOptional, T defaultValue, boolean typeHint) {
        this.name = name;
        this.tagClass = tagClass;
        this.tag = tag;
        this.codec = codec;
        this.isOptional = isOptional;
        this.defaultValue = defaultValue;
        this.typeHint = typeHint;
        require(tagClass >= 0);
        require(tag >= 0);
    }

    @Override
    public boolean matches(DerHeader header) {
        return header.getTagClass() == tagClass && header.getTag() == tag;
    }

    @Override
    public T fromDer(DerReader reader) throws IOException {
        DerHeader peekedHeader = reader.peekHeader();
        if (peekedHeader == null || peekedHeader.getTagClass() != tagClass || peekedHeader.getTag() != tag) {
            if (isOptional) return (T) defaultValue;
            throw new IOException("expected " + this + " but was " + peekedHeader + " at " + reader);
        }

        T result = reader.read(name, () -> codec.decode(reader));

        if (typeHint) {
            reader.setTypeHint(result);
        }

        return result;
    }

    @Override
    public void toDer(DerWriter writer, T value) {
        if (typeHint) {
            writer.setTypeHint(value);
        }

        if (isOptional && value == defaultValue) {
            return;
        }

        writer.write(name, tagClass, tag, () -> codec.encode(writer, value));
    }

    public BasicDerAdapter<T> withTag(int tagClass, long tag) {
        return copy(tagClass, tag);
    }

    public BasicDerAdapter<T> optional(T defaultValue) {
        return copy(true, defaultValue);
    }

    public BasicDerAdapter<T> asTypeHint() {
        return copy(typeHint);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + name.hashCode();
        result = 31 * result + tagClass;
        result = 31 * result + (int) tag;
        result = 31 * result + codec.hashCode();
        result = 31 * result + (isOptional ? 1 : 0);
        result = 31 * result + defaultValue.hashCode();
        result = 31 * result + (typeHint ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return name + " [" + tagClass + "/" + tag + "]";
    }

    public interface Codec<T> {
        void decode(DerReader reader);
        void encode(DerWriter writer, T value);
    }
}