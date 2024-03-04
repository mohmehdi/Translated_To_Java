package com.github.shadowsocks.bg;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteCallbackList;
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
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.Commandline;
import com.github.shadowsocks.utils.Dns;
import com.github.shadowsocks.utils.Executable;
import com.github.shadowsocks.utils.GuardedProcess;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.TrafficMonitorThread;

import org.json.JSONObject;

import java.io.File;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class BaseService extends Service {

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static final String CONFIG_FILE = "shadowsocks.conf";

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
        public final ServiceReceiver closeReceiver = new ServiceReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case Action.RELOAD:
                        service.forceLoad();
                        break;
                    default:
                        Toast.makeText((Context) service, R.string.stopping, Toast.LENGTH_SHORT).show();
                        service.stopRunner(true);
                        break;
                }
            }
        };
        public boolean closeReceiverRegistered = false;

        public final IShadowsocksService.Stub binder = new IShadowsocksService.Stub() {
            @Override
            public int getState() {
                return Data.this.state;
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
                                    App.app.handler.post(() -> {
                                        if (bandwidthListeners.isEmpty()) {
                                            int n = callbacks.beginBroadcast();
                                            for (int i = 0; i < n; i++) {
                                                try {
                                                    IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                                                    if (bandwidthListeners.contains(item.asBinder())) {
                                                        item.trafficUpdated(profile.id,
                                                                TrafficMonitor.txRate, TrafficMonitor.rxRate,
                                                                TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
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

        public Data(Interface service) {
            this.service = service;
        }

        public void updateTrafficTotal(long tx, long rx) {
            if (profile == null) return;
            Profile p = ProfileManager.getProfile(profile.id);
            if (p == null) return;
            p.tx += tx;
            p.rx += rx;
            ProfileManager.updateProfile(p);
            App.app.handler.post(() -> {
                if (!bandwidthListeners.isEmpty()) {
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

        public File shadowsocksConfigFile;
        public File buildShadowsocksConfig() {
            if (profile == null) return null;
            JSONObject config = new JSONObject();
            config.put("server", profile.host)
                    .put("server_port", profile.remotePort)
                    .put("password", profile.password)
                    .put("method", profile.method);
            String pluginPath = this.pluginPath;
            if (pluginPath != null) {
                ArrayList<String> pluginCmd = new ArrayList<>();
                pluginCmd.add(pluginPath);
                if (TcpFastOpen.sendEnabled) pluginCmd.add("--fast-open");
                config.put("plugin", Commandline.toString(service.buildAdditionalArguments(pluginCmd)))
                        .put("plugin_opts", plugin.toString());
            }

            File file = new File(UserManagerCompat.isUserUnlocked(App.app) ?
                    App.app.getFilesDir() : (Build.VERSION.SDK_INT >= 24 ?
                    App.app.deviceContext.noBackupFilesDir : null), CONFIG_FILE);
            shadowsocksConfigFile = file;
            String configString = config.toString();
            FileUtils.writeTextToFile(file, configString);
            return file;
        }

        public File getAclFile() {
            if (profile == null) return null;
            String route = profile.route;
            return (route == null || route.equals(Acl.ALL)) ? null : Acl.getFile(route);
        }

        public void changeState(int s, String msg) {
            if (state == s && msg == null) return;
            if (callbacks.getRegisteredCallbackCount() > 0) {
                App.app.handler.post(() -> {
                    int n = callbacks.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        try {
                            callbacks.getBroadcastItem(i).stateChanged(s, binder.getProfileName(), msg);
                        } catch (Exception ignored) {
                        }
                    }
                    callbacks.finishBroadcast();
                });
            }
            state = s;
        }
    }

    public interface Interface {
        String tag();

        default IBinder onBind(Intent intent) {
            return (intent != null && Action.SERVICE.equals(intent.getAction())) ? data.binder : null;
        }

        default boolean checkProfile(Profile profile) {
            if (profile.host.isEmpty() || profile.password.isEmpty()) {
                stopRunner(true, ((Context) this).getString(R.string.proxy_empty));
                return false;
            }
            return true;
        }

        default void forceLoad() {
            Profile p = App.app.currentProfile;
            if (p == null) {
                data.notification = data.service.createNotification("");
                stopRunner(true, ((Context) this).getString(R.string.profile_empty));
                return;
            }
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
                    Log.w(tag(), "Illegal state when invoking use: " + s);
                    break;
            }
        }

        default ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
            return cmd;
        }

        default void startNativeProcesses() {
            Interface service = data.service;
            ArrayList<String> cmd = buildAdditionalArguments(new ArrayList<>(List.of(
                    new File(((Context) service).getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                    "-u",
                    "-b", "127.0.0.1",
                    "-l", String.valueOf(DataStore.portProxy),
                    "-t", "600",
                    "-c", data.buildShadowsocksConfig().getAbsolutePath()
            )));

            File acl = data.getAclFile();
            if (acl != null) {
                cmd.add("--acl");
                cmd.add(acl.getAbsolutePath());
            }

            if (TcpFastOpen.sendEnabled) cmd.add("--fast-open");

            data.sslocalProcess = new GuardedProcess(cmd).start();
        }

        default ServiceNotification createNotification(String profileName) {
            return null;
        }

        default void startRunner() {
            this.stopRunner(true);
            Context context = (Context) this;
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(new Intent(context, this.getClass()));
            } else {
                context.startService(new Intent(context, this.getClass()));
            }
        }

        default void killProcesses() {
            GuardedProcess process = data.sslocalProcess;
            if (process != null) {
                process.destroy();
            }
            data.sslocalProcess = null;
        }

        default void stopRunner(boolean stopService) {
            Data data = this.data;
            data.changeState(STOPPING);

            App.app.track(tag(), "stop");

            killProcesses();

            if (this instanceof Service) {
                if (data.closeReceiverRegistered) {
                    ((Service) this).unregisterReceiver(data.closeReceiver);
                    data.closeReceiverRegistered = false;
                }
            }

            if (data.shadowsocksConfigFile != null) {
                data.shadowsocksConfigFile.delete();
            }
            data.shadowsocksConfigFile = null;

            if (data.notification != null) {
                data.notification.destroy();
            }
            data.notification = null;

            data.updateTrafficTotal(TrafficMonitor.txTotal, TrafficMonitor.rxTotal);

            TrafficMonitor.reset();
            if (data.trafficMonitorThread != null) {
                data.trafficMonitorThread.stopThread();
            }
            data.trafficMonitorThread = null;

            data.changeState(STOPPED, null);

            if (stopService) {
                ((Service) this).stopSelf();
            }

            data.profile = null;
        }

        default Data data() {
            return BaseService.instances.get(this);
        }

        default int onStartCommand(Intent intent, int flags, int startId) {
            Data data = this.data();
            if (data.state != STOPPED) {
                return Service.START_NOT_STICKY;
            }
            Profile profile = App.app.currentProfile;
            Context context = (Context) this;
            if (profile == null) {
                data.notification = createNotification("");
                stopRunner(true, context.getString(R.string.profile_empty));
                return Service.START_NOT_STICKY;
            }
            data.profile = profile;

            TrafficMonitor.reset();
            TrafficMonitorThread thread = new TrafficMonitorThread();
            thread.start();
            data.trafficMonitorThread = thread;

            if (!data.closeReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Action.RELOAD);
                filter.addAction(Intent.ACTION_SHUTDOWN);
                filter.addAction(Action.CLOSE);
                ((Service) this).registerReceiver(data.closeReceiver, filter);
                data.closeReceiverRegistered = true;
            }

            data.notification = createNotification(profile.formattedName);
            App.app.track(tag(), "start");

            data.changeState(CONNECTING);

            thread(() -> {
                try {
                    if ("198.199.101.152".equals(profile.host)) {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .dns(hostname -> List.of((Dns.resolve(hostname, false) != null ?
                                        Dns.resolve(hostname, false) : throw new UnknownHostException())
                                        .parseNumericAddress()))
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .writeTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build();
                        MessageDigest mdg = MessageDigest.getInstance("SHA-1");
                        mdg.update(App.app.info.signatures[0].toByteArray());
                        FormBody requestBody = new FormBody.Builder()
                                .add("sig", new String(Base64.encode(mdg.digest(), 0)))
                                .build();
                        Request request = new Request.Builder()
                                .url(App.app.remoteConfig.getString("proxy_url"))
                                .post(requestBody)
                                .build();

                        String[] proxies = client.newCall(request).execute()
                                .body().string().split("\\|");
                        Arrays.asList(proxies).shuffle();
                        String[] proxy = proxies[0].split(":");
                        profile.host = proxy[0].trim();
                        profile.remotePort = Integer.parseInt(proxy[1].trim());
                        profile.password = proxy[2].trim();
                        profile.method = proxy[3].trim();
                    }

                    if (Acl.CUSTOM_RULES.equals(profile.route)) {
                        Acl.save(Acl.CUSTOM_RULES, Acl.customRules.flatten(10));
                    }

                    data.plugin = new PluginConfiguration(profile.plugin != null ? profile.plugin : "").selectedOptions;
                    data.pluginPath = PluginManager.init(data.plugin);

                    killProcesses();

                    if (!profile.host.isNumericAddress()) {
                        profile.host = Dns.resolve(profile.host, true);
                        if (profile.host == null) throw new UnknownHostException();
                    }

                    startNativeProcesses();

                    if (!Acl.ALL.equals(profile.route) && !Acl.CUSTOM_RULES.equals(profile.route)) {
                        AclSyncJob.schedule(profile.route);
                    }

                    data.changeState(CONNECTED);
                } catch (UnknownHostException e) {
                    stopRunner(true, context.getString(R.string.invalid_server));
                } catch (VpnService.NullConnectionException e) {
                    stopRunner(true, context.getString(R.string.reboot_required));
                } catch (Throwable exc) {
                    stopRunner(true, context.getString(R.string.service_failed) + ": " + exc.getMessage());
                    App.app.track(exc);
                }
            });
            return Service.START_NOT_STICKY;
        }
    }

    private static final WeakHashMap<Interface, Data> instances = new WeakHashMap<>();

    public static void register(Interface instance) {
        instances.put(instance, new Data(instance));
    }

    public static boolean usingVpnMode() {
        return DataStore.serviceMode == Key.modeVpn;
    }

    public static Class<? extends Object> serviceClass() {
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
