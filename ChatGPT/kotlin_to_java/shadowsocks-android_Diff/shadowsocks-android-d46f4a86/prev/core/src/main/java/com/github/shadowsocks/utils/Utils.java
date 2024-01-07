package com.github.shadowsocks.utils;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.preference.Preference;

import com.crashlytics.android.Crashlytics;

import java.net.InetAddress;
import java.net.URLConnection;
import java.util.List;

public class Utils {

    private static final Method parseNumericAddress;

    static {
        try {
            parseNumericAddress = InetAddress.class.getDeclaredMethod("parseNumericAddress", String.class);
            parseNumericAddress.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static InetAddress parseNumericAddress(String address) {
        try {
            byte[] bytes = Os.inet_pton(OsConstants.AF_INET, address);
            if (bytes == null) {
                bytes = Os.inet_pton(OsConstants.AF_INET6, address);
                if (bytes != null) {
                    return (InetAddress) parseNumericAddress.invoke(null, address);
                }
            } else {
                return InetAddress.getByAddress(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int parsePort(String str, int defaultPort, int min) {
        int value = defaultPort;
        try {
            value = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            // Do nothing
        }
        if (value < min || value > 65535) {
            return defaultPort;
        } else {
            return value;
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

    public static long getResponseLength(URLConnection connection) {
        if (Build.VERSION.SDK_INT >= 24) {
            return connection.getContentLengthLong();
        } else {
            return connection.getContentLength();
        }
    }

    public static Bitmap openBitmap(ContentResolver resolver, Uri uri) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                return BitmapFactory.decodeStream(resolver.openInputStream(uri));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static List<PackageInfo> getSignaturesCompat(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= 28) {
            return packageInfo.signingInfo.getApkContentsSigners();
        } else {
            return packageInfo.signatures;
        }
    }

    public static int resolveResourceId(Resources.Theme theme, @AttrRes int resId) {
        TypedValue typedValue = new TypedValue();
        if (!theme.resolveAttribute(resId, typedValue, true)) {
            throw new Resources.NotFoundException();
        }
        return typedValue.resourceId;
    }

    public static List<Uri> getDatas(Intent intent) {
        List<Uri> datas = new ArrayList<>();
        Uri data = intent.getData();
        if (data != null) {
            datas.add(data);
        }
        if (intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                Uri uri = intent.getClipData().getItemAt(i).getUri();
                if (uri != null) {
                    datas.add(uri);
                }
            }
        }
        return datas;
    }

    public static void printLog(Throwable t) {
        Crashlytics.logException(t);
        t.printStackTrace();
    }

    public static void removePreference(Preference preference) {
        preference.getParent().removePreference(preference);
    }

    public interface Callback {
        void onReceive(Context context, Intent intent);
    }
}