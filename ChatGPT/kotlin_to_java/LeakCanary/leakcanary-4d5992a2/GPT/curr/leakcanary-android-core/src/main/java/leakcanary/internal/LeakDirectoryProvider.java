
package leakcanary.internal;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import com.squareup.leakcanary.core.R;
import leakcanary.CanaryLog;
import leakcanary.internal.HeapDumper;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

class LeakDirectoryProvider {
    private static final int DEFAULT_MAX_STORED_HEAP_DUMPS = 7;

    private static final String HPROF_SUFFIX = ".hprof";
    private static final String PENDING_HEAPDUMP_SUFFIX = "_pending" + HPROF_SUFFIX;

    /** 10 minutes */
    private static final long ANALYSIS_MAX_DURATION_MS = 10 * 60 * 1000;
    private final Context context;
    private volatile boolean writeExternalStorageGranted = false;
    private volatile boolean permissionNotificationDisplayed = false;

    LeakDirectoryProvider(Context context, int maxStoredHeapDumps) {
        if (maxStoredHeapDumps < 1) {
            throw new IllegalArgumentException("maxStoredHeapDumps must be at least 1");
        }
        this.context = context.getApplicationContext();
    }

    ArrayList<File> listFiles(FilenameFilter filter) {
        if (!hasStoragePermission()) {
            requestWritePermissionNotification();
        }
        ArrayList<File> files = new ArrayList<>();

        File[] externalFiles = externalStorageDirectory().listFiles(filter);
        if (externalFiles != null) {
            files.addAll(Arrays.asList(externalFiles));
        }

        File[] appFiles = appStorageDirectory().listFiles(filter);
        if (appFiles != null) {
            files.addAll(Arrays.asList(appFiles));
        }
        return files;
    }

    boolean hasPendingHeapDump() {
        ArrayList<File> pendingHeapDumps = listFiles((dir, filename) -> filename.endsWith(HeapDumper.PENDING_HEAPDUMP_SUFFIX));
        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < HeapDumper.ANALYSIS_MAX_DURATION_MS) {
                return true;
            }
        }
        return false;
    }

    File newHeapDumpFile() {
        ArrayList<File> pendingHeapDumps = listFiles((dir, filename) -> filename.endsWith(HeapDumper.PENDING_HEAPDUMP_SUFFIX));

        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < HeapDumper.ANALYSIS_MAX_DURATION_MS) {
                CanaryLog.d("Could not dump heap, previous analysis still is in progress.");
                return HeapDumper.RETRY_LATER;
            }
        }

        cleanupOldHeapDumps();

        File storageDirectory = externalStorageDirectory();
        if (!directoryWritableAfterMkdirs(storageDirectory)) {
            if (!hasStoragePermission()) {
                CanaryLog.d("WRITE_EXTERNAL_STORAGE permission not granted");
                requestWritePermissionNotification();
            } else {
                String state = Environment.getExternalStorageState();
                if (!Environment.MEDIA_MOUNTED.equals(state)) {
                    CanaryLog.d("External storage not mounted, state: %s", state);
                } else {
                    CanaryLog.d("Could not create heap dump directory in external storage: [%s]", storageDirectory.getAbsolutePath());
                }
            }

            storageDirectory = appStorageDirectory();
            if (!directoryWritableAfterMkdirs(storageDirectory)) {
                CanaryLog.d("Could not create heap dump directory in app storage: [%s]", storageDirectory.getAbsolutePath());
                return HeapDumper.RETRY_LATER;
            }
        }

        return new File(storageDirectory, UUID.randomUUID().toString() + HeapDumper.PENDING_HEAPDUMP_SUFFIX);
    }

    void clearLeakDirectory() {
        ArrayList<File> allFilesExceptPending = listFiles((dir, filename) -> !filename.endsWith(HeapDumper.PENDING_HEAPDUMP_SUFFIX));
        for (File file : allFilesExceptPending) {
            boolean deleted = file.delete();
            if (!deleted) {
                CanaryLog.d("Could not delete file %s", file.getPath());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        if (writeExternalStorageGranted) {
            return true;
        }
        writeExternalStorageGranted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return writeExternalStorageGranted;
    }

    void requestWritePermissionNotification() {
        if (permissionNotificationDisplayed) {
            return;
        }
        permissionNotificationDisplayed = true;

        // Code for creating notification is not available in the original code
    }

    private File externalStorageDirectory() {
        File downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDirectory, "leakcanary-" + context.getPackageName());
    }

    private File appStorageDirectory() {
        File appFilesDirectory = context.getFilesDir();
        return new File(appFilesDirectory, "leakcanary");
    }

    private boolean directoryWritableAfterMkdirs(File directory) {
        boolean success = directory.mkdirs();
        return (success || directory.exists()) && directory.canWrite();
    }

    private void cleanupOldHeapDumps() {
        ArrayList<File> hprofFiles = listFiles((dir, name) -> name.endsWith(HeapDumper.HPROF_SUFFIX));
        int filesToRemove = hprofFiles.size() - HeapDumper.DEFAULT_MAX_STORED_HEAP_DUMPS;
        if (filesToRemove > 0) {
            CanaryLog.d("Removing %d heap dumps", filesToRemove);

            hprofFiles.sort((lhs, rhs) -> Long.valueOf(lhs.lastModified()).compareTo(rhs.lastModified()));
            for (int i = 0; i < filesToRemove; i++) {
                boolean deleted = hprofFiles.get(i).delete();
                if (!deleted) {
                    CanaryLog.d("Could not delete old hprof file %s", hprofFiles.get(i).getPath());
                }
            }
        }
    }
}