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
import com.github.shadowsocks.utils.UtilsKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.withContext;
import kotlinx.coroutines.withTimeout;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

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
        this.configFile = null;
        this.trafficMonitor = null;
        this.plugin = new PluginConfiguration(profile.getPlugin() != null ? profile.getPlugin() : "");
        this.pluginPath = PluginManager.INSTANCE.init(plugin).getSelectedOptions();
    }

    public ProxyInstance(Profile profile) {
        this(profile, profile.getRoute());
    }

    public Profile getProfile() {
        return profile;
    }

    public String getRoute() {
        return route;
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

    public PluginConfiguration getPlugin() {
        return plugin;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public void init(UtilsKt.Resolver resolver) {
        if (profile.getHost().equals("198.199.101.152")) {
            try {
                MessageDigest mdg = MessageDigest.getInstance("SHA-1");
                mdg.update(Core.getPackageInfo().signaturesCompat().get(0).toByteArray());
                HttpURLConnection conn = (HttpURLConnection) RemoteConfig.INSTANCE.getProxyUrl().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                List<String> proxies = new ArrayList<>();
                try {
                    withTimeout(30000, () -> {
                        withContext(Dispatchers.IO, () -> {
                            conn.getOutputStream().write(("sig=" + Base64.encodeToString(mdg.digest(), Base64.DEFAULT)).getBytes());
                            return conn.getInputStream().bufferedReader().readText();
                        });
                    });
                } finally {
                    conn.disconnect();
                }
                proxies = UtilsKt.split$default(proxies, "|", false, 0, 6, null).toMutableList();
                UtilsKt.shuffle(proxies);
                List<String> proxy = UtilsKt.split$default(proxies.get(0), ":", false, 0, 6, null);
                profile.setHost(proxy.get(0).trim());
                profile.setRemotePort(Integer.parseInt(proxy.get(1).trim()));
                profile.setPassword(proxy.get(2).trim());
                profile.setMethod(proxy.get(3).trim());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (route.equals(Acl.CUSTOM_RULES)) {
            Acl.save(Acl.CUSTOM_RULES, UtilsKt.flatten(Acl.getCustomRules(), 10));
        }

        if (UtilsKt.parseNumericAddress(profile.getHost()) == null) {
            try {
                profile.setHost(withTimeout(10000, () -> {
                    return withContext(Dispatchers.IO, () -> {
                        try {
                            return resolver.resolve(profile.getHost()).getHostAddress();
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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
        configFile.writeText(config.toString());

        List<String> cmd = new ArrayList<>();
        cmd.add(new File(((Context) service).getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath());
        cmd.add("-b");
        cmd.add(DataStore.INSTANCE.getListenAddress());
        cmd.add("-l");
        cmd.add(String.valueOf(DataStore.INSTANCE.getPortProxy()));
        cmd.add("-t");
        cmd.add("600");
        cmd.add("-S");
        cmd.add(stat.getAbsolutePath());
        cmd.add("-c");
        cmd.add(configFile.getAbsolutePath());
        if (extraFlag != null) {
            cmd.add(extraFlag);
        }

        if (!route.equals(Acl.ALL)) {
            cmd.add("--acl");
            cmd.add(Acl.getFile(route).getAbsolutePath());
        }

        if (profile.isUdpdns()) {
            cmd.add("-D");
        }

        if (DataStore.INSTANCE.isTcpFastOpen()) {
            cmd.add("--fast-open");
        }

        service.getData().getProcesses().start(cmd);
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
                Profile profile = ProfileManager.INSTANCE.getProfile(this.profile.getId());
                if (profile != null) {
                    profile.setTx(profile.getTx() + trafficMonitor.getCurrent().getTxTotal());
                    profile.setRx(profile.getRx() + trafficMonitor.getCurrent().getRxTotal());
                    ProfileManager.INSTANCE.updateProfile(profile);
                }
            } catch (IOException e) {
                if (!DataStore.INSTANCE.isDirectBootAware()) {
                    throw new RuntimeException(e);
                }
                Profile profile = DirectBoot.INSTANCE.getDeviceProfile().toList().stream().filter(p -> p.getId() == this.profile.getId()).findFirst().orElse(null);
                if (profile != null) {
                    profile.setTx(profile.getTx() + trafficMonitor.getCurrent().getTxTotal());
                    profile.setRx(profile.getRx() + trafficMonitor.getCurrent().getRxTotal());
                    profile.setDirty(true);
                    DirectBoot.INSTANCE.update(profile);
                    DirectBoot.INSTANCE.listenForUnlock();
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