package com.github.shadowsocks.bg;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.UserManagerCompat;
import android.support.v4.os.UserManagerCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import com.github.shadowsocks.App;
import com.github.shadowsocks.R;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.AclSyncJob;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.plugin.PluginOptions;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.*;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import kotlin.Lazy;
import kotlin.Unit;
import kotlin.jvm.Transient;


public class BaseService {
    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static final String CONFIG_FILE = "shadowsocks.conf";

class Data {
    private final Interface service;
    @Volatile
    private Profile profile;
    @Volatile
    private int state = STOPPED;
    @Volatile
    private PluginOptions plugin = new PluginOptions();
    @Volatile
    private String pluginPath;
    private GuardedProcess sslocalProcess;

    private Timer timer;
    private TrafficMonitorThread trafficMonitorThread;

    private final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
    private final Set<IBinder> bandwidthListeners = new HashSet<>(); // the binder is the real identifier

    private ServiceNotification notification;
    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            try {
                Method method = intent.getClass().getMethod("getAction");
                action = (String) method.invoke(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }

            switch (action) {
                case Action.RELOAD:
                    service.forceLoad();
                    break;
                default:
                    Toast.makeText(service, R.string.stopping, Toast.LENGTH_SHORT).show();
                    service.stopRunner(true);
            }
        }
    };
    @Volatile
    private boolean closeReceiverRegistered;

    private final IShadowsocksService.Stub binder = new IShadowsocksService.Stub() {
        @Override
        public int getState() {
            return Data.this.state;
        }

        @Override
        public String getProfileName() {
            return (profile != null) ? profile.formattedName : "Idle";
        }

        @Override
        public void registerCallback(IShadowsocksServiceCallback cb) {
            callbacks.register(cb);
        }

        @Override
        public void startListeningForBandwidth(IShadowsocksServiceCallback cb) {
            if (bandwidthListeners.add(cb.asBinder())) {
                if (timer == null) {
                    Timer t = new Timer(true);
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (state == CONNECTED && TrafficMonitor.updateRate()) {
                                Executor executor = Executors.newSingleThreadExecutor();
                                executor.execute(() -> {
                                    long txRate = TrafficStats.getUidTxPackets(service.getUid()) * 100 / (double) TrafficStats.getUidRxPackets(service.getUid());
                                    long rxRate = TrafficStats.getUidRxPackets(service.getUid()) * 100 / (double) TrafficStats.getUidRxPackets(service.getUid());
                                    long txTotal = TrafficStats.getTotalTxBytes();
                                    long rxTotal = TrafficStats.getTotalRxBytes();

                                    int n = callbacks.beginBroadcast();
                                    for (int i = 0; i < n; i++) {
                                        try {
                                            IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                                            if (bandwidthListeners.contains(item.asBinder()))
                                                item.trafficUpdated(profile.id, txRate, rxRate, txTotal, rxTotal);
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    callbacks.finishBroadcast();
                                });
                            }
                        }
                    }, 1000, 1000);
                    timer = t;
                }
                TrafficMonitor.updateRate();
                if (state == CONNECTED) {
                    try {
                        cb.trafficUpdated(profile.id,
                                TrafficMonitor.txRate, TrafficMonitor.rxRate,
                                TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void stopListeningForBandwidth(IShadowsocksServiceCallback cb) {
            if (bandwidthListeners.remove(cb.asBinder()) && bandwidthListeners.isEmpty()) {
                timer.cancel();
                timer = null;
            }
        }

        @Override
        public void unregisterCallback(IShadowsocksServiceCallback cb) {
            stopListeningForBandwidth(cb); // saves an RPC, and safer
            callbacks.unregister(cb);
        }
    };

    internal Data(Interface service) {
        this.service = service;
    }

    internal void updateTrafficTotal(long tx, long rx) {
        if (profile == null) return;

        Profile p = ProfileManager.getProfile(profile.id);
        if (p == null) return; // profile may have host, etc. modified

        p.tx += tx;
        p.rx += rx;

        ProfileManager.updateProfile(p);

        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int n = callbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                    if (bandwidthListeners.contains(item.asBinder()))
                        item.trafficPersisted(p.id);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            callbacks.finishBroadcast();
        });
    }

    internal File shadowsocksConfigFile;

    internal File buildShadowsocksConfig() {
        if (profile == null) return null;

        JSONObject config = new JSONObject();
        try {
            config.put("server", profile.host);
            config.put("server_port", profile.remotePort);
            config.put("password", profile.password);
            config.put("method", profile.method);

            if (pluginPath != null) {
                List<String> pluginCmd = new ArrayList<>();
                pluginCmd.add(pluginPath);
                if (TcpFastOpen.sendEnabled) pluginCmd.add("--fast-open");

                String pluginArgs = service.buildAdditionalArguments(pluginCmd);
                config.put("plugin", pluginArgs);
                config.put("plugin_opts", plugin.toString());
            }

            File file;
            if (UserManagerCompat.isUserUnlocked(app)) {
                file = new File(app.getFilesDir(), CONFIG_FILE);
            } else {
                file = new File((app.getDeviceContext().getNoBackupFilesDir()), CONFIG_FILE);
            }
            shadowsocksConfigFile = file;
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(config.toString());
            writer.close();

            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private File aclFile() {
        if (profile == null) return null;

        String route = profile.route;
        if (Acl.ALL.equals(route)) return null;

        return Acl.getFile(route);
    }

    void changeState(int s, @Nullable String msg) {
        if (state == s && msg == null) return;

        if (callbacks.getRegisteredCallbackCount() > 0) {
            Executor executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                int n = callbacks.beginBroadcast();
                for (int i = 0; i < n; i++) {
                    try {
                        IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                        item.stateChanged(s, binder.getProfileName(), msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                callbacks.finishBroadcast();
            });
        }
        state = s;
    }
}







public interface Interface {
    String tag = "ShadowSocks";

    IBinder onBind(Intent intent);

    default boolean checkProfile(Profile profile) {
        if (TextUtils.isEmpty(profile.host) || TextUtils.isEmpty(profile.password)) {
            stopRunner(true, getString(R.string.proxy_empty));
            return false;
        }
        return true;
    }

    default void forceLoad() {
        Profile p = app.currentProfile;
        if (p == null)
            return stopRunner(true, getString(R.string.profile_empty));

        if (!checkProfile(p)) return;

        int s = data.state;
        switch (s) {
            case STOPPED:
                startRunner();
                break;
            case CONNECTED:
                stopRunner(false);
                startRunner();
                break;
            default:
                Log.w(tag, "Illegal state when invoking use: " + s);
        }
    }

    default ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
        return cmd;
    }

    default void startNativeProcesses() {
        Data data = this.data;
        List<String> cmd = buildAdditionalArguments(new ArrayList<>(Arrays.asList(
                new File(getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                "-u",
                "-b", "127.0.0.1",
                "-l", String.valueOf(DataStore.portProxy),
                "-t", "600",
                "-c", data.buildShadowsocksConfig().getAbsolutePath()
        )));

        File acl = data.aclFile;
        if (acl != null) {
            cmd.add("--acl");
            cmd.add(acl.getAbsolutePath());
        }

        if (TcpFastOpen.sendEnabled) cmd.add("--fast-open");

        data.sslocalProcess = new GuardedProcess(cmd).start();
    }

    ServiceNotification createNotification(String profileName);

    default void startRunner() {
        if (Build.VERSION.SDK_INT >= 26)
            startForegroundService(new Intent(this, getClass()));
        else
            startService(new Intent(this, getClass()));
    }

    default void killProcesses() {
        Data data = data;
        if (data.sslocalProcess != null) {
            data.sslocalProcess.destroy();
            data.sslocalProcess = null;
        }
    }

    default void stopRunner(boolean stopService, String msg) {
        // change the state
        Data data = data;
        data.changeState(STOPPING);

        app.track(tag, "stop");

        killProcesses();

        // clean up recevier
        if (data.closeReceiverRegistered) {
            unregisterReceiver(data.closeReceiver);
            data.closeReceiverRegistered = false;
        }

        data.shadowsocksConfigFile.delete();    // remove old config possibly in device storage
        data.shadowsocksConfigFile = null;

        data.notification.cancel();
        data.notification = null;

        // Make sure update total traffic when stopping the runner
        data.updateTrafficTotal(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes());

        TrafficMonitor.reset();
        data.trafficMonitorThread.stopThread();
        data.trafficMonitorThread = null;

        // change the state
        data.changeState(STOPPED, msg);

        // stop the service if nothing has bound to it
        if (stopService) stopSelf();

        data.profile = null;
    }

    default Data data() {
        return instances.get(this);
    }

    @Nullable
    @Override
    default IBinder onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Data data = data();
        if (data.state != STOPPED) return Service.START_NOT_STICKY;
        Profile profile = app.currentProfile;
        if (profile == null) {
            data.notification = createNotification("");  // gracefully shutdown: https://stackoverflow.com/questions/47337857/context-startforegroundservice-did-not-then-call-service-startforeground-eve
            stopRunner(true, getString(R.string.profile_empty));
            return Service.START_NOT_STICKY;
        }
        data.profile = profile;

        TrafficMonitor.reset();
        ExecutorService thread = Executors.newSingleThreadExecutor();
        thread.execute(() -> {
            try {
                if (profile.host.equals("198.199.101.152")) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .dns(address -> {
                                List<InetAddress> inetAddresses = Dns.resolve(address, false);
                                if (inetAddresses == null) throw new UnknownHostException();
                                return Arrays.asList(inetAddresses.get(0).getHostAddress());
                            })
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .build();
                    MessageDigest mdg = MessageDigest.getInstance("SHA-1");
                    mdg.update(app.info.signatures[0].toByteArray());
                    RequestBody requestBody = new FormBody.Builder()
                            .add("sig", Base64.encodeToString(mdg.digest(), 0))
                            .build();
                    Request request = new Request.Builder()
                            .url(app.remoteConfig.getString("proxy_url"))
                            .post(requestBody)
                            .build();

                    Response proxies = client.newCall(request).execute();
                    List<String> proxiesList = Arrays.asList(proxies.body().string().split("\\|"));
                    Collections.shuffle(proxiesList);
                    String[] proxy = proxiesList.get(0).split(":");
                    profile.host = proxy[0].trim();
                    profile.remotePort = Integer.parseInt(proxy[1].trim());
                    profile.password = proxy[2].trim();
                    profile.method = proxy[3].trim();
                }

                if (profile.route.equals(Acl.CUSTOM_RULES))
                    Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));

                data.plugin = new PluginConfiguration(profile.plugin == null ? "" : profile.plugin).selectedOptions;
                data.pluginPath = PluginManager.init(data.plugin);

                // Clean up
                killProcesses();

                if (!profile.host.isNumericAddress())
                    profile.host = Objects.requireNonNull(Dns.resolve(profile.host, true)).getHostAddress();

                startNativeProcesses();

                if (!profile.route.equals(Acl.ALL) && !profile.route.equals(Acl.CUSTOM_RULES))
                    AclSyncJob.schedule(profile.route);

                data.changeState(CONNECTED);
            } catch (UnknownHostException e) {
                stopRunner(true, getString(R.string.invalid_server));
            } catch (VpnService.NullConnectionException e) {
                stopRunner(true, getString(R.string.reboot_required));
            } catch (Throwable e) {
                stopRunner(true, String.format(Locale.getDefault(), "%s: %s", getString(R.string.service_failed), e.getMessage()));
                app.track(e);
            }
        });
        return Service.START_NOT_STICKY;
    }
}







private Map<Interface, Data> instances = new WeakHashMap<>();

public void register(Interface instance) {
    instances.put(instance, new Data(instance));
}

public boolean isUsingVpnMode() {
    return DataStore.serviceMode == Key.modeVpn;
}

public Class<? extends Any> getServiceClass() {
    switch (DataStore.serviceMode) {
        case Key.modeProxy:
            return ProxyService.class;
        case Key.modeVpn:
            return VpnService.class;
        case Key.modeTransproxy:
            return TransproxyService.class;
        default:
            throw new UnknownError();
    }
}

}