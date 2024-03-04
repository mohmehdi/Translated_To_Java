

package shark;

import java.io.InputStream; // StreamingSourceProvider
import java.io.RandomAccessFile; // RandomAccessSourceProvider

public interface DualSourceProvider extends StreamingSourceProvider, RandomAccessSourceProvider {
}