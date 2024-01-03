package com.github.shadowsocks.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.util.SortedList;
import android.util.TypedValue;
import com.github.shadowsocks.App;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URLConnection;

public class Utils {

    private static final Field fieldChildFragmentManager;

    static {
        try {
            fieldChildFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
            fieldChildFragmentManager.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isNumericAddress(String str) {
        return JniHelper.parseNumericAddress(str) != null;
    }

    public static InetAddress parseNumericAddress(String str) {
        byte[] addr = JniHelper.parseNumericAddress(str);
        if (addr == null) {
            return null;
        } else {
            try {
                return InetAddress.getByAddress(str, addr);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static int parsePort(String str, int defaultVal, int min) {
        int x;
        try {
            x = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            x = defaultVal;
        }
        if (x < min || x > 65535) {
            return defaultVal;
        } else {
            return x;
        }
    }

    public static BroadcastReceiver broadcastReceiver(final Callback callback) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                callback.onReceive(context, intent);
            }
        };
    }

    public static Thread thread(boolean start, boolean isDaemon, ClassLoader contextClassLoader,
                                String name, int priority, final Runnable block) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                block.run();
            }
        });
        thread.setUncaughtExceptionHandler(App.Companion.getApp()::track);
        if (start) {
            thread.start();
        }
        return thread;
    }

    public static long getResponseLength(URLConnection connection) {
        if (Build.VERSION.SDK_INT >= 24) {
            return connection.getContentLengthLong();
        } else {
            return connection.getContentLength();
        }
    }

    public static FragmentManager getChildFragManager(Fragment fragment) {
        try {
            return (FragmentManager) fieldChildFragmentManager.get(fragment);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setChildFragManager(Fragment fragment, FragmentManager fragmentManager) {
        try {
            fieldChildFragmentManager.set(fragment, fragmentManager);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static int resolveResourceId(Resources.Theme theme, @AttrRes int resId) {
        TypedValue typedValue = new TypedValue();
        if (!theme.resolveAttribute(resId, typedValue, true)) {
            throw new Resources.NotFoundException();
        }
        return typedValue.resourceId;
    }

    private static class SortedListIterable<T> implements Iterable<T> {
        private final SortedList<T> list;

        public SortedListIterable(SortedList<T> list) {
            this.list = list;
        }

        @Override
        public Iterator<T> iterator() {
            return new SortedListIterator<>(list);
        }
    }

    private static class SortedListIterator<T> implements Iterator<T> {
        private final SortedList<T> list;
        private int count;

        public SortedListIterator(SortedList<T> list) {
            this.list = list;
            this.count = 0;
        }

        @Override
        public boolean hasNext() {
            return count < list.size();
        }

        @Override
        public T next() {
            return list.get(count++);
        }
    }

    public static <T> Iterable<T> asIterable(SortedList<T> sortedList) {
        return new SortedListIterable<>(sortedList);
    }

    public interface Callback {
        void onReceive(Context context, Intent intent);
    }
}