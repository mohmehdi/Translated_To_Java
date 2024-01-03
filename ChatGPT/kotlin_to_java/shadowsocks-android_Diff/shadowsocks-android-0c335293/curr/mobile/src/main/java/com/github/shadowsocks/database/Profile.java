package com.github.shadowsocks.database;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parsePort;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Profile implements Serializable {
    private static final String TAG = "ShadowParser";
    private static final Pattern pattern = Pattern.compile("(?i)ss:");
    private static final Pattern userInfoPattern = Pattern.compile("^(.+?):(.*)$");
    private static final Pattern legacyPattern = Pattern.compile("^(.+?):(.*)@(.+?):(\\d+?)$");

    public static List<Profile> findAll(CharSequence data) {
        Matcher matcher = pattern.matcher(data != null ? data : "");
        List<Profile> profiles = new ArrayList<>();
        while (matcher.find()) {
            Uri uri = Uri.parse(matcher.group());
            if (uri.getUserInfo() == null) {
                Matcher legacyMatcher = legacyPattern.matcher(new String(Base64.decode(uri.getHost(), Base64.NO_PADDING)));
                if (legacyMatcher.matches()) {
                    Profile profile = new Profile();
                    profile.method = legacyMatcher.group(1).toLowerCase();
                    profile.password = legacyMatcher.group(2);
                    profile.host = legacyMatcher.group(3);
                    profile.remotePort = Integer.parseInt(legacyMatcher.group(4));
                    profile.plugin = uri.getQueryParameter(Key.plugin);
                    profile.name = uri.getFragment();
                    profiles.add(profile);
                } else {
                    Log.e(TAG, "Unrecognized URI: " + matcher.group());
                }
            } else {
                Matcher userInfoMatcher = userInfoPattern.matcher(new String(Base64.decode(uri.getUserInfo(),
                        Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE)));
                if (userInfoMatcher.matches()) {
                    Profile profile = new Profile();
                    profile.method = userInfoMatcher.group(1);
                    profile.password = userInfoMatcher.group(2);

                    URI javaURI = URI.create(matcher.group());
                    profile.host = javaURI.getHost();
                    if (profile.host.charAt(0) == '[' && profile.host.charAt(profile.host.length() - 1) == ']')
                        profile.host = profile.host.substring(1, profile.host.length() - 1);
                    profile.remotePort = javaURI.getPort();
                    profile.plugin = uri.getQueryParameter(Key.plugin);
                    profile.name = uri.getFragment() != null ? uri.getFragment() : "";
                    profiles.add(profile);
                } else {
                    Log.e(TAG, "Unknown user info: " + matcher.group());
                }
            }
        }
        return profiles;
    }

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField
    private String name = "";

    @DatabaseField
    private String host = "198.199.101.152";

    @DatabaseField
    private int remotePort = 8388;

    @DatabaseField
    private String password = "u1rRWTssNv0p";

    @DatabaseField
    private String method = "aes-256-cfb";

    @DatabaseField
    private String route = "all";

    @DatabaseField
    private String remoteDns = "8.8.8.8";

    @DatabaseField
    private boolean proxyApps = false;

    @DatabaseField
    private boolean bypass = false;

    @DatabaseField
    private boolean udpdns = false;

    @DatabaseField
    private boolean ipv6 = true;

    @DatabaseField(dataType = DataType.LONG_STRING)
    private String individual = "";

    @DatabaseField
    private long tx = 0;

    @DatabaseField
    private long rx = 0;

    @DatabaseField
    private Date date = new Date();

    @DatabaseField
    private long userOrder = 0;

    @DatabaseField
    private String plugin = null;

    public String getFormattedAddress() {
        return (host.contains(":")) ? String.format(Locale.ENGLISH, "[%s]:%d", host, remotePort) : String.format(Locale.ENGLISH, "%s:%d", host, remotePort);
    }

    public String getFormattedName() {
        return name.isEmpty() ? getFormattedAddress() : name;
    }

    public Uri toUri() {
        Uri.Builder builder = new Uri.Builder()
                .scheme("ss")
                .encodedAuthority(String.format(Locale.ENGLISH,
                        "%s@%s:%d",
                        Base64.encodeToString(String.format(Locale.ENGLISH, "%s:%s", method, password).getBytes(),
                                Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE),
                        host.contains(":") ? "[" + host + "]" : host,
                        remotePort));
        PluginConfiguration configuration = new PluginConfiguration(plugin != null ? plugin : "");
        if (!configuration.getSelected().isEmpty())
            builder.appendQueryParameter(Key.plugin, configuration.getSelectedOptions().toString(false));
        if (!name.isEmpty()) builder.fragment(name);
        return builder.build();
    }

    @Override
    public String toString() {
        return toUri().toString();
    }

    public void serialize() {
        DataStore.privateStore.putString(Key.name, name);
        DataStore.privateStore.putString(Key.host, host);
        DataStore.privateStore.putString(Key.remotePort, String.valueOf(remotePort));
        DataStore.privateStore.putString(Key.password, password);
        DataStore.privateStore.putString(Key.route, route);
        DataStore.privateStore.putString(Key.remoteDns, remoteDns);
        DataStore.privateStore.putString(Key.method, method);
        DataStore.proxyApps = proxyApps;
        DataStore.bypass = bypass;
        DataStore.privateStore.putBoolean(Key.udpdns, udpdns);
        DataStore.privateStore.putBoolean(Key.ipv6, ipv6);
        DataStore.individual = individual;
        DataStore.plugin = plugin != null ? plugin : "";
        DataStore.privateStore.remove(Key.dirty);
    }

    public void deserialize() {
        name = DataStore.privateStore.getString(Key.name, "");
        host = DataStore.privateStore.getString(Key.host, "");
        remotePort = parsePort(DataStore.privateStore.getString(Key.remotePort), 8388, 1);
        password = DataStore.privateStore.getString(Key.password, "");
        method = DataStore.privateStore.getString(Key.method, "");
        route = DataStore.privateStore.getString(Key.route, "");
        remoteDns = DataStore.privateStore.getString(Key.remoteDns, "");
        proxyApps = DataStore.proxyApps;
        bypass = DataStore.bypass;
        udpdns = DataStore.privateStore.getBoolean(Key.udpdns, false);
        ipv6 = DataStore.privateStore.getBoolean(Key.ipv6, false);
        individual = DataStore.individual;
        plugin = DataStore.plugin;
    }
}