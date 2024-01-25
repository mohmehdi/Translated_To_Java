package com.github.shadowsocks.bg;

import android.content.Context;
import android.net.InetAddress;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;

import com.github.shadowsocks.Core;
import com.github.shadowsocks.Preference;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.AclSyncer;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.FileUtils;
import com.github.shadowsocks.utils.NumericUtils;
import com.github.shadowsocks.utils.SSOClient;
import com.github.shadowsocks.utils.SSOServer;
import com.github.shadowsocks.utils.StringUtils;
import com.github.shadowsocks.utils.URLUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProxyInstance implements AutoCloseable {
    private final Profile profile;
    private final String route;
    private TrafficMonitor trafficMonitor;
    private PluginConfiguration plugin;
    private String pluginPath;
    private final Context context;

    public ProxyInstance(Profile profile, String route, Context context) {
        this.profile = profile;
        this.route = route;
        this.context = context;
    }

    @Override
    public void close() throws IOException {
        if (trafficMonitor != null) {
            trafficMonitor.close();
            Profile currentProfile = ProfileManager.getProfile(profile.id);
            if (currentProfile != null) {
                currentProfile.tx += trafficMonitor.current.txTotal;
                currentProfile.rx += trafficMonitor.current.rxTotal;
                ProfileManager.updateProfile(currentProfile);
            } else {
                DirectBoot.getDeviceProfile().ifPresent(deviceProfile -> {
                    List<Profile> profiles = deviceProfile.getProfiles();
                    Profile profileToUpdate = profiles.stream()
                            .filter(p -> p.id.equals(profile.id))
                            .findFirst()
                            .orElse(null);
                    if (profileToUpdate != null) {
                        profileToUpdate.tx += trafficMonitor.current.txTotal;
                        profileToUpdate.rx += trafficMonitor.current.rxTotal;
                        profileToUpdate.dirty = true;
                        DirectBoot.update(deviceProfile);
                        DirectBoot.listenForUnlock();
                    }
                });
            }
        }
        trafficMonitor = null;
        if (configFile != null) {
            configFile.delete();
        }
        configFile = null;
    }

    public void start(BaseService.Interface service, File stat, File configFile, String extraFlag) throws IOException, JSONException {
        trafficMonitor = new TrafficMonitor(stat);

        this.configFile = configFile;
        JSONObject config = profile.toJson();
        if (pluginPath != null) {
            config.put("plugin", pluginPath);
            config.put("plugin_opts", plugin.toString());
        }
        FileUtils.writeFile(configFile, config.toString());

        List<String> cmd = service.buildAdditionalArguments(Arrays.asList(
                new File(context.getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                "-b", DataStore.listenAddress,
                "-l", Integer.toString(DataStore.portProxy),
                "-t", "600",
                "-S", stat.getAbsolutePath(),
                "-c", configFile.getAbsolutePath()));
        if (extraFlag != null) {
            cmd.add(extraFlag);
        }

        if (!route.equals(Acl.ALL)) {
            cmd.add("--acl");
            cmd.add(Acl.getFile(route).getAbsolutePath());
        }

        if (profile.udpdns) {
            cmd.add("-D");
        }

        if (DataStore.tcpFastOpen) {
            cmd.add("--fast-open");
        }

        Process process = service.data.processes.start(cmd);
    }

    public void scheduleUpdate() {
        if (!route.equals(Acl.ALL) && !route.equals(Acl.CUSTOM_RULES)) {
            AclSyncer.schedule(route);
        }
    }

    public void init(Resolver resolver) throws IOException, JSONException, NoSuchAlgorithmException {
        if (profile.host.equals("198.199.101.152")) {
            MessageDigest mdg = MessageDigest.getInstance("SHA-1");
            mdg.update(Core.packageInfo.signaturesCompat.get(0).toByteArray());
            String sig = Base64.encodeToString(mdg.digest(), Base64.DEFAULT);

            HttpURLConnection conn = (HttpURLConnection) RemoteConfig.proxyUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write("sig=" + sig);
            writer.flush();
            writer.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String proxies = reader.readLine();
            reader.close();
            conn.disconnect();

            List<String> proxiesList = Arrays.asList(proxies.split("\\|"));
            Collections.shuffle(proxiesList);
            String[] proxy = proxiesList.get(0).split(":");
            profile.host = proxy[0].trim();
            profile.remotePort = Integer.parseInt(proxy[1].trim());
            profile.password = proxy[2].trim();
            profile.method = proxy[3].trim();
        }

        if (route.equals(Acl.CUSTOM_RULES)) {
            Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
        }

        if (!NumericUtils.isValidIpAddress(profile.host)) {
            profile.host = Objects.requireNonNull(withTimeout(10_000, () -> {
                try {
                    return resolver.resolve(profile.host).get(0).getHostAddress();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }));
            if (profile.host == null) {
                throw new UnknownHostException();
            }
        }
    }
}