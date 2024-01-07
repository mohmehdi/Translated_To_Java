package com.github.shadowsocks.bg;

import android.content.Context;
import android.util.Base64;
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
import kotlinx.coroutines.*;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;

public class ProxyInstance implements AutoCloseable {
    private Profile profile;
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
        this.profile = profile;
        this.route = profile.route;
    }

    public void init(CoroutineScope resolver) throws UnknownHostException {
        if (profile.host.equals("198.199.101.152")) {
            MessageDigest mdg = MessageDigest.getInstance("SHA-1");
            mdg.update(Core.packageInfo.signaturesCompat.first().toByteArray());
            HttpURLConnection conn = (HttpURLConnection) RemoteConfig.proxyUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            try {
                runBlocking(Dispatchers.IO) {
                    conn.getOutputStream().bufferedWriter().use {
                        it.write("sig=" + Base64.encodeToString(mdg.digest(), Base64.DEFAULT));
                    }
                    conn.getInputStream().bufferedReader().readText();
                }
            } finally {
                conn.disconnectFromMain();
            }
            String[] proxies = conn.getInputStream().bufferedReader().readText().split("\\|");
            List<String> proxyList = new ArrayList<>(Arrays.asList(proxies));
            Collections.shuffle(proxyList);
            String[] proxy = proxyList.get(0).split(":");
            profile.host = proxy[0].trim();
            profile.remotePort = Integer.parseInt(proxy[1].trim());
            profile.password = proxy[2].trim();
            profile.method = proxy[3].trim();
        }

        if (route.equals(Acl.CUSTOM_RULES)) {
            Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
        }

        if (profile.host.parseNumericAddress() == null) {
            profile.host = runBlocking(Dispatchers.IO) {
                resolver(profile.host).first().getHostAddress();
            };
            if (profile.host == null) {
                throw new UnknownHostException();
            }
        }
    }

    public void start(BaseService.Interface service, File stat, File configFile, String extraFlag) {
        trafficMonitor = new TrafficMonitor(stat);

        this.configFile = configFile;
        JSONObject config = profile.toJson();
        if (pluginPath != null) {
            config.put("plugin", pluginPath).put("plugin_opts", plugin.toString());
        }
        configFile.writeText(config.toString());

        List<String> cmd = service.buildAdditionalArguments(new ArrayList<>(Arrays.asList(
                new File(((Context) service).getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                "-b", DataStore.listenAddress,
                "-l", String.valueOf(DataStore.portProxy),
                "-t", "600",
                "-S", stat.getAbsolutePath(),
                "-c", configFile.getAbsolutePath())));
        if (extraFlag != null) {
            cmd.add(extraFlag);
        }

        if (route != Acl.ALL) {
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

    @Override
    public void close() throws IOException {
        if (trafficMonitor != null) {
            trafficMonitor.close();

            try {
                Profile profile = ProfileManager.getProfile(profile.id);
                if (profile != null) {
                    profile.tx += current.txTotal;
                    profile.rx += current.rxTotal;
                    ProfileManager.updateProfile(profile);
                }
            } catch (IOException e) {
                if (!DataStore.directBootAware) {
                    throw e;
                }
                List<Profile> profiles = DirectBoot.getDeviceProfile().toList().filterNotNull();
                Profile profile = profiles.stream().filter(p -> p.id == profile.id).findFirst().orElse(null);
                if (profile != null) {
                    profile.tx += current.txTotal;
                    profile.rx += current.rxTotal;
                    profile.dirty = true;
                    DirectBoot.update(profile);
                    DirectBoot.listenForUnlock();
                }
            }
        }
        trafficMonitor = null;
        configFile.delete();
        configFile = null;
    }
}