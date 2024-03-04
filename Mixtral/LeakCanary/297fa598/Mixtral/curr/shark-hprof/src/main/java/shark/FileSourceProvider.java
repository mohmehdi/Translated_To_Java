

package shark;

import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class FileSourceProvider implements DualSourceProvider {
    private final File file;

    public FileSourceProvider(File file) {
        this.file = file;
    }

    @Override
    public BufferedSource openStreamingSource() {
        return Okio.buffer(Okio.source(file.inputStream()));
    }

    @Override
    public RandomAccessSource openRandomAccessSource() {
        FileInputStream inputStream = new FileInputStream(file);
        FileChannel channel = inputStream.getChannel();
        return new RandomAccessSource() {
            @Override
            public long read(Buffer sink, long position, long byteCount) throws IOException {
                return channel.transferTo(position, byteCount, Channels.newChannel(sink));
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }
}