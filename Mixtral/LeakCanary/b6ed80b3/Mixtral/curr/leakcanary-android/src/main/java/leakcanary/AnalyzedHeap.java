

package leakcanary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class AnalyzedHeap {
    final HeapDump heapDump;
    final AnalysisResult result;
    final File selfFile;

    final boolean heapDumpFileExists;
    final long selfLastModified;

    public AnalyzedHeap(HeapDump heapDump, AnalysisResult result, File selfFile) {
        this.heapDump = heapDump;
        this.result = result;
        this.selfFile = selfFile;
        this.heapDumpFileExists = heapDump.heapDumpFile.exists();
        this.selfLastModified = selfFile.lastModified();
    }

    public static File save(HeapDump heapDump, AnalysisResult result) {
        File analyzedHeapfile = new File(
                heapDump.heapDumpFile.getParentFile(),
                heapDump.heapDumpFile.getName() + ".result"
        );
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(analyzedHeapfile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(heapDump);
            oos.writeObject(result);
            return analyzedHeapfile;
        } catch (IOException e) {
            CanaryLog.d(e, "Could not save leak analysis result to disk.");
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    public static AnalyzedHeap load(File resultFile) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(resultFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            HeapDump heapDump = (HeapDump) ois.readObject();
            AnalysisResult result = (AnalysisResult) ois.readObject();
            return new AnalyzedHeap(heapDump, result, resultFile);
        } catch (IOException e) {
            boolean deleted = resultFile.delete();
            if (deleted) {
                CanaryLog.d(
                        e, "Could not read result file %s, deleted it.", resultFile
                );
            } else {
                CanaryLog.d(
                        e, "Could not read result file %s, could not delete it either.",
                        resultFile
                );
            }
        } catch (ClassNotFoundException e) {
            boolean deleted = resultFile.delete();
            if (deleted) {
                CanaryLog.d(
                        e, "Could not read result file %s, deleted it.", resultFile
                );
            } else {
                CanaryLog.d(
                        e, "Could not read result file %s, could not delete it either.",
                        resultFile
                );
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }
}