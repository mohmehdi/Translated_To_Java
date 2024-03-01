package okhttp3.tls.internal.der;

import okio.Buffer;
import okio.ByteString;

internal interface DerAdapter<T> {

  boolean matches(DerHeader header);

  T readValue(DerReader reader);

  void writeValue(DerWriter writer, T value);

  default ByteString toDer(T value) {
    Buffer buffer = new Buffer();
    DerWriter writer = new DerWriter(buffer);
    writeValue(writer, value);
    return buffer.readByteString();
  }

  default T fromDer(ByteString byteString) {
    Buffer buffer = new Buffer().write(byteString);
    DerReader reader = new DerReader(buffer);
    return readValue(reader);
  }

  @SuppressWarnings("unchecked")
  default BasicDerAdapter<T> withExplicitBox(int tagClass, long tag, Boolean forceConstructed) {
    BasicDerAdapter.Codec<T> codec = new BasicDerAdapter.Codec<T>() {
      @Override
      public T decode(DerReader reader) {
        return readValue(reader);
      }

      @Override
      public void encode(DerWriter writer, T value) {
        writeValue(writer, value);
        if (forceConstructed != null) {
          writer.constructed = forceConstructed;
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

  default BasicDerAdapter<List<T>> asSequenceOf(String name, int tagClass, long tag) {
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
        17L
    );
  }
}