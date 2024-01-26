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

public class ProxyInstance {
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

    public File getConfigFile() {
        return configFile;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    public TrafficMonitor getTrafficMonitor() {
        return trafficMonitor;
    }

    public void setTrafficMonitor(TrafficMonitor trafficMonitor) {
        this.trafficMonitor = trafficMonitor;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public void setPluginPath(String pluginPath) {
        this.pluginPath = pluginPath;
    }

    public void init(CoroutineScope scope, Function1<? super String, ? extends Array<InetAddress>> resolver) throws UnknownHostException {
        if (profile.getHost().equals("198.199.101.152")) {
            MessageDigest mdg = MessageDigest.getInstance("SHA-1");
            mdg.update(Core.packageInfo.signaturesCompat.first().toByteArray());
            HttpURLConnection conn = (HttpURLConnection) RemoteConfig.proxyUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String proxies = null;
            try {
                runBlocking {
                    withTimeout(30_000) {
                        withContext(Dispatchers.IO) {
                            conn.getOutputStream().bufferedWriter().use {
                                it.write("sig=" + Base64.encodeToString(mdg.digest(), Base64.DEFAULT));
                            }
                            proxies = conn.getInputStream().bufferedReader().readText();
                        }
                    }
                }
            } finally {
                conn.disconnectFromMain();
            }
            List<String> proxyList = Arrays.asList(proxies.split("\\|"));
            Collections.shuffle(proxyList);
            String proxy = proxyList.get(0);
            String[] proxyParts = proxy.split(":");
            profile.setHost(proxyParts[0].trim());
            profile.setRemotePort(Integer.parseInt(proxyParts[1].trim()));
            profile.setPassword(proxyParts[2].trim());
            profile.setMethod(proxyParts[3].trim());
        }

        if (route.equals(Acl.CUSTOM_RULES)) {
            Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
        }

        if (profile.getHost().parseNumericAddress() == null) {
            profile.setHost(runBlocking {
                GlobalScope.async(Dispatchers.IO) {
                    resolver.invoke(profile.getHost())[0].getHostAddress();
                }.await();
            });
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

        List<String> cmd = service.buildAdditionalArguments(new ArrayList<String>(Arrays.asList(
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

        if (profile.getRoute().equals(Acl.ALL) || profile.getRoute().equals(Acl.BYPASS_LAN)) {
            cmd.add("-D");
        }

        if (DataStore.tcpFastOpen) {
            cmd.add("--fast-open");
        }

        service.getData().getProcesses().start(cmd);
    }

    public void scheduleUpdate() {
        if (!route.equals(Acl.ALL) && !route.equals(Acl.CUSTOM_RULES)) {
            AclSyncer.schedule(route);
        }
    }

    public void shutdown(CoroutineScope scope) throws IOException {
        if (trafficMonitor != null) {
            trafficMonitor.thread.shutdown(scope);

            try {
                Profile profile = ProfileManager.getProfile(profile.getId());
                if (profile != null) {
                    profile.setTx(profile.getTx() + trafficMonitor.current.getTxTotal());
                    profile.setRx(profile.getRx() + trafficMonitor.current.getRxTotal());
                    ProfileManager.updateProfile(profile);
                }
            } catch (IOException e) {
                if (!DataStore.directBootAware) {
                    throw e;
                }
                Profile profile = DirectBoot.getDeviceProfile().toList().filterNotNull().stream().filter(p -> p.getId() == profile.getId()).findFirst().orElse(null);
                if (profile != null) {
                    profile.setTx(profile.getTx() + trafficMonitor.current.getTxTotal());
                    profile.setRx(profile.getRx() + trafficMonitor.current.getRxTotal());
                    profile.setDirty(true);
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