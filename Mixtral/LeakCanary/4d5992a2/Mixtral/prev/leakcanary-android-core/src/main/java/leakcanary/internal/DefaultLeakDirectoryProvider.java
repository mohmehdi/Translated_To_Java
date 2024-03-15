
package leakcanary.internal;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
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

public class DefaultLeakDirectoryProvider implements LeakDirectoryProvider {

    private static final String TAG = "DefaultLeakDirectoryProvider";
    private static final int DEFAULT_MAX_STORED_HEAP_DUMPS = 7;
    private static final long ANALYSIS_MAX_DURATION_MS = 10 * 60 * 1000;
    private static final String HPROF_SUFFIX = ".hprof";
    private static final String PENDING_HEAPDUMP_SUFFIX = "_pending" + HPROF_SUFFIX;

    private final Context context;
    private int maxStoredHeapDumps;
    private volatile boolean writeExternalStorageGranted;
    private volatile boolean permissionNotificationDisplayed;

    public DefaultLeakDirectoryProvider(Context context, int maxStoredHeapDumps) {
        if (maxStoredHeapDumps < 1) {
            throw new IllegalArgumentException("maxStoredHeapDumps must be at least 1");
        }
        this.context = context.getApplicationContext();
        this.maxStoredHeapDumps = maxStoredHeapDumps;
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
        List<File> pendingHeapDumps =
                listFiles(filename -> filename.endsWith(PENDING_HEAPDUMP_SUFFIX));
        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < ANALYSIS_MAX_DURATION_MS) {
                return true;
            }
        }
        return false;
    }

    @Override
    public File newHeapDumpFile() {
        List<File> pendingHeapDumps =
                listFiles(filename -> filename.endsWith(PENDING_HEAPDUMP_SUFFIX));

        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < ANALYSIS_MAX_DURATION_MS) {
                Log.d(TAG, "Could not dump heap, previous analysis still is in progress.");
                return RETRY_LATER;
            }
        }

        cleanupOldHeapDumps();

        File storageDirectory = externalStorageDirectory();
        if (!directoryWritableAfterMkdirs(storageDirectory)) {
            if (!hasStoragePermission()) {
                Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission not granted");
                requestWritePermissionNotification();
            } else {
                String state = Environment.getExternalStorageState();
                if (!Environment.MEDIA_MOUNTED.equals(state)) {
                    Log.d(TAG, "External storage not mounted, state: %s", state);
                } else {
                    Log.d(TAG,
                            "Could not create heap dump directory in external storage: [%s]",
                            storageDirectory.getAbsolutePath());
                }
            }

            storageDirectory = appStorageDirectory();
            if (!directoryWritableAfterMkdirs(storageDirectory)) {
                Log.d(TAG,
                        "Could not create heap dump directory in app storage: [%s]",
                        storageDirectory.getAbsolutePath());
                return RETRY_LATER;
            }
        }

        return new File(storageDirectory, UUID.randomUUID().toString() + PENDING_HEAPDUMP_SUFFIX);
    }

    @Override
    public void clearLeakDirectory() {
        List<File> allFilesExceptPending =
                listFiles(filename -> !filename.endsWith(PENDING_HEAPDUMP_SUFFIX));
        for (File file : allFilesExceptPending) {
            boolean deleted = file.delete();
            if (!deleted) {
                Log.d(TAG, "Could not delete file %s", file.getPath());
            }
        }
    }

    @TargetApi(VERSION_CODES.M)
    private boolean hasStoragePermission() {
        if (VERSION.SDK_INT < VERSION_CODES.M) {
            return true;
        }

        if (writeExternalStorageGranted) {
            return true;
        }
        int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        writeExternalStorageGranted = permission == PackageManager.PERMISSION_GRANTED;
        return writeExternalStorageGranted;
    }

    private void requestWritePermissionNotification() {
        if (permissionNotificationDisplayed) {
            return;
        }
        permissionNotificationDisplayed = true;

        Intent intent = RequestStoragePermissionActivity.createIntent(context);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
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
        List<File> hprofFiles = listFiles(filename -> filename.endsWith(HPROF_SUFFIX));
        int filesToRemove = hprofFiles.size() - maxStoredHeapDumps;
        if (filesToRemove > 0) {
            Log.d(TAG, "Removing %d heap dumps", filesToRemove);

            hprofFiles.sort(Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < filesToRemove; i++) {
                boolean deleted = hprofFiles.get(i).delete();
                if (!deleted) {
                    Log.d(TAG, "Could not delete old hprof file %s", hprofFiles.get(i).getPath());
                }
            }
        }
    }
}