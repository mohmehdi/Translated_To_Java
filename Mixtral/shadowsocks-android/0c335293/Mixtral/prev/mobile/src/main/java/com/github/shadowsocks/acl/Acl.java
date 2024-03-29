

package com.github.shadowsocks.acl;

import android.net.Uri;
import android.support.v7.util.SortedList;
import android.util.Log;

import com.github.shadowsocks.App;
import com.github.shadowsocks.utils.Subnet;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DatabaseTable
class Acl {
    public static final String TAG = "Acl";
    public static final String ALL = "all";
    public static final String BYPASS_LAN = "bypass-lan";
    public static final String BYPASS_CHN = "bypass-china";
    public static final String BYPASS_LAN_CHN = "bypass-lan-china";
    public static final String GFWLIST = "gfwlist";
    public static final String CHINALIST = "china-list";
    public static final String CUSTOM_RULES = "custom-rules";
    public static final String CUSTOM_RULES_FLATTENED = "custom-rules-flattened";

    public static final Pattern networkAclParser = Pattern.compile("^IMPORT_URL\\s*<(.+)>\\s*$");

    public static File getFile(String id) {
        return new File(App.app.getFilesDir(), id + ".acl");
    }

    public static Acl customRules {
        get {
            Acl acl = new Acl();
            try {
                acl.fromId(CUSTOM_RULES);
            } catch (FileNotFoundException e) {
                // swallow
            }
            acl.bypass = true;
            acl.bypassHostnames.clear();
            return acl;
        }
    }

    public static void save(String id, Acl acl) throws IOException {
        getFile(id).createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(getFile(id)));
        writer.write(acl.toString());
        writer.close();
    }

    private abstract static class BaseSorter<T> implements SortedList.Callback<T> {
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

        abstract int compareNonNull(T o1, T o2);

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
    }

    private static class DefaultSorter<T extends Comparable<T>> extends BaseSorter<T> {
        @Override
        int compareNonNull(T o1, T o2) {
            return o1.compareTo(o2);
        }
    }

    private static BaseSorter<String> StringSorter = new DefaultSorter<String>() {
        @Override
        int compareNonNull(String o1, String o2) {
            return o1.compareTo(o2);
        }
    };

    private static BaseSorter<Subnet> SubnetSorter = new DefaultSorter<Subnet>() {
        @Override
        int compareNonNull(Subnet o1, Subnet o2) {
            return o1.compareTo(o2);
        }
    };

    private static BaseSorter<URL> URLSorter = new BaseSorter<URL>() {
        private final List<Uri.Builder> ordering = new ArrayList<Uri.Builder>() {{
            add(new Uri.Builder().scheme("http").authority("host"));
            add(new Uri.Builder().scheme("http").authority("port"));
            add(new Uri.Builder().scheme("http").authority("file"));
            add(new Uri.Builder().scheme("http").authority("scheme"));
        }};

        @Override
        public int compareNonNull(URL o1, URL o2) {
            for (Uri.Builder builder : ordering) {
                int cmp = builder.build().getQueryParameter("value1", o1.toString()).compareTo(builder.build().getQueryParameter("value2", o2.toString()));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        }
    };

    @DatabaseField(generatedId = true)
    int id;

    SortedList<String> bypassHostnames = new SortedList<>(String.class, StringSorter);
    SortedList<String> proxyHostnames = new SortedList<>(String.class, StringSorter);
    SortedList<Subnet> subnets = new SortedList<>(Subnet.class, SubnetSorter);
    SortedList<URL> urls = new SortedList<>(URL.class, URLSorter);

    @DatabaseField
    boolean bypass;

    public Acl fromAcl(Acl other) {
        bypassHostnames.clear();
        for (String item : other.bypassHostnames) {
            bypassHostnames.add(item);
        }
        proxyHostnames.clear();
        for (String item : other.proxyHostnames) {
            proxyHostnames.add(item);
        }
        subnets.clear();
        for (Subnet item : other.subnets) {
            subnets.add(item);
        }
        urls.clear();
        for (URL item : other.urls) {
            urls.add(item);
        }
        bypass = other.bypass;
        return this;
    }

    public Acl fromReader(Reader reader, boolean defaultBypass) throws IOException {
        bypassHostnames.clear();
        proxyHostnames.clear();
        subnets.clear();
        urls.clear();
        bypass = defaultBypass;

        SortedList<Subnet> bypassSubnets = new SortedList<>(Subnet.class, SubnetSorter);
        SortedList<Subnet> proxySubnets = new SortedList<>(Subnet.class, SubnetSorter);

        SortedList<String> hostnames = defaultBypass ? proxyHostnames : bypassHostnames;
        SortedList<Subnet> subnetsList = defaultBypass ? proxySubnets : bypassSubnets;

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] blocks = line.split("#", 2);
                String url = getUrlFromLine(blocks[1]);
                if (url != null) {
                    urls.add(new URL(url));
                }
                String input = blocks[0].trim();
                switch (input) {
                    case "[outbound_block_list]":
                        hostnames = null;
                        subnetsList = null;
                        break;
                    case "[black_list]":
                    case "[bypass_list]":
                        hostnames = bypassHostnames;
                        subnetsList = bypassSubnets;
                        break;
                    case "[white_list]":
                    case "[proxy_list]":
                        hostnames = proxyHostnames;
                        subnetsList = proxySubnets;
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
                        if (subnetsList != null && !input.isEmpty()) {
                            try {
                                subnetsList.add(Subnet.fromString(input));
                            } catch (IllegalArgumentException e) {
                                hostnames.add(input);
                            }
                        }
                        break;
                }
            }
        }

        subnets.addAll(bypassSubnets);
        subnets.addAll(proxySubnets);
        return this;
    }

    public Acl fromId(String id) throws IOException {
        return fromReader(new FileReader(getFile(id)));
    }

    public Acl flatten(int depth) {
        if (depth > 0) {
            for (URL url : urls) {
                Acl child = new Acl().fromReader(url.openStream(), bypass).flatten(depth - 1);
                if (bypass != child.bypass) {
                    Log.w(TAG, "Imported network ACL has a conflicting mode set. " +
                            "This will probably not work as intended. URL: " + url);
                    child.subnets.clear();
                    child.bypass = bypass;
                }
                bypassHostnames.addAll(child.bypassHostnames);
                proxyHostnames.addAll(child.proxyHostnames);
                subnets.addAll(child.subnets);
            }
        }
        urls.clear();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(bypass ? "[bypass_all]\n" : "[proxy_all]\n");

        List<String> bypassList = new ArrayList<>();
        List<String> proxyList = new ArrayList<>();

        if (bypass) {
            bypassList.addAll(bypassHostnames);
            bypassList.addAll(subnets.stream().map(Subnet::toString).toList());
            bypassList.addAll(proxyHostnames);
        } else {
            proxyList.addAll(subnets.stream().map(Subnet::toString).toList());
            proxyList.addAll(proxyHostnames);
            bypassList.addAll(bypassHostnames);
        }

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

        for (URL url : urls) {
            result.append("#IMPORT_URL <")
                    .append(url.toString())
                    .append(">\n");
        }

        return result.toString();
    }


}