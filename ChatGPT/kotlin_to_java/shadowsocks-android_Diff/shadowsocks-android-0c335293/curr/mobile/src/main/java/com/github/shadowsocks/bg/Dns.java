package com.github.shadowsocks.bg;

import android.util.Log;
import com.github.shadowsocks.App;

import com.github.shadowsocks.BuildConfig;
import org.xbill.DNS.*;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

public class Dns {
    private static final String TAG = "Dns";

    private static boolean hasIPv6Support() {
        try {
            long count = NetworkInterface.getNetworkInterfaces().asIterator().flatMap(it -> {
                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it.getInetAddresses(), Spliterator.ORDERED), false);
            }).filter(it -> {
                return it instanceof Inet6Address && !((Inet6Address) it).isLoopbackAddress() && !((Inet6Address) it).isLinkLocalAddress();
            }).count();
            boolean result = count > 0;
            if (result && BuildConfig.DEBUG) {
                Log.d(TAG, "IPv6 address detected");
            }
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
            Record[] records = lookup.run();
            if (records == null) {
                return null;
            }
            List<Record> recordList = new ArrayList<>(Arrays.asList(records));
            Collections.shuffle(recordList);
            for (Record r : recordList) {
                switch (addrType) {
                    case Type.A:
                        return ((ARecord) r).getAddress().getHostAddress();
                    case Type.AAAA:
                        return ((AAAARecord) r).getAddress().getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
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
        if (resolve(host, Type.A) != null) {
            return resolve(host, Type.A);
        }
        return resolve(host);
    }
}