package com.github.shadowsocks.bg;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.os.Build;
import android.util.Log;
import com.github.shadowsocks.App;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;
import org.xbill.DNS.*;

public class Dns {
    private static final String TAG = "Dns";

    public static String getDnsResolver(Context context) throws Exception {
        Collection<InetAddress> dnsResolvers = getDnsResolvers(context);
        if (dnsResolvers.isEmpty()) throw new Exception("Couldn't find an active DNS resolver");
        String dnsResolver = dnsResolvers.iterator().next().toString();
        if (dnsResolver.startsWith("/")) return dnsResolver.substring(1);
        return dnsResolver;
    }

    private static Collection<InetAddress> getDnsResolvers(Context context) throws Exception {
        List<InetAddress> addresses = new ArrayList<>();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Class<?> classLinkProperties = Class.forName("android.net.LinkProperties");
        java.lang.reflect.Method getActiveLinkPropertiesMethod = ConnectivityManager.class.getMethod("getActiveLinkProperties");
        Object linkProperties = getActiveLinkPropertiesMethod.invoke(connectivityManager);
        if (linkProperties != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Collection<?> dnses = (Collection<?>) classLinkProperties.getMethod("getDnses").invoke(linkProperties);
                for (Object dns : dnses) {
                    addresses.add((InetAddress) dns);
                }
            } else {
                addresses.addAll(((LinkProperties) linkProperties).getDnsServers());
            }
        }
        return addresses;
    }

    private static boolean hasIPv6Support() {
        try {
            boolean result = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(networkInterface -> Collections.list(networkInterface.getInetAddresses()).stream())
                    .filter(inetAddress -> inetAddress instanceof Inet6Address && !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress())
                    .count() > 0;
            if (result && BuildConfig.DEBUG) Log.d(TAG, "IPv6 address detected");
            return result;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to get interfaces' addresses.");
            App.Companion.getApp().track(ex);
            return false;
        }
    }

    private static String resolve(String host, int addrType) {
        try {
            Lookup lookup = new Lookup(host, addrType);
            SimpleResolver resolver = new SimpleResolver("208.67.220.220");
            resolver.setTCP(true);
            resolver.setPort(443);
            resolver.setTimeout(5);
            lookup.setResolver(resolver);
            List<?> records = lookup.run();
            if (records == null) return null;
            Collections.shuffle(records);
            for (Object r : records) {
                switch (addrType) {
                    case Type.A:
                        return ((ARecord) r).getAddress().getHostAddress();
                    case Type.AAAA:
                        return ((AAAARecord) r).getAddress().getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String resolve(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    public static String resolve(String host, boolean enableIPv6) {
        return (enableIPv6 && hasIPv6Support()) ? resolve(host, Type.AAAA) : null;
    }
}