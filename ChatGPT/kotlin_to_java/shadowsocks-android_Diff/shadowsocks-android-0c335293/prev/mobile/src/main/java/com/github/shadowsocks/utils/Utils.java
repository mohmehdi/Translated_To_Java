package com.github.shadowsocks.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.util.SortedList;
import android.util.TypedValue;
import com.github.shadowsocks.App;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URLConnection;

public class Utils {
    private static final Method isNumericMethod;
    private static final Method parseNumericAddressMethod;
    private static final Field fieldChildFragmentManager;

    static {
        try {
            isNumericMethod = InetAddress.class.getMethod("isNumeric", String.class);
            parseNumericAddressMethod = InetAddress.class.getMethod("parseNumericAddress", String.class);
            fieldChildFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
            fieldChildFragmentManager.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isNumericAddress(String address) {
        try {
            return (boolean) isNumericMethod.invoke(null, address);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static InetAddress parseNumericAddress(String address) {
        try {
            return (InetAddress) parseNumericAddressMethod.invoke(null, address);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static int parsePort(String str, int defaultPort, int min) {
        int x = (str != null) ? Integer.parseInt(str) : defaultPort;
        return (x < min || x > 65535) ? defaultPort : x;
    }

    public static BroadcastReceiver broadcastReceiver(BroadcastReceiverCallback callback) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                callback.onReceive(context, intent);
            }
        };
    }

    public static Thread thread(boolean start, boolean isDaemon, ClassLoader contextClassLoader,
                                String name, int priority, Runnable block) {
        Thread thread = kotlin.concurrent.ThreadsKt.thread(start, isDaemon, contextClassLoader, name, priority, block);
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

    public static float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, App.Companion.getApp().getResources().getDisplayMetrics());
    }

    public static FragmentManager getChildFragmentManager(Fragment fragment) {
        try {
            return (FragmentManager) fieldChildFragmentManager.get(fragment);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setChildFragmentManager(Fragment fragment, FragmentManager fragmentManager) {
        try {
            fieldChildFragmentManager.set(fragment, fragmentManager);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
}