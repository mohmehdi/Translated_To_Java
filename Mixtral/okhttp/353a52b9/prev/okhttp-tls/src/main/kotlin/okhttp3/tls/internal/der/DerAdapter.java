

package okhttp3.tls.internal.der;

import okio.Buffer;
import okio.ByteString;

public interface DerAdapter<T> {

  boolean matches(DerHeader header);

  T readValue(DerReader reader);

  void writeValue(DerWriter writer, T value);

    public static <T> ByteString toDer(T value) {
        Buffer buffer = new Buffer();
        DEROutputStream writer = new DEROutputStream(buffer);
        try {
            writeValue(writer, value);
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode to DER", e);
        }
        return buffer.readByteString();
    }

  T fromDer(ByteString byteString);

  default BasicDerAdapter<T> withExplicitBox(
      int tagClass,
      long tag,
      Boolean forceConstructed) {
    BasicDerAdapter.Codec<T> codec = new BasicDerAdapter.Codec<T>() {
      @Override
      public T decode(DerReader reader) {
        return readValue(reader);
      }

      @Override
      public void encode(DerWriter writer, T value) {
        writeValue(writer, value);
        if (forceConstructed != null) {
          writer.setConstructed(forceConstructed);
        }
      }
    };

    return new BasicDerAdapter<>(
        "EXPLICIT",
        tagClass,
        tag,
        codec
    );
  }

  default BasicDerAdapter<List<T>> asSequenceOf(
      String name,
      int tagClass,
      long tag) {
    BasicDerAdapter.Codec<List<T>> codec = new BasicDerAdapter.Codec<List<T>>() {
      @Override
      public void encode(DerWriter writer, List<T> value) {
        for (T v : value) {
          writeValue(writer, v);
        }
      }

      @Override
      public List<T> decode(DerReader reader) {
        List<T> result = new ArrayList<>();
        while (reader.hasNext()) {
          result.add(readValue(reader));
        }
        return result;
      }
    };

    return new BasicDerAdapter<>(name, tagClass, tag, codec);
  }

  default BasicDerAdapter<List<T>> asSetOf() {
    return asSequenceOf(
        "SET OF",
        DerHeader.TAG_CLASS_UNIVERSAL,
        17
    );
  }
}