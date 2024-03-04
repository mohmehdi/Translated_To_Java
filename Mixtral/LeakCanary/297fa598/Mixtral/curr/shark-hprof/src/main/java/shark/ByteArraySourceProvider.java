

package shark;

import okio.Buffer;
import okio.BufferedSource;
import okio.Source;
import okio.RandomAccessSource;
import java.io.IOException;

class ByteArraySourceProvider implements DualSourceProvider {
  private final byte[] byteArray;

  public ByteArraySourceProvider(byte[] byteArray) {
    this.byteArray = byteArray;
  }

  @Override
  public BufferedSource openStreamingSource() {
    Buffer buffer = new Buffer();
    buffer.write(byteArray);
    return buffer;
  }

  @Override
  public RandomAccessSource openRandomAccessSource() {
    return new RandomAccessSource() {
      private boolean closed = false;

      @Override
      public long read(Buffer sink, long position, long byteCount) throws IOException {
        if (closed) {
          throw new IOException("Source closed");
        }
        long maxByteCount = Math.min(byteCount, byteArray.length - position);
        sink.write(byteArray, (int) position, (int) maxByteCount);
        return maxByteCount;
      }

      @Override
      public void close() {
        closed = true;
      }
    };
  }
}