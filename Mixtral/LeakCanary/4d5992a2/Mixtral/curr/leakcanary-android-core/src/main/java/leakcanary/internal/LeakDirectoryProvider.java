
package leakcanary.internal;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LeakDirectoryProvider {

    private static final int DEFAULT_MAX_STORED_HEAP_DUMPS = 7;
    private static final String HPROF_SUFFIX = ".hprof";
    private static final String PENDING_HEAPDUMP_SUFFIX = "_pending" + HPROF_SUFFIX;
    private static final long ANALYSIS_MAX_DURATION_MS = 10 * 60 * 1000;

    private final Context context;
    private int maxStoredHeapDumps;
    private boolean writeExternalStorageGranted;
    private boolean permissionNotificationDisplayed;

    public LeakDirectoryProvider(Context context, int maxStoredHeapDumps) {
        if (maxStoredHeapDumps < 1) {
            throw new IllegalArgumentException("maxStoredHeapDumps must be at least 1");
        }
        this.context = context.getApplicationContext();
        this.maxStoredHeapDumps = maxStoredHeapDumps;
    }

    public LeakDirectoryProvider(Context context) {
        this(context, DEFAULT_MAX_STORED_HEAP_DUMPS);
    }

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

    public boolean hasPendingHeapDump() {
        FilenameFilter pendingHeapDumpFilter = (dir, name) -> name.endsWith(PENDING_HEAPDUMP_SUFFIX);
        List<File> pendingHeapDumps = listFiles(pendingHeapDumpFilter);
        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < ANALYSIS_MAX_DURATION_MS) {
                return true;
            }
        }
        return false;
    }

    public File newHeapDumpFile() {
        FilenameFilter pendingHeapDumpFilter = (dir, name) -> name.endsWith(PENDING_HEAPDUMP_SUFFIX);
        List<File> pendingHeapDumps = listFiles(pendingHeapDumpFilter);

        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < ANALYSIS_MAX_DURATION_MS) {
                CanaryLog.d("Could not dump heap, previous analysis still is in progress.");
                return RETRY_LATER;
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
                    CanaryLog.d("Could not create heap dump directory in external storage: [%s]",
                            storageDirectory.getAbsolutePath());
                }
            }

            storageDirectory = appStorageDirectory();
            if (!directoryWritableAfterMkdirs(storageDirectory)) {
                CanaryLog.d("Could not create heap dump directory in app storage: [%s]",
                        storageDirectory.getAbsolutePath());
                return RETRY_LATER;
            }
        }

        return new File(storageDirectory, UUID.randomUUID().toString() + PENDING_HEAPDUMP_SUFFIX);
    }

    public void clearLeakDirectory() {
        FilenameFilter filter = (dir, name) -> !name.endsWith(PENDING_HEAPDUMP_SUFFIX);
        List<File> allFilesExceptPending = listFiles(filter);
        for (File file : allFilesExceptPending) {
            boolean deleted = file.delete();
            if (!deleted) {
                CanaryLog.d("Could not delete file %s", file.getPath());
            }
        }
    }

    @TargetApi(VERSION_CODES.M)
    public boolean hasStoragePermission() {
        if (VERSION.SDK_INT < VERSION_CODES.M) {
            return true;
        }

        if (writeExternalStorageGranted) {
            return ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        writeExternalStorageGranted = ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return writeExternalStorageGranted;
    }

    public void requestWritePermissionNotification() {
        if (permissionNotificationDisplayed) {
            return;
        }
        permissionNotificationDisplayed = true;

        PendingIntent pendingIntent = RequestStoragePermissionActivity.createPendingIntent(context);
        String contentTitle = context.getString(R.string.leak_canary_permission_notification_title);
        String packageName = context.getPackageName();
        String contentText = context.getString(R.string.leak_canary_permission_notification_text, packageName);
        LeakCanaryUtils.showNotification(context, contentTitle, contentText, pendingIntent, -0x21504111);
    }

    private File externalStorageDirectory() {
        File downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDirectory, "leakcanary-" + context.getPackageName());
    }

    private File appStorageDirectory() {
        return new File(context.getFilesDir(), "leakcanary");
    }

    private boolean directoryWritableAfterMkdirs(File directory) {
        boolean success = directory.mkdirs();
        return (success || directory.exists()) && directory.canWrite();
    }

    private void cleanupOldHeapDumps() {
        FilenameFilter hprofFilesFilter = (dir, name) -> name.endsWith(HPROF_SUFFIX);
        List<File> hprofFiles = listFiles(hprofFilesFilter);
        int filesToRemove = hprofFiles.size() - maxStoredHeapDumps;
        if (filesToRemove > 0) {
            CanaryLog.d("Removing %d heap dumps", filesToRemove);

            hprofFiles.sort(new Comparator<File>() {

            });

            for (int i = 0; i < filesToRemove; i++) {
                boolean deleted = hprofFiles.get(i).delete();
                if (!deleted) {
                    CanaryLog.d("Could not delete old hprof file %s", hprofFiles.get(i).getPath());
                }
            }
        }
    }
}