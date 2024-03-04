package com.github.shadowsocks.acl;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.util.Log;
import com.github.shadowsocks.App;

import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Subnet;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;

public class Acl {
    public static final String TAG = "Acl";
    public static final String ALL = "all";
    public static final String BYPASS_LAN = "bypass-lan";
    public static final String BYPASS_CHN = "bypass-china";
    public static final String BYPASS_LAN_CHN = "bypass-lan-china";
    public static final String GFWLIST = "gfwlist";
    public static final String CHINALIST = "china-list";
    public static final String CUSTOM_RULES = "custom-rules";

    private static final java.util.regex.Pattern networkAclParser = java.util.regex.Pattern.compile("^IMPORT_URL\\s*<(.+)>\\s*$");

    public static File getFile(String id, Context context) {
        return new File(context.getFilesDir(), id + ".acl");
    }

    public static Acl getCustomRules() {
        Acl acl = new Acl();
        String str = DataStore.publicStore.getString(CUSTOM_RULES);
        if (str != null) {
            acl.fromReader(str.reader());
            if (!acl.bypass) {
                acl.subnets.clear();
                acl.hostnames.clear();
                acl.bypass = true;
            }
        } else {
            acl.bypass = true;
        }
        return acl;
    }

    public static void setCustomRules(Acl value) {
        DataStore.publicStore.putString(CUSTOM_RULES, ((!value.bypass ||
                value.subnets.size() == 0 && value.hostnames.size() == 0) && value.urls.size() == 0) ?
                null : value.toString());
    }

    public static void save(String id, Acl acl) throws IOException {
        getFile(id, App.Companion.getApp().getDeviceContext()).writeText(acl.toString());
    }

    private abstract static class BaseSorter<T> extends SortedList.Callback<T> {
        @Override
        public void onInserted(int position, int count) { }

        @Override
        public boolean areContentsTheSame(T oldItem, T newItem) {
            return oldItem == newItem;
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) { }

        @Override
        public void onChanged(int position, int count) { }

        @Override
        public void onRemoved(int position, int count) { }

        @Override
        public boolean areItemsTheSame(T item1, T item2) {
            return item1 == item2;
        }

        @Override
        public int compare(T o1, T o2) {
            if (o1 == null) {
                return o2 == null ? 0 : 1;
            } else if (o2 == null) {
                return -1;
            } else {
                return compareNonNull(o1, o2);
            }
        }

        protected abstract int compareNonNull(T o1, T o2);
    }

    private static class DefaultSorter<T extends Comparable<T>> extends BaseSorter<T> {
        @Override
        protected int compareNonNull(T o1, T o2) {
            return o1.compareTo(o2);
        }
    }

    private static class StringSorter extends DefaultSorter<String> { }

    private static class SubnetSorter extends DefaultSorter<Subnet> { }

    private static class URLSorter extends BaseSorter<URL> {
        private final java.util.Comparator<URL> ordering = java.util.Comparator.comparing(URL::getHost)
                .thenComparing(URL::getPort)
                .thenComparing(URL::getFile)
                .thenComparing(URL::getProtocol);

        @Override
        protected int compareNonNull(URL o1, URL o2) {
            return ordering.compare(o1, o2);
        }
    }

    private final SortedList<String> hostnames = new SortedList<>(String.class, new StringSorter());
    private final SortedList<Subnet> subnets = new SortedList<>(Subnet.class, new SubnetSorter());
    private final SortedList<URL> urls = new SortedList<>(URL.class, new URLSorter());
    private boolean bypass = false;

    public Acl clear() {
        hostnames.clear();
        subnets.clear();
        urls.clear();
        return this;
    }

    public Acl fromAcl(Acl other) {
        clear();
        for (String item : other.hostnames) {
            hostnames.add(item);
        }
        for (Subnet item : other.subnets) {
            subnets.add(item);
        }
        for (URL item : other.urls) {
            urls.add(item);
        }
        bypass = other.bypass;
        return this;
    }


    public Acl fromReader(Reader reader, boolean defaultBypass) throws IOException {
        clear();
        bypass = defaultBypass;
        SortedList<String> proxyHostnames = null;
        SortedList<String> bypassHostnames = null;
        SortedList<Subnet> bypassSubnets = null;
        SortedList<Subnet> proxySubnets = null;
        SortedList<String> hostnames = defaultBypass ? proxyHostnames : bypassHostnames;
        SortedList<Subnet> subnets = defaultBypass ? proxySubnets : bypassSubnets;
        try (java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] blocks = line.split("#", 2);
                String url = networkAclParser.matcher(blocks.length > 1 ? blocks[1] : "").matches() ?
                        networkAclParser.matcher(blocks[1]).group(1) : null;
                if (url != null) {
                    urls.add(new URL(url));
                }
                String input = blocks[0].trim();
                switch (input) {
                    case "[outbound_block_list]":
                        hostnames = null;
                        subnets = null;
                        break;
                    case "[black_list]":
                    case "[bypass_list]":
                        hostnames = bypassHostnames;
                        subnets = bypassSubnets;
                        break;
                    case "[white_list]":
                    case "[proxy_list]":
                        hostnames = proxyHostnames;
                        subnets = proxySubnets;
                        break;
                    case "[reject_all]":
                    case "[bypass_all]":
                        bypass = true;
                        break;
                    case "[accept_all]":
                    case "[proxy_all]":
                        bypass = false;
                        break;
                    default:
                        if (subnets != null && !input.isEmpty()) {
                            Subnet subnet = Subnet.fromString(input);
                            if (subnet == null) {
                                hostnames.add(input);
                            } else {
                                subnets.add(subnet);
                            }
                        }
                        break;
                }
            }
        }
        for (String item : (bypass ? proxyHostnames : bypassHostnames)) {
            this.hostnames.add(item);
        }
        for (Subnet item : (bypass ? proxySubnets : bypassSubnets)) {
            this.subnets.add(item);
        }
        return this;
    }

    public Acl fromId(String id) {
        try {
            return fromReader(new java.io.FileReader(getFile(id, App.Companion.getApp().getDeviceContext())));
        } catch (IOException e) {
            return this;
        }
    }

    public Acl flatten(int depth) {
        if (depth > 0) {
            for (URL url : urls) {
                Acl child = new Acl();
                try {
                    child.fromReader(url.openStream().bufferedReader(), bypass).flatten(depth - 1);
                } catch (IOException e) {
                    continue;
                }
                if (bypass != child.bypass) {
                    Log.w(TAG, "Imported network ACL has a conflicting mode set. " +
                            "This will probably not work as intended. URL: " + url);
                    child.hostnames.clear();
                    child.subnets.clear();
                    child.bypass = bypass;
                }
                for (String item : child.hostnames) {
                    hostnames.add(item);
                }
                for (Subnet item : child.subnets) {
                    subnets.add(item);
                }
            }
        }
        urls.clear();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(bypass ? "[bypass_all]\n[proxy_list]\n" : "[proxy_all]\n[bypass_list]\n");
        result.append(String.join("\n", subnets));
        result.append('\n');
        result.append(String.join("\n", hostnames));
        result.append('\n');
        result.append(urls.stream().map(url -> "#IMPORT_URL <" + url + ">\n").reduce("", String::concat));
        return result.toString();
    }
}