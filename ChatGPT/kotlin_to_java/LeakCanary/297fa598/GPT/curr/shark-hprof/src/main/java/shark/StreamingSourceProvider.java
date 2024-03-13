

package shark;

import okio.BufferedSource;
import okio.Source;

public interface StreamingSourceProvider {
    BufferedSource openStreamingSource();
}