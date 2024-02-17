

package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Dns;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.MessageDigest;
import android.os.Messenger;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Toast;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.shadowsocks.R;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.AclSyncJob;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.plugin.PluginConfiguration;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Executable;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.TrafficMonitorThread;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BaseService {

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static class Data {
        private final Interface service;
        private volatile Profile profile;
        private volatile int state = STOPPED;
        private volatile PluginOptions plugin = new PluginOptions();
        private volatile String pluginPath;
        private GuardedProcess sslocalProcess;

        private Timer timer;
        private TrafficMonitorThread trafficMonitorThread;

        private RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
        private Set<IBinder> bandwidthListeners = new HashSet<>();

        private ServiceNotification notification;
        private BroadcastReceiver closeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

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
        private boolean closeReceiverRegistered = false;

        public Data(Interface service) {
            this.service = service;
        }

        public int state() {
            return state;
        }

        public IBinder getBinder() {
            return new IShadowsocksService.Stub() {
                @Override
                public int getState() {
                    return state();
                }

                @Override
                public String getProfileName() {
                    return (profile != null) ? profile.formattedName : "Idle";
                }

                @Override
                public void registerCallback(IShadowsocksServiceCallback cb) {
                    synchronized (callbacks) {
                        callbacks.register(cb);
                    }
                    if (bandwidthListeners.add(cb.asBinder())) {
                        if (timer == null) {
                            Timer t = new Timer(true);
                            t.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    if (state == CONNECTED && TrafficMonitor.updateRate()) {
                                        service.handler.post(() -> {
                                            if (!bandwidthListeners.isEmpty()) {
                                                int txRate = TrafficMonitor.txRate;
                                                int rxRate = TrafficMonitor.rxRate;
                                                long txTotal = TrafficMonitor.txTotal;
                                                long rxTotal = TrafficMonitor.rxTotal;
                                                int n = callbacks.beginBroadcast();
                                                for (int i = 0; i < n; i++) {
                                                    try {
                                                        IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                                                        if (bandwidthListeners.contains(item.asBinder())) {
                                                            item.trafficUpdated(profile.id, txRate, rxRate, txTotal, rxTotal);
                                                        }
                                                    } catch (Exception e) {
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
                            cb.trafficUpdated(profile.id, TrafficMonitor.txRate, TrafficMonitor.rxRate, TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
                        }
                    }
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
                                        service.handler.post(() -> {
                                            if (!bandwidthListeners.isEmpty()) {
                                                int txRate = TrafficMonitor.txRate;
                                                int rxRate = TrafficMonitor.rxRate;
                                                long txTotal = TrafficMonitor.txTotal;
                                                long rxTotal = TrafficMonitor.rxTotal;
                                                int n = callbacks.beginBroadcast();
                                                for (int i = 0; i < n; i++) {
                                                    try {
                                                        IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                                                        if (bandwidthListeners.contains(item.asBinder())) {
                                                            item.trafficUpdated(profile.id, txRate, rxRate, txTotal, rxTotal);
                                                        }
                                                    } catch (Exception e) {
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
                            cb.trafficUpdated(profile.id, TrafficMonitor.txRate, TrafficMonitor.rxRate, TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
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
                    synchronized (callbacks) {
                        callbacks.unregister(cb);
                    }
                }
            };
        }

        public void updateTrafficTotal(long tx, long rx) {
            Profile p = profile;
            if (p == null) return;
            p = ProfileManager.getProfile(p.id);
            if (p == null) return;
            p.tx += tx;
            p.rx += rx;
            ProfileManager.updateProfile(p);
            service.handler.post(() -> {
                if (!bandwidthListeners.isEmpty()) {
                    int n = callbacks.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        try {
                            IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                            if (bandwidthListeners.contains(item.asBinder())) {
                                item.trafficPersisted(p.id);
                            }
                        } catch (Exception e) {
                        }
                    }
                    callbacks.finishBroadcast();
                }
            });
        }

        public void buildShadowsocksConfig() {
            Profile profile = this.profile;
            if (profile == null) return;
            JSONObject config = new JSONObject();
            try {
                config.put("server", profile.host);
                config.put("server_port", profile.remotePort);
                config.put("password", profile.password);
                config.put("method", profile.method);

                String pluginPath = pluginPath;
                if (pluginPath != null) {
                    List<String> pluginCmd = new ArrayList<>(Arrays.asList(pluginPath));
                    if (TcpFastOpen.sendEnabled) pluginCmd.add("--fast-open");
                    config.put("plugin", String.join(" ", service.buildAdditionalArguments(pluginCmd)));
                    config.put("plugin_opts", plugin.toString());
                }

                File configFile = new File(service.getApplicationContext().getFilesDir(), "shadowsocks.json");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                    writer.write(config.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public interface Interface {
        String tag = "BaseService";

        IBinder onBind(Intent intent);

        boolean checkProfile(Profile profile);

        void forceLoad();

        List<String> buildAdditionalArguments(List<String> cmd);

        void startNativeProcesses();

        ServiceNotification createNotification();

        void startRunner();

        void killProcesses();

        void stopRunner(boolean stopService, String msg);
        
        int onStartCommand(@Nullable Intent intent, int flags, int startId);

        default int onStartCommand(Intent intent, int flags, int startId) {
            // val data = data
            if (intent.getIntExtra("state", -1) != STOPPED) return START_NOT_STICKY;
            Profile profile = app.getCurrentProfile();
            if (profile == null) {
                stopRunner(true);
                return START_NOT_STICKY;
            }
            intent.putExtra("profile", profile);
            profile.name = profile.formattedName;

            TrafficMonitor.reset();
            TrafficMonitorThread thread = new TrafficMonitorThread();
            thread.start();
            intent.putExtra("trafficMonitorThread", thread);

            if (!intent.getBooleanExtra("closeReceiverRegistered", false)) {
                // register close receiver
                IntentFilter filter = new IntentFilter();
                filter.addAction(Action.RELOAD);
                filter.addAction(Intent.ACTION_SHUTDOWN);
                filter.addAction(Action.CLOSE);
                Objects.requireNonNull(getContext()).registerReceiver(intent.getParcelableExtra("closeReceiver"), filter);
                intent.putExtra("closeReceiverRegistered", true);
            }

            intent.putExtra("notification", createNotification());
            app.track(tag, "start");

            changeState(CONNECTING);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                Context context = getContext();
                try {
                    if (profile.host.equals("198.199.101.152")) {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .dns(dns -> {
                                    List<String> addresses = Dns.resolve(dns, false);
                                    if (addresses == null) throw new UnknownHostException();
                                    return Arrays.asList(Dns.parseNumericAddress(addresses.get(0)));
                                })
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .writeTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build();
                        MessageDigest mdg = MessageDigest.getInstance("SHA-1");
                        mdg.update(app.info.signatures[0].getBytes());
                        RequestBody requestBody = new FormBody.Builder()
                                .add("sig", Base64.encodeToString(mdg.digest(), 0))
                                .build();
                        Request request = new Request.Builder()
                                .url(app.remoteConfig.getString("proxy_url"))
                                .post(requestBody)
                                .build();

                        Response proxies = client.newCall(request).execute();
                        List<String> proxyList = Arrays.asList(proxies.body().string().split("\\|"));
                        Collections.shuffle(proxyList);
                        String[] proxy = proxyList.get(0).split(":");
                        profile.host = proxy[0].trim();
                        profile.remotePort = Integer.parseInt(proxy[1].trim());
                        profile.password = proxy[2].trim();
                        profile.method = proxy[3].trim();
                    }

                    if (profile.route == Acl.CUSTOM_RULES)
                        Acl.save(Acl.CUSTOM_RULES_FLATTENED, Acl.customRules.flatten(10));

                    intent.putExtra("plugin", new Gson().toJsonTree(profile.plugin == null ? new PluginConfiguration("").selectedOptions : profile.plugin.selectedOptions));
                    intent.putExtra("pluginPath", PluginManager.init(intent.getStringExtra("plugin")));

                    // Clean up
                    killProcesses();

                    if (!TextUtils.isNumeric(profile.host))
                        profile.host = Dns.resolve(profile.host, true);

                    startNativeProcesses();

                    if (profile.route != Acl.ALL && profile.route != Acl.CUSTOM_RULES)
                        AclSyncJob.schedule(profile.route);

                    changeState(CONNECTED);
                } catch (UnknownHostException e) {
                    stopRunner(true, context.getString(R.string.invalid_server));
                } catch (VpnService.NullConnectionException e) {
                    stopRunner(true, context.getString(R.string.reboot_required));
                } catch (Throwable e) {
                    stopRunner(true, String.format(Locale.ROOT, "%s: %s", context.getString(R.string.service_failed), e.getMessage()));
                    app.track(e);
                }
            });
            return START_NOT_STICKY;
        }

        void changeState(int s, @Nullable String msg);
    }

    private static final Map<Interface, Data> instances = new WeakHashMap<>();

    public static void register(Interface instance) {
        instances.put(instance, new Data(instance));
    }



}