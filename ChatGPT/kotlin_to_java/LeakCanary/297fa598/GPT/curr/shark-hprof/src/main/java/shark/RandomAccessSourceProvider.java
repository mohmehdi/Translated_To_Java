

package shark;

import okio.Source;

public interface RandomAccessSourceProvider {
    RandomAccessSource openRandomAccessSource();
}