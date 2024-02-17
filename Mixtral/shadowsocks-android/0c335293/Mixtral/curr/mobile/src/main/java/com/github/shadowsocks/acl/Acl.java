

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Acl {
     public static final String TAG = "Acl";
    public static final String ALL = "all";
    public static final String BYPASS_LAN = "bypass-lan";
    public static final String BYPASS_CHN = "bypass-china";
    public static final String BYPASS_LAN_CHN = "bypass-lan-china";
    public static final String GFWLIST = "gfwlist";
    public static final String CHINALIST = "china-list";
    public static final String CUSTOM_RULES = "custom-rules";

    public static Pattern networkAclParser = Pattern.compile("^IMPORT_URL\\s*<(.+)>\\s*$");

    public static File getFile(String id, Context context) {
        return new File(context.getFilesDir(), id + ".acl");
    }

    private static Acl customRules;

    public static void save(String id, Acl acl) {
        try {
            getFile(id).writeText(acl.toString());
        } catch (IOException e) {
            // Handle exception
        }
    }

    private static class BaseSorter<T> extends SortedList.Callback<T> {
        @Override
        public void onInserted(int position, int count) {
        }

        @Override
        public boolean areContentsTheSame(T oldItem, T newItem) {
            return oldItem == newItem;
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
        }

        @Override
        public void onChanged(int position, int count) {
        }

        @Override
        public void onRemoved(int position, int count) {
        }

        @Override
        public boolean areItemsTheSame(T item1, T item2) {
            return item1 == item2;
        }

        public abstract int compareNonNull(T o1, T o2);

        @Override
        public int compare(T o1, T o2) {
            if (o1 == null) {
                return (o2 == null) ? 0 : 1;
            } else if (o2 == null) {
                return -1;
            } else {
                return compareNonNull(o1, o2);
            }
        }
    }

    private static class DefaultSorter<T extends Comparable<T>> extends BaseSorter<T> {
        @Override
        public int compareNonNull(T o1, T o2) {
            return o1.compareTo(o2);
        }
    }


    private static final BaseSorter<URL> URLSorter = new BaseSorter<URL>() {
        private final SortedList.Callback<URL> ordering = SortedList.callback(
                (o1, o2) -> o1.getHost().compareTo(o2.getHost()),
                (o1, o2) -> Integer.compare(o1.getPort(), o2.getPort()),
                (o1, o2) -> o1.getFile().compareTo(o2.getFile()),
                (o1, o2) -> o1.getProtocol().compareTo(o2.getProtocol())
        );

        @Override
        public int compareNonNull(URL o1, URL o2) {
            return ordering.compare(o1, o2);
        }
    };

    public final SortedList<String> hostnames = new SortedList<>(String.class, StringSorter);
    public final SortedList<Subnet> subnets = new SortedList<>(Subnet.class, SubnetSorter);
    public final SortedList<URL> urls = new SortedList<>(URL.class, URLSorter);
    public boolean bypass;

    public Acl clear() {
        hostnames.clear();
        subnets.clear();
        urls.clear();
        return this;
    }

    public Acl fromAcl(Acl other) {
        clear();
        for (String item : other.hostnames.asIterable()) {
            hostnames.add(item);
        }
        for (Subnet item : other.subnets.asIterable()) {
            subnets.add(item);
        }
        for (URL item : other.urls.asIterable()) {
            urls.add(item);
        }
        bypass = other.bypass;
        return this;
    }

    public Acl fromReader(Reader reader, boolean defaultBypass) throws IOException {
        clear();
        bypass = defaultBypass;
        SortedList<String> proxyHostnames = new SortedList<>(String.class, StringSorter);
        SortedList<String> bypassHostnames = new SortedList<>(String.class, StringSorter);
        SortedList<Subnet> proxySubnets = new SortedList<>(Subnet.class, SubnetSorter);
        SortedList<Subnet> bypassSubnets = new SortedList<>(Subnet.class, SubnetSorter);
        SortedList<String> hostnames = (defaultBypass) ? proxyHostnames : bypassHostnames;
        SortedList<Subnet> subnets = (defaultBypass) ? proxySubnets : bypassSubnets;
        Iterable<String> lines = reader.lines().toList();
        for (String line : lines) {
            String[] blocks = line.split("#", 2);
            String url = getUrlFromNetworkAclParser(blocks[1]);
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
            }
        }
        for (String item : (bypass ? proxyHostnames : bypassHostnames).asIterable()) {
            hostnames.add(item);
        }
        for (Subnet item : (bypass ? proxySubnets : bypassSubnets).asIterable()) {
            subnets.add(item);
        }
        return this;
    }

    public Acl fromId(String id) {
        try {
            return fromReader(new File(getFile(id, App.app.deviceContext).getPath()).bufferedReader());
        } catch (IOException e) {
            return this;
        }
    }

    public Acl flatten(int depth) {
        if (depth > 0) {
            for (URL url : urls.asIterable()) {
                Acl child = new Acl();
                try {
                    child.fromReader(url.openStream().bufferedReader(), bypass);
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
                for (String item : child.hostnames.asIterable()) {
                    hostnames.add(item);
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
        result.append((bypass) ? "[bypass_all]\n[proxy_list]\n" : "[proxy_all]\n[bypass_list]\n");
        result.append(subnets.asIterable().toString());
        result.append('\n');
        result.append(hostnames.asIterable().toString());
        result.append('\n');
        result.append(urls.asIterable().toString());
        return result.toString();
    }

}