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
import com.github.shadowsocks.utils.disconnectFromMain;
import com.github.shadowsocks.utils.parseNumericAddress;
import com.github.shadowsocks.utils.signaturesCompat;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.RemoteConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProxyInstance {
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
    public void init(Context context) throws NoSuchAlgorithmException, IOException, JSONException, ExecutionException, InterruptedException, TimeoutException {
        if (profile.host.equals("198.199.101.152")) {
            MessageDigest mdg = MessageDigest.getInstance("SHA-1");
            mdg.update(Core.packageInfo.signaturesCompat.get(0).toByteArray());
            URL connUrl = new URL(RemoteConfig.proxyUrl);
            HttpURLConnection conn = (HttpURLConnection) connUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String proxies = conn.getResponseMessage();
            conn.disconnectFromMain();

            JSONArray jsonArray = new JSONArray(proxies);
            Arrays.sort(jsonArray);
            JSONObject jsonObject = jsonArray.getJSONObject(0);
            String[] proxy = new String[]{jsonObject.getString("host"), jsonObject.getString("port"), jsonObject.getString("password"), jsonObject.getString("method")};
            profile.host = proxy[0].trim();
            profile.remotePort = Integer.parseInt(proxy[1].trim());
            profile.password = proxy[2].trim();
            profile.method = proxy[3].trim();
        }

        if (route.equals(Acl.CUSTOM_RULES)) {
            Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
        }

        if (!profile.host.parseNumericAddress().equals("")) {
            profile.host = Objects.requireNonNull(getInetAddressAsync(context, profile.host)).getHostAddress();
        } else {
            throw new UnknownHostException();
        }
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private InetAddress getInetAddressAsync(Context context, String host) throws ExecutionException, InterruptedException {
        List<InetAddress> inetAddresses = executor.submit(() -> {
            try {
                return InetAddress.getAllByName(host);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }).get(10, TimeUnit.SECONDS);

        if (inetAddresses.size() > 0) {
            return inetAddresses.get(0);
        } else {
            return null;
        }
    }

    public void start(BaseService.Interface service, File stat, File configFile, String extraFlag) {
        trafficMonitor = new TrafficMonitor(stat);

        this.configFile = configFile;
        JSONObject config = profile.toJson();
        if (pluginPath != null) {
            config.put("plugin", pluginPath);
            config.put("plugin_opts", plugin.toString());
        }
        try (FileOutputStream fos = new FileOutputStream(configFile);
             OutputStreamWriter osw = new OutputStreamWriter(fos);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(config.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> cmd = service.buildAdditionalArguments(Arrays.asList(
                new File(context.getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
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

        if (profile.route.equals(Acl.ALL) || profile.route.equals(Acl.BYPASS_LAN)) {
            cmd.add("-D");
        }

        if (DataStore.tcpFastOpen) {
            cmd.add("--fast-open");
        }

        service.data.processes.start(cmd);
    }

    public void scheduleUpdate() {
        if (!route.equals(Acl.ALL) && !route.equals(Acl.CUSTOM_RULES)) {
            AclSyncer.schedule(route);
        }
    }

    public void shutdown(CoroutineScope scope) {
        if (trafficMonitor != null) {
            trafficMonitor.shutdown(scope);

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
                List<Profile> profiles = DirectBoot.getDeviceProfile().toList();
                Profile targetProfile = profiles.stream().filter(p -> p.id.equals(profile.id)).findFirst().orElse(null);
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
        if (configFile != null) {
            configFile.delete();
        }
        configFile = null;
    }
}