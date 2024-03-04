package com.github.shadowsocks.acl;

import android.support.v7.util.SortedList;
import android.util.Log;
import com.github.shadowsocks.App;

import com.github.shadowsocks.utils.Subnet;
import com.github.shadowsocks.utils.asIterable;
import com.j256.ormlite.field.DatabaseField;
import java.io.File;
import java.io.FileNotFoundException;
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
    public static final String CUSTOM_RULES_FLATTENED = "custom-rules-flattened";

    private static final java.util.regex.Pattern networkAclParser = java.util.regex.Pattern.compile("^IMPORT_URL\\s*<(.+)>\\s*$");

    public static File getFile(String id) {
        return new File(App.Companion.getApp().getFilesDir(), id + ".acl");
    }

    public static Acl customRules() {
        Acl acl = new Acl();
        try {
            acl.fromId(CUSTOM_RULES);
        } catch (FileNotFoundException e) { }
        acl.bypass = true;
        acl.bypassHostnames.clear();
        return acl;
    }

    public static void save(String id, Acl acl) throws java.io.IOException {
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(getFile(id)))) {
            writer.write(acl.toString());
        }
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

    @DatabaseField(generatedId = true)
    public int id = 0;
    public final SortedList<String> bypassHostnames = new SortedList<>(String.class, new StringSorter());
    public final SortedList<String> proxyHostnames = new SortedList<>(String.class, new StringSorter());
    public final SortedList<Subnet> subnets = new SortedList<>(Subnet.class, new SubnetSorter());
    public final SortedList<URL> urls = new SortedList<>(URL.class, new URLSorter());

    @DatabaseField
    public boolean bypass = false;

    public Acl fromAcl(Acl other) {
        bypassHostnames.clear();
        for (String item : other.bypassHostnames.asIterable()) {
            bypassHostnames.add(item);
        }
        proxyHostnames.clear();
        for (String item : other.proxyHostnames.asIterable()) {
            proxyHostnames.add(item);
        }
        subnets.clear();
        for (Subnet item : other.subnets.asIterable()) {
            subnets.add(item);
        }
        urls.clear();
        for (URL item : other.urls.asIterable()) {
            urls.add(item);
        }
        bypass = other.bypass;
        return this;
    }

    public Acl fromReader(Reader reader, boolean defaultBypass) throws java.io.IOException {
        bypassHostnames.clear();
        proxyHostnames.clear();
        subnets.clear();
        urls.clear();
        bypass = defaultBypass;
        SortedList<Subnet> bypassSubnets = null;
        SortedList<Subnet> proxySubnets = null;
        SortedList<String> hostnames = defaultBypass ? proxyHostnames : bypassHostnames;
        SortedList<Subnet> subnets = defaultBypass ? proxySubnets : bypassSubnets;
        try (java.io.BufferedReader bufferedReader = new java.io.BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] blocks = line.split("#", 2);
                String url = networkAclParser.matcher(blocks.length > 1 ? blocks[1] : "").matches() ? blocks[1] : null;
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
                            try {
                                subnets.add(Subnet.fromString(input));
                            } catch (IllegalArgumentException e) {
                                hostnames.add(input);
                            }
                        }
                        break;
                }
            }
        }
        if (bypass) {
            for (Subnet item : proxySubnets.asIterable()) {
                this.subnets.add(item);
            }
        } else {
            for (Subnet item : bypassSubnets.asIterable()) {
                this.subnets.add(item);
            }
        }
        return this;
    }

    public Acl fromId(String id) throws FileNotFoundException, java.io.IOException {
        return fromReader(new java.io.FileReader(getFile(id)));
    }

    public Acl flatten(int depth) throws java.io.IOException {
        if (depth > 0) {
            for (URL url : urls.asIterable()) {
                Acl child = new Acl().fromReader(url.openStream().bufferedReader(), bypass).flatten(depth - 1);
                if (bypass != child.bypass) {
                    Log.w(TAG, "Imported network ACL has a conflicting mode set. " +
                            "This will probably not work as intended. URL: " + url);
                    child.subnets.clear();
                    child.bypass = bypass;
                }
                for (String item : child.bypassHostnames.asIterable()) {
                    bypassHostnames.add(item);
                }
                for (String item : child.proxyHostnames.asIterable()) {
                    proxyHostnames.add(item);
                }
                for (Subnet item : child.subnets.asIterable()) {
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
        result.append(bypass ? "[bypass_all]\n" : "[proxy_all]\n");
        java.util.List<String> bypassList = bypass ? bypassHostnames.asIterable().toList() :
                java.util.stream.Stream.concat(subnets.asIterable().stream().map(Subnet::toString),
                        proxyHostnames.asIterable().stream()).toList();
        java.util.List<String> proxyList = bypass ? java.util.stream.Stream.concat(subnets.asIterable().stream().map(Subnet::toString),
                proxyHostnames.asIterable().stream()) : bypassHostnames.asIterable().toList();
        if (!bypassList.isEmpty()) {
            result.append("[bypass_list]\n");
            result.append(String.join("\n", bypassList));
            result.append('\n');
        }
        if (!proxyList.isEmpty()) {
            result.append("[proxy_list]\n");
            result.append(String.join("\n", proxyList));
            result.append('\n');
        }
        result.append(urls.asIterable().stream().map(url -> "#IMPORT_URL <" + url + ">\n").collect(java.util.stream.Collectors.joining()));
        return result.toString();
    }
}