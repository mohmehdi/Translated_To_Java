package com.github.shadowsocks.bg;

import android.content.Context;
import android.net.InetAddress;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.github.shadowsocks.Core;
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
import com.github.shadowsocks.utils.ParseUtils;
import com.github.shadowsocks.utils.RemoteConfig;
import com.github.shadowsocks.utils.SignaturesCompat;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.executable.Executable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class ProxyInstance implements AutoCloseable {
    private final Profile profile;
    private String route;
    private File configFile;
    private TrafficMonitor trafficMonitor;
    private PluginConfiguration plugin;
    private String pluginPath;

    public ProxyInstance(Profile profile, String route) {
        this.profile = profile;
        this.route = route;
    }

    public ProxyInstance(Profile profile) {
        this(profile, profile.route);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void init(Context context, InetAddress resolver) throws IOException, NoSuchAlgorithmException, ExecutionException, InterruptedException, TimeoutException {
        if (profile.host.equals("198.199.101.152")) {
            MessageDigest mdg = MessageDigest.getInstance("SHA-1");
            mdg.update(Core.packageInfo.signaturesCompat.get(0).toByteArray());
            HttpURLConnection conn = RemoteConfig.proxyUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String proxies = Objects.requireNonNull(getResponseBody(conn)).split("\\|")[0];
            List<String> proxyList = Arrays.stream(proxies.split(";")).collect(Collectors.toList());
            proxyList.shuffle();
            String proxy = proxyList.get(0);
            String[] proxyInfo = proxy.split(":");
            profile.host = ParseUtils.parseNumericAddress(proxyInfo[0].trim());
            profile.remotePort = Integer.parseInt(proxyInfo[1].trim());
            profile.password = proxyInfo[2].trim();
            profile.method = proxyInfo[3].trim();
        }

        if (route.equals(Acl.CUSTOM_RULES)) {
            Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
        }

        if (!profile.host.equals(NumericUtils.parseNumericAddress(profile.host))) {
            profile.host = Objects.requireNonNull(getInetAddress(context, profile.host)).getHostAddress();
        }
    }

    public void start(BaseService.Interface service, File stat, File configFile, String extraFlag) throws IOException {
        trafficMonitor = new TrafficMonitor(stat);

        this.configFile = configFile;
        String config = profile.toJson();
        if (pluginPath != null) {
            JSONObject configJson = new JSONObject(config);
            configJson.put("plugin", pluginPath);
            configJson.put("plugin_opts", plugin.toString());
            config = configJson.toString();
        }
        FileUtils.writeFile(configFile, config);

        List<String> cmd = service.buildAdditionalArguments(Arrays.asList(
                new File(Objects.requireNonNull(context.getApplicationInfo()).nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                "-b", DataStore.listenAddress,
                "-l", DataStore.portProxy.toString(),
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

    @Override
    public void close() {
        if (trafficMonitor != null) {
            trafficMonitor.close();
            try {
                Profile profile = ProfileManager.getProfile(profile.id);
                if (profile != null) {
                    profile.tx += trafficMonitor.current.txTotal;
                    profile.rx += trafficMonitor.current.rxTotal;
                    ProfileManager.updateProfile(profile);
                }
            } catch (IOException e) {
                if (!DataStore.directBootAware) {
                    throw new RuntimeException(e);
                }
                List<Profile> deviceProfile = DirectBoot.getDeviceProfile();
                if (deviceProfile != null) {
                    Profile targetProfile = deviceProfile.stream()
                            .filter(p -> p.id.equals(profile.id))
                            .findFirst()
                            .orElse(null);
                    if (targetProfile != null) {
                        targetProfile.tx += trafficMonitor.current.txTotal;
                        targetProfile.rx += trafficMonitor.current.rxTotal;
                        targetProfile.dirty = true;
                        DirectBoot.update(targetProfile);
                        DirectBoot.listenForUnlock();
                    }
                }
            }
            trafficMonitor = null;
        }
        if (configFile != null) {
            configFile.delete();
        }
        configFile = null;
    }

}