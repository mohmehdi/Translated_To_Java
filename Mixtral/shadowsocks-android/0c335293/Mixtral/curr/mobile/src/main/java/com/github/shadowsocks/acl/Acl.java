package com.github.shadowsocks.acl;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.util.Log;
import com.github.shadowsocks.App;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Subnet;
import com.github.shadowsocks.utils.asIterable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;

public class Acl {
    private static final String TAG = "Acl";
    private static final String ALL = "all";
    private static final String BYPASS_LAN = "bypass-lan";
    private static final String BYPASS_CHN = "bypass-china";
    private static final String BYPASS_LAN_CHN = "bypass-lan-china";
    private static final String GFWLIST = "gfwlist";
    private static final String CHINALIST = "china-list";
    private static final String CUSTOM_RULES = "custom-rules";

    private static final Pattern networkAclParser = Pattern.compile("^IMPORT_URL\s*<(.+)>\s*$");

    public static File getFile(String id, Context context) {
        return new File(context.getFilesDir(), id + ".acl");
    }

    private static Acl customRules;

    public static Acl getCustomRules() {
        Acl acl = new Acl();
        String str = DataStore.publicStore.getString(CUSTOM_RULES);
        if (str != null) {
        acl.fromReader(new StringReader(str));
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
        DataStore.publicStore.putString(CUSTOM_RULES, (value.bypass || value.subnets.size() == 0 && value.hostnames.size() == 0 && value.urls.size() == 0) ? null : value.toString());
    }

    public static void save(String id, Acl acl) throws IOException {
        getFile(id).writeText(acl.toString());
    }
    public static class BaseSorter<T> implements SortedList.Callback<T> {
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

        public abstract int compareNonNull(T o1, T o2);
    }

    public static class DefaultSorter<T extends Comparable<T>> extends BaseSorter<T> {
        @Override
        public int compareNonNull(T o1, T o2) {
            return o1.compareTo(o2);
        }
    }

    public static class StringSorter extends DefaultSorter<String> { }
    public static class SubnetSorter extends DefaultSorter<Subnet> { }
    public static class URLSorter extends BaseSorter<URL> {
        private final java.util.Comparator<URL> ordering =
                Comparator.comparing(url -> url.getHost())
                        .thenComparing(url -> url.getPort())
                        .thenComparing(url -> url.getFile())
                        .thenComparing(url -> url.getProtocol());

        @Override
        public int compareNonNull(URL o1, URL o2) {
            return ordering.compare(o1, o2);
        }
    }

    public SortedList<String> hostnames = new SortedList<>(String.class, new StringSorter());
    public SortedList<Subnet> subnets = new SortedList<>(Subnet.class, new SubnetSorter());
    public SortedList<URL> urls = new SortedList<>(URL.class, new URLSorter());
    public boolean bypass = false;

    public Acl clear() {
        hostnames.clear();
        subnets.clear();
        urls.clear();
        return this;
    }

    public Acl fromAcl(Acl other) {
        clear();
        for (String item : other.hostnames.asIterable()) hostnames.add(item);
        for (Subnet item : other.subnets.asIterable()) subnets.add(item);
        for (URL item : other.urls.asIterable()) urls.add(item);
        bypass = other.bypass;
        return this;
    }

    public Acl fromReader(Reader reader, boolean defaultBypass) throws IOException {
        clear();
        bypass = defaultBypass;
        SortedList<String> proxyHostnames = new SortedList<>(String.class, new StringSorter());
        SortedList<String> bypassHostnames = new SortedList<>(String.class, new StringSorter());
        SortedList<Subnet> bypassSubnets = new SortedList<>(Subnet.class, new SubnetSorter());
        SortedList<Subnet> proxySubnets = new SortedList<>(Subnet.class, new SubnetSorter());
        SortedList<String> hostnames = defaultBypass ? proxyHostnames : bypassHostnames;
        SortedList<Subnet> subnets = defaultBypass ? proxySubnets : bypassSubnets;
        java.util.function.Function<String, URL> urlFunction = url -> new URL(url);
        java.util.List<String> blocks;
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder blockBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                blocks = java.util.Collections.singletonList(line.split("#", 2)[0].trim());
                String url = networkAclParser.matchEntire(blocks.get(1)).map(matchResult -> matchResult.group(1))
                        .orElse(null);
                if (url != null) urls.add(urlFunction.apply(url));
                String input = blocks.get(0);
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
                            if (subnet == null) hostnames.add(input);
                            else subnets.add(subnet);
                        }
                }
            }
        }
        if (hostnames != null) {
            for (String item : hostnames.asIterable()) this.hostnames.add(item);
            for (Subnet item : subnets.asIterable()) this.subnets.add(item);
        }
        return this;
    }

    public Acl fromId(String id) {
        try {
            return fromReader(new File(App.app.getFilesDir(), id + ".acl").getBufferedReader());
        } catch (IOException e) {
            return this;
        }
    }

    public Acl flatten(int depth) {
        if (depth > 0) {
            for (URL url : urls.asIterable()) {
                Acl child = new Acl();
                try {
                    child.fromReader(url.openStream().getBufferedReader(), bypass).flatten(depth - 1);
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
                for (String item : child.hostnames.asIterable()) this.hostnames.add(item);
                for (Subnet item : child.subnets.asIterable()) this.subnets.add(item);
            }
        }
        urls.clear();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(bypass ? "[bypass_all]\n[proxy_list]\n" : "[proxy_all]\n[bypass_list]\n");
        result.append(subnets.asIterable().stream().collect(Collectors.joining("\n")));
        result.append('\n');
        result.append(hostnames.asIterable().stream().collect(Collectors.joining("\n")));
        result.append('\n');
        result.append(urls.asIterable().stream().map(url -> "#IMPORT_URL <" + url + ">\n")
                .collect(Collectors.joining()));
        return result.toString();
    }
}