

package shark;

import java.io.File;
import java.lang.reflect.KClass;
import java.nio.channels.FileChannel;

@Deprecated("Replaced by HprofStreamingReader.readerFor or HprofRandomAccessReader.openReaderFor")
public class HprofReader {
    private final Hprof hprof;

    public HprofReader(Hprof hprof) {
        this.hprof = hprof;
    }

    private final int identifierByteSize = hprof.header.identifierByteSize;
    private final long startPosition = hprof.header.recordsPosition.toLong();

    public void readHprofRecords(
            Set<KClass< ? extends HprofRecord>> recordTypes,
            OnHprofRecordListener listener) {
        StreamingHprofReader.readerFor(hprof.getFile(), hprof.getHeader())
                .readRecords(recordTypes, listener);
    }
}