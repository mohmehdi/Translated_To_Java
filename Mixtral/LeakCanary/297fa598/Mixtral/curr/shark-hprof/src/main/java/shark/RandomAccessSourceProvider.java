

package shark;

import okio.RandomAccessSource;

import java.io.IOException;

public interface RandomAccessSourceProvider {
    RandomAccessSource openRandomAccessSource() throws IOException;
}