

package com.squareup.picasso3;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.provider.Settings.Global;
import android.util.Log;
import androidx.core.content.ContextCompat;
import okio.BufferedSource;
import okio.ByteString;

import java.io.File;
import java.io.FileNotFoundException;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Utils {
    public static final String THREAD_PREFIX = "Picasso-";
    public static final String THREAD_IDLE_NAME = THREAD_PREFIX + "Idle";
    private static final String PICASSO_CACHE = "picasso-cache";
    private static final long MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024;
    public static final int THREAD_LEAK_CLEANING_MS = 1000;

    public static final StringBuilder MAIN_THREAD_KEY_BUILDER = new StringBuilder();

    public static final String OWNER_MAIN = "Main";
    public static final String OWNER_DISPATCHER = "Dispatcher";
    public static final String OWNER_HUNTER = "Hunter";
    public static final String VERB_CREATED = "created";
    public static final String VERB_CHANGED = "changed";
    public static final String VERB_IGNORED = "ignored";
    public static final String VERB_ENQUEUED = "enqueued";
    public static final String VERB_CANCELED = "canceled";
    public static final String VERB_RETRYING = "retrying";
    public static final String VERB_EXECUTING = "executing";
    public static final String VERB_DECODED = "decoded";
    public static final String VERB_TRANSFORMED = "transformed";
    public static final String VERB_JOINED = "joined";
    public static final String VERB_REMOVED = "removed";
    public static final String VERB_DELIVERED = "delivered";
    public static final String VERB_REPLAYING = "replaying";
    public static final String VERB_COMPLETED = "completed";
    public static final String VERB_ERRORED = "errored";
    public static final String VERB_PAUSED = "paused";
    public static final String VERB_RESUMED = "resumed";

    private static final ByteString WEBP_FILE_HEADER_RIFF = ByteString.encodeUtf8("RIFF");
    private static final ByteString WEBP_FILE_HEADER_WEBP = ByteString.encodeUtf8("WEBP");

    public static <T> T checkNotNull(T value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        return value;
    }

    public static void checkNotMain() {
        check(!isMain(), "Method call should not happen from the main thread.");
    }

    public static void checkMain() {
        check(isMain(), "Method call should happen from the main thread.");
    }

    private static boolean isMain() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static String getLogIdsForHunter(BitmapHunter hunter, String prefix) {
        StringBuilder builder = new StringBuilder();
        builder.append(prefix);
        Action action = hunter.getAction();
        if (action != null) {
            builder.append(action.getRequest().logId());
        }
        List<Action> actions = hunter.getActions();
        if (actions != null) {
            for (int i = 0; i < actions.size(); i++) {
                if (i > 0 || action != null) {
                    builder.append(", ");
                }
                builder.append(actions.get(i).getRequest().logId());
            }
        }
        return builder.toString();
    }

    public static void log(String owner, String verb, String logId, String extras) {
        Log.d(TAG, String.format("%1$-11s %2$-12s %3$s %4$s", owner, verb, logId, extras != null ? extras : ""));
    }

    public static File createDefaultCacheDir(Context context) {
        File cache = new File(context.getApplicationContext().getCacheDir(), PICASSO_CACHE);
        if (!cache.exists()) {
            cache.mkdirs();
        }
        return cache;
    }

    public static long calculateDiskCacheSize(File dir) {
        long size = MIN_DISK_CACHE_SIZE;

        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long blockCount = statFs.getBlockCountLong();
            long blockSize = statFs.getBlockSizeLong();
            long available = blockCount * blockSize;

            size = available / 50;
        } catch (IllegalArgumentException ignored) {
        }

        return max(min(size, MAX_DISK_CACHE_SIZE), MIN_DISK_CACHE_SIZE);
    }

    public static int calculateMemoryCacheSize(Context context) {
        ActivityManager am = ContextCompat.getSystemService(context, ActivityManager.class);
        boolean largeHeap = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
        int memoryClass = largeHeap ? am.getLargeMemoryClass() : am.getMemoryClass();

        return (int) (1024L * 1024L * memoryClass / 7);
    }

    public static boolean isAirplaneModeOn(Context context) {
        try {
            int airplaneMode = Global.getInt(context.getContentResolver(), Global.AIRPLANE_MODE_ON, 0);
            return airplaneMode != 0;
        } catch (NullPointerException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static boolean hasPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isWebPFile(BufferedSource source) {
        return source.rangeEquals(0, WEBP_FILE_HEADER_RIFF) &&
                source.rangeEquals(8, WEBP_FILE_HEADER_WEBP);
    }

    public static int getResourceId(Resources resources, Request data) throws FileNotFoundException {
        if (data.getResourceId() != 0 || data.getUri() == null) {
            return data.getResourceId();
        }

        String pkg = data.getUri().getAuthority();
        if (pkg == null) {
            throw new FileNotFoundException("No package provided: " + data.getUri());
        }

        List<String> segments = data.getUri().getPathSegments();
        int size = segments != null ? segments.size() : 0;
        switch (size) {
            case 0:
                throw new FileNotFoundException("No path segments: " + data.getUri());
            case 1:
                try {
                    return Integer.parseInt(segments.get(0));
                } catch (NumberFormatException e) {
                    throw new FileNotFoundException("Last path segment is not a resource ID: " + data.getUri());
                }
            case 2:
                String type = segments.get(0);
                String name = segments.get(1);
                return resources.getIdentifier(name, type, pkg);
            default:
                throw new FileNotFoundException("More than two path segments: " + data.getUri());
        }
    }

    public static Resources getResources(Context context, Request data) throws FileNotFoundException {
        if (data.getResourceId() != 0 || data.getUri() == null) {
            return context.getResources();
        }

        try {
            String pkg = data.getUri().getAuthority();
            if (pkg == null) {
                throw new FileNotFoundException("No package provided: " + data.getUri());
            }
            return context.getPackageManager().getResourcesForApplication(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            throw new FileNotFoundException("Unable to obtain resources for package: " + data.getUri());
        }
    }

    public static void flushStackLocalLeaks(Looper looper) {
        Handler handler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                sendMessageDelayed(obtainMessage(), THREAD_LEAK_CLEANING_MS);
            }
        };
        handler.sendMessageDelayed(handler.obtainMessage(), THREAD_LEAK_CLEANING_MS);
    }
}