

package shark;

import kotlin.reflect.KClass;

@Deprecated("Replaced by HprofStreamingReader.readerFor or HprofRandomAccessReader.openReaderFor")
public class HprofReader {
    private final Hprof hprof;

    public HprofReader(Hprof hprof) {
        this.hprof = hprof;
    }

    public int getIdentifierByteSize() {
        return hprof.getHeader().getIdentifierByteSize();
    }

    public long getStartPosition() {
        return hprof.getHeader().getRecordsPosition();
    }

    public void readHprofRecords(Set<KClass<? extends HprofRecord>> recordTypes, OnHprofRecordListener listener) {
        StreamingHprofReader reader = StreamingHprofReader.readerFor(hprof.getFile(), hprof.getHeader());
        reader.readRecords(recordTypes, listener);
    }
}