

package okhttp3.tls.internal.der;

import okio.IOException;

import java.util.Objects;

public final class BasicDerAdapter<T> implements DerAdapter<T> {

    private final String name;
    private final int tagClass;
    private final long tag;
    private final Codec<T> codec;
    private final boolean isOptional;
    private final T defaultValue;
    private final boolean typeHint;

    public BasicDerAdapter(String name, int tagClass, long tag, Codec<T> codec, boolean isOptional, T defaultValue, boolean typeHint) {
        this.name = Objects.requireNonNull(name, "name == null");
        this.tagClass = tagClass;
        this.tag = tag;
        this.codec = Objects.requireNonNull(codec, "codec == null");
        this.isOptional = isOptional;
        this.defaultValue = defaultValue;
        this.typeHint = typeHint;
        if (tagClass < 0) throw new IllegalArgumentException("tagClass < 0");
        if (tag < 0) throw new IllegalArgumentException("tag < 0");
    }

    @Override
    public boolean matches(DerHeader header) {
        return header.getTagClass() == tagClass && header.getTag() == tag;
    }

    @Override
    public T readValue(DerReader reader) {
        DerHeader peekedHeader = reader.peekHeader();
        if (peekedHeader == null || peekedHeader.getTagClass() != tagClass || peekedHeader.getTag() != tag) {
            if (isOptional) return defaultValue;
            throw new IOException("expected " + this + " but was " + peekedHeader + " at " + reader);
        }

        T result = reader.read(name, derReader -> codec.decode(derReader));

        if (typeHint) {
            reader.setTypeHint(result);
        }

        return result;
    }

    @Override
    public void writeValue(DerWriter writer, T value) {
        if (typeHint) {
            writer.setTypeHint(value);
        }

        if (isOptional && Objects.equals(value, defaultValue)) {
            return;
        }

        writer.write(name, tagClass, tag, derWriter -> codec.encode(derWriter, value));
    }

    public BasicDerAdapter<T> withTag(int tagClass, long tag) {
        return new BasicDerAdapter<>(name, tagClass, tag, codec, isOptional, defaultValue, typeHint);
    }

    public BasicDerAdapter<T> optional(T defaultValue) {
        return new BasicDerAdapter<>(name, tagClass, tag, codec, true, defaultValue, typeHint);
    }

    public BasicDerAdapter<T> asTypeHint() {
        return new BasicDerAdapter<>(name, tagClass, tag, codec, isOptional, defaultValue, true);
    }

    

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + tagClass;
        result = 31 * result + (int) (tag ^ (tag >>> 32));
        result = 31 * result + codec.hashCode();
        result = 31 * result + (isOptional ? 1 : 0);
        result = 31 * result + Objects.hashCode(defaultValue);
        result = 31 * result + (typeHint ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return name + " [" + tagClass + "/" + tag + "]";
    }

    public interface Codec<T> {
        T decode(DerReader derReader) throws IOException;

        void encode(DerWriter derWriter, T value) throws IOException;
    }
}