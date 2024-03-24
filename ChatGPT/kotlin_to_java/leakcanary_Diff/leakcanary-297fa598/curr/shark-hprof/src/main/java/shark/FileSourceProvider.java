

package shark;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileSourceProvider implements DualSourceProvider {
    private final File file;

    public FileSourceProvider(File file) {
        this.file = file;
    }

    @Override
    public BufferedSource openStreamingSource() throws IOException {
        return Okio.buffer(Okio.source(file));
    }

    @Override
    public RandomAccessSource openRandomAccessSource() {
        FileChannel channel = null;
        try {
            channel = FileChannel.open(file.toPath());
            final FileChannel finalChannel = channel;
            return new RandomAccessSource() {
                @Override
                public long read(Buffer sink, long position, long byteCount) throws IOException {
                    return finalChannel.transferTo(position, byteCount, sink.outputStream());
                }

                @Override
                public void close() throws IOException {
                    finalChannel.close();
                }
            };
        } catch (IOException e) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
            throw new RuntimeException(e);
        }
    }
}