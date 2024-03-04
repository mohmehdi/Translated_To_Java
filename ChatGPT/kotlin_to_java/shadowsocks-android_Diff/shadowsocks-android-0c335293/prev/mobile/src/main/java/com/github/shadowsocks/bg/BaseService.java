package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import android.text.TextUtils;
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
import org.json.JSONObject;

import java.io.File;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BaseService extends Service {

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static class Data {

        private final Interface service;
        public volatile Profile profile;
        public volatile int state = STOPPED;
        public volatile PluginOptions plugin = new PluginOptions();
        public volatile String pluginPath;
        public GuardedProcess sslocalProcess;

        public Timer timer;
        public TrafficMonitorThread trafficMonitorThread;

        public final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
        public final HashSet<IBinder> bandwidthListeners = new HashSet<>();

        public ServiceNotification notification;
        public BroadcastReceiver closeReceiver;
        public boolean closeReceiverRegistered = false;

        public Data(Interface service) {
            this.service = service;
        }

        private int state() {
            return state;
        }

        public IShadowsocksService.Stub binder = new IShadowsocksService.Stub() {
            @Override
            public int getState() {
                return state();
            }

            @Override
            public String getProfileName() {
                return profile != null ? profile.formattedName : "Idle";
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
                                    app.handler.post(() -> {
                                        if (bandwidthListeners.size() > 0) {
                                            long txRate = TrafficMonitor.txRate;
                                            long rxRate = TrafficMonitor.rxRate;
                                            long txTotal = TrafficMonitor.txTotal;
                                            long rxTotal = TrafficMonitor.rxTotal;
                                            int n = callbacks.beginBroadcast();
                                            for (int i = 0; i < n; i++) {
                                                try {
                                                    IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                                                    if (bandwidthListeners.contains(item.asBinder())) {
                                                        item.trafficUpdated(profile.id, txRate, rxRate, txTotal, rxTotal);
                                                    }
                                                } catch (Exception ignored) {
                                                }
                                            }
                                            callbacks.finishBroadcast();
                                        }
                                    });
                                }
                            }
                        }, 1000, 1000);
                        timer = t;
                    }
                    TrafficMonitor.updateRate();
                    if (state == CONNECTED) {
                        cb.trafficUpdated(profile.id,
                                TrafficMonitor.txRate, TrafficMonitor.rxRate,
                                TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
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
                stopListeningForBandwidth(cb);
                callbacks.unregister(cb);
            }
        };

        public void updateTrafficTotal(long tx, long rx) {
            Profile profile = this.profile;
            if (profile == null) return;
            Profile p = ProfileManager.getProfile(profile.id);
            if (p == null) return;
            p.tx += tx;
            p.rx += rx;
            ProfileManager.updateProfile(p);
            app.handler.post(() -> {
                if (bandwidthListeners.size() > 0) {
                    int n = callbacks.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        try {
                            IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                            if (bandwidthListeners.contains(item.asBinder())) {
                                item.trafficPersisted(p.id);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    callbacks.finishBroadcast();
                }
            });
        }

        public void buildShadowsocksConfig() {
            Profile profile = this.profile;
            if (profile == null) return;
            JSONObject config = new JSONObject()
                    .put("server", profile.host)
                    .put("server_port", profile.remotePort)
                    .put("password", profile.password)
                    .put("method", profile.method);
            String pluginPath = this.pluginPath;
            if (pluginPath != null) {
                ArrayList<String> pluginCmd = new ArrayList<>(Collections.singletonList(pluginPath));
                if (TcpFastOpen.sendEnabled) pluginCmd.add("--fast-open");
                config
                        .put("plugin", Commandline.toString(service.buildAdditionalArguments(pluginCmd)))
                        .put("plugin_opts", plugin.toString());
            }
            try {
                File file = new File(app.filesDir, "shadowsocks.json");
                file.getParentFile().mkdirs();
                file.createNewFile();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(config.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface Interface {
        String tag();

        default IBinder onBind(Intent intent) {
            return (intent.getAction() != null && intent.getAction().equals(Action.SERVICE)) ? data.binder : null;
        }

        default boolean checkProfile(Profile profile) {
            Context context = (Context) this;
            if (TextUtils.isEmpty(profile.host) || TextUtils.isEmpty(profile.password)) {
                stopRunner(true, context.getString(R.string.proxy_empty));
                return false;
            }
            return true;
        }

        default void forceLoad() {
            Profile p = app.currentProfile;
            if (p == null) {
                return stopRunner(true, ((Context) this).getString(R.string.profile_empty));
            }
            if (!checkProfile(p)) {
                return;
            }
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
                    Log.w(tag(), "Illegal state when invoking use: " + s);
            }
        }

        default ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
            return cmd;
        }

        default void startNativeProcesses() {
            Data data = data();
            data.buildShadowsocksConfig();
            ArrayList<String> cmd = buildAdditionalArguments(new ArrayList<>(Arrays.asList(
                    new File(((Context) this).applicationInfo.nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                    "-u",
                    "-b", "127.0.0.1",
                    "-l", String.valueOf(DataStore.portProxy),
                    "-t", "600",
                    "-c", "shadowsocks.json")));

            Acl.Route route = data.profile.route;
            if (route != Acl.ALL) {
                cmd.add("--acl");
                cmd.add(Acl.getFile(route == Acl.CUSTOM_RULES ? Acl.CUSTOM_RULES_FLATTENED : route).getAbsolutePath());
            }

            if (TcpFastOpen.sendEnabled) {
                cmd.add("--fast-open");
            }

            data.sslocalProcess = new GuardedProcess(cmd).start();
        }

        default ServiceNotification createNotification() {
            return null;
        }

        default void startRunner() {
            Context context = (Context) this;
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(new Intent(context, getClass()));
            } else {
                context.startService(new Intent(context, getClass()));
            }
        }

        default void killProcesses() {
            Data data = data();
            if (data.sslocalProcess != null) {
                data.sslocalProcess.destroy();
                data.sslocalProcess = null;
            }
        }

        default void stopRunner(boolean stopService, String msg) {
            changeState(STOPPING);

            app.track(tag(), "stop");

            killProcesses();

            Data data = data();
            Context context = (Context) this;
            if (data.closeReceiverRegistered) {
                context.unregisterReceiver(data.closeReceiver);
                data.closeReceiverRegistered = false;
            }

            if (data.notification != null) {
                data.notification.destroy();
                data.notification = null;
            }

            data.updateTrafficTotal(TrafficMonitor.txTotal, TrafficMonitor.rxTotal);

            TrafficMonitor.reset();
            if (data.trafficMonitorThread != null) {
                data.trafficMonitorThread.stopThread();
                data.trafficMonitorThread = null;
            }

            changeState(STOPPED, msg);

            if (stopService) {
                ((Service) this).stopSelf();
            }

            data.profile = null;
        }

        default Data data() {
            return instances.get(this);
        }

        default int onStartCommand(Intent intent, int flags, int startId) {
            Data data = data();
            if (data.state != STOPPED) {
                return Service.START_NOT_STICKY;
            }
            Profile profile = app.currentProfile;
            if (profile == null) {
                stopRunner(true);
                return Service.START_NOT_STICKY;
            }
            data.profile = profile;
            profile.name = profile.formattedName;

            TrafficMonitor.reset();
            TrafficMonitorThread thread = new TrafficMonitorThread();
            thread.start();
            data.trafficMonitorThread = thread;

            if (!data.closeReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Action.RELOAD);
                filter.addAction(Intent.ACTION_SHUTDOWN);
                filter.addAction(Action.CLOSE);
                ((Context) this).registerReceiver(data.closeReceiver, filter);
                data.closeReceiverRegistered = true;
            }

            data.notification = createNotification();
            app.track(tag(), "start");

            changeState(CONNECTING);

            new Thread(() -> {
                Context context = (Context) this;
                try {
                    if ("198.199.101.152".equals(profile.host)) {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .dns(hostname -> {
                                    List<InetAddress> addresses = Collections.singletonList((Dns.resolve(hostname, false) != null) ?
                                            Objects.requireNonNull(Dns.resolve(hostname, false)).parseNumericAddress() :
                                            throw new UnknownHostException());
                                    return addresses;
                                })
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .writeTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build();
                        MessageDigest mdg = MessageDigest.getInstance("SHA-1");
                        mdg.update(app.info.signatures[0].toByteArray());
                        FormBody requestBody = new FormBody.Builder()
                                .add("sig", new String(Base64.encode(mdg.digest(), 0)))
                                .build();
                        Request request = new Request.Builder()
                                .url(app.remoteConfig.getString("proxy_url"))
                                .post(requestBody)
                                .build();

                        String response = Objects.requireNonNull(client.newCall(request).execute().body()).string();
                        List<String> proxies = Arrays.asList(response.split("\\|"));
                        Collections.shuffle(proxies);
                        String proxy = proxies.get(0);
                        String[] parts = proxy.split(":");
                        profile.host = parts[0].trim();
                        profile.remotePort = Integer.parseInt(parts[1].trim());
                        profile.password = parts[2].trim();
                        profile.method = parts[3].trim();
                    }

                    if (profile.route == Acl.CUSTOM_RULES) {
                        Acl.save(Acl.CUSTOM_RULES_FLATTENED, Acl.customRules.flatten(10));
                    }

                    data.plugin = new PluginConfiguration(profile.plugin != null ? profile.plugin : "").selectedOptions;
                    data.pluginPath = PluginManager.init(data.plugin);

                    killProcesses();

                    if (!profile.host.isNumericAddress()) {
                        profile.host = Dns.resolve(profile.host, true);
                        if (profile.host == null) throw new UnknownHostException();
                    }

                    startNativeProcesses();

                    if (!Arrays.asList(Acl.ALL, Acl.CUSTOM_RULES).contains(profile.route)) {
                        AclSyncJob.schedule(profile.route);
                    }

                    changeState(CONNECTED);
                } catch (UnknownHostException e) {
                    stopRunner(true, context.getString(R.string.invalid_server));
                } catch (VpnService.NullConnectionException e) {
                    stopRunner(true, context.getString(R.string.reboot_required));
                } catch (Throwable exc) {
                    stopRunner(true, context.getString(R.string.service_failed) + ": " + exc.getMessage());
                    app.track(exc);
                }
            }).start();

            return Service.START_NOT_STICKY;
        }

        default void changeState(int s, String msg) {
            Data data = instances.get(this);
            if (data.state == s && msg == null) return;
            if (data.callbacks.getRegisteredCallbackCount() > 0) {
                app.handler.post(() -> {
                    int n = data.callbacks.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        try {
                            data.callbacks.getBroadcastItem(i).stateChanged(s, data.binder.profileName, msg);
                        } catch (Exception ignored) {
                        }
                    }
                    data.callbacks.finishBroadcast();
                });
            }
            data.state = s;
        }
    }

    private static final WeakHashMap<Interface, Data> instances = new WeakHashMap<>();

    static void register(Interface instance) {
        instances.put(instance, new Data(instance));
    }

    public static boolean usingVpnMode() {
        return DataStore.serviceMode == Key.modeVpn;
    }

    public static Class<?> serviceClass() {
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
