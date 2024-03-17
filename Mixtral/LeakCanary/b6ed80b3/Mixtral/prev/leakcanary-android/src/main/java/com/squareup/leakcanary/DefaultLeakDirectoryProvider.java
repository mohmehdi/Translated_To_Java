

package com.squareup.leakcanary;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DefaultLeakDirectoryProvider implements LeakDirectoryProvider {

    private static final String TAG = "DefaultLeakDirectoryProvider";
    private static final int REQUEST_CODE = 123;
    private static final String HPROF_SUFFIX = ".hprof";
    private static final String PENDING_HEAPDUMP_SUFFIX = "_pending" + HPROF_SUFFIX;
    private static final long ANALYSIS_MAX_DURATION_MS = 10 * 60 * 1000;

    private final Context context;
    private final int maxStoredHeapDumps;

    @Volatile
    private boolean writeExternalStorageGranted;
    @Volatile
    private boolean permissionNotificationDisplayed;

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
        FilenameFilter pendingHeapDumpFilter = (dir, name) -> name.endsWith(PENDING_HEAPDUMP_SUFFIX);
        File[] pendingHeapDumps = listFiles(pendingHeapDumpFilter);
        for (File file : pendingHeapDumps) {
            if (System.currentTimeMillis() - file.lastModified() < ANALYSIS_MAX_DURATION_MS) {
                return true;
            }
        }
        return false;
    }

    @Override
    public File newHeapDumpFile() {
        FilenameFilter pendingHeapDumpFilter = (dir, name) -> name.endsWith(PENDING_HEAPDUMP_SUFFIX);
        File[] pendingHeapDumps = listFiles(pendingHeapDumpFilter);

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
                    Log.d(
                            TAG,
                            "Could not create heap dump directory in external storage: [%s]",
                            storageDirectory.getAbsolutePath()
                    );
                }
            }

            storageDirectory = appStorageDirectory();
            if (!directoryWritableAfterMkdirs(storageDirectory)) {
                Log.d(
                        TAG,
                        "Could not create heap dump directory in app storage: [%s]",
                        storageDirectory.getAbsolutePath()
                );
                return RETRY_LATER;
            }
        }

        return new File(storageDirectory, UUID.randomUUID().toString() + PENDING_HEAPDUMP_SUFFIX);
    }

    @Override
    public void clearLeakDirectory() {
        FilenameFilter filter = (dir, name) -> !name.endsWith(PENDING_HEAPDUMP_SUFFIX);
        File[] allFilesExceptPending = listFiles(filter);
        for (File file : allFilesExceptPending) {
            boolean deleted = file.delete();
            if (!deleted) {
                Log.d(TAG, "Could not delete file %s", file.getPath());
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

        writeExternalStorageGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return writeExternalStorageGranted;
    }

    private void requestWritePermissionNotification() {
        if (permissionNotificationDisplayed) {
            return;
        }
        permissionNotificationDisplayed = true;

        Intent intent = new Intent(context, RequestStoragePermissionActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, REQUEST_CODE, intent, 0);
        String contentTitle = context.getString(R.string.leak_canary_permission_notification_title);
        String packageName = context.getPackageName();
        String contentText = context.getString(R.string.leak_canary_permission_notification_text, packageName);
        LeakCanaryInternals.showNotification(context, contentTitle, contentText, pendingIntent, -0x21504111);
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
        FilenameFilter hprofFilter = (dir, name) -> name.endsWith(HPROF_SUFFIX);
        File[] hprofFiles = listFiles(hprofFilter);
        int filesToRemove = hprofFiles.length - maxStoredHeapDumps;
        if (filesToRemove > 0) {
            Log.d(TAG, "Removing %d heap dumps", filesToRemove);

            Arrays.sort(hprofFiles, (lhs, rhs) -> Long.valueOf(lhs.lastModified()).compareTo(rhs.lastModified()));
            for (int i = 0; i < filesToRemove; i++) {
                boolean deleted = hprofFiles[i].delete();
                if (!deleted) {
                    Log.d(TAG, "Could not delete old hprof file %s", hprofFiles[i].getPath());
                }
            }
        }
    }
}