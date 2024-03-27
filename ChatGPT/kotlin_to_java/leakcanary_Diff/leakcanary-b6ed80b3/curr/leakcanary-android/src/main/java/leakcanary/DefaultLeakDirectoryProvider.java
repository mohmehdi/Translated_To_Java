
package leakcanary;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import com.squareup.leakcanary.R;
import leakcanary.HeapDumper.Companion;
import leakcanary.internal.LeakCanaryInternals;
import leakcanary.internal.RequestStoragePermissionActivity;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DefaultLeakDirectoryProvider implements LeakDirectoryProvider {

    private Context context;
    private boolean writeExternalStorageGranted = false;
    private boolean permissionNotificationDisplayed = false;

    public DefaultLeakDirectoryProvider(Context context, int maxStoredHeapDumps) {
        if (maxStoredHeapDumps < 1) {
            throw new IllegalArgumentException("maxStoredHeapDumps must be at least 1");
        }
        this.context = context.getApplicationContext();
    }

    @Override
    public List<File> listFiles(FilenameFilter filter) {
        if (!hasStoragePermission()) {
            requestWritePermissionNotification();
        }
        List<File> files = new ArrayList<>();

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

    @Override
    public boolean hasPendingHeapDump() {
        List<File> pendingHeapDumps = listFiles((dir, filename) -> filename.endsWith("_pending.hprof"));
        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < 600000) {
                return true;
            }
        }
        return false;
    }

    @Override
    public File newHeapDumpFile() {
        List<File> pendingHeapDumps = listFiles((dir, filename) -> filename.endsWith("_pending.hprof"));

        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < 600000) {
                CanaryLog.d("Could not dump heap, previous analysis still is in progress.");
                return Companion.RETRY_LATER;
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
                return Companion.RETRY_LATER;
            }
        }

        return new File(storageDirectory, UUID.randomUUID().toString() + "_pending.hprof");
    }

    @Override
    public void clearLeakDirectory() {
        List<File> allFilesExceptPending = listFiles((dir, filename) -> !filename.endsWith("_pending.hprof"));
        for (File file : allFilesExceptPending) {
            boolean deleted = file.delete();
            if (!deleted) {
                CanaryLog.d("Could not delete file %s", file.getPath());
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        if (writeExternalStorageGranted) {
            return true;
        }
        writeExternalStorageGranted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return writeExternalStorageGranted;
    }

    private void requestWritePermissionNotification() {
        if (permissionNotificationDisplayed) {
            return;
        }
        permissionNotificationDisplayed = true;

        RequestStoragePermissionActivity.createPendingIntent(context);
        String contentTitle = context.getString(R.string.leak_canary_permission_notification_title);
        String packageName = context.getPackageName();
        String contentText = context.getString(R.string.leak_canary_permission_notification_text, packageName);
        LeakCanaryInternals.showNotification(context, contentTitle, contentText, null, -0x21504111);
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
        List<File> hprofFiles = listFiles((dir, name) -> name.endsWith(".hprof"));
        int filesToRemove = hprofFiles.size() - 7;
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

    private static final int DEFAULT_MAX_STORED_HEAP_DUMPS = 7;
    private static final String HPROF_SUFFIX = ".hprof";
    private static final String PENDING_HEAPDUMP_SUFFIX = "_pending.hprof";
    private static final int ANALYSIS_MAX_DURATION_MS = 10 * 60 * 1000;
}