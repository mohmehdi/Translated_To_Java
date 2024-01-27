package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
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

public class BaseService extends Service {
    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;






public class Data {
    private Interface service;
    private volatile Profile profile;
    private volatile int state = STOPPED;
    private PluginOptions plugin = new PluginOptions();
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
            if (action.equals(Action.RELOAD)) {
                service.forceLoad();
            } else {
                Toast.makeText(service, R.string.stopping, Toast.LENGTH_SHORT).show();
                service.stopRunner(true);
            }
        }
    };
    private boolean closeReceiverRegistered;

    public Data(Interface service) {
        this.service = service;
    }

    public int state() {
        return state;
    }

    public IShadowsocksService.Stub binder = new IShadowsocksService.Stub() {
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
            callbacks.register(cb);
        }

        @Override
        public void startListeningForBandwidth(IShadowsocksServiceCallback cb) {
            IBinder binder = cb.asBinder();
            if (bandwidthListeners.add(binder)) {
                if (timer == null) {
                    Timer t = new Timer(true);
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (state == CONNECTED && TrafficMonitor.updateRate()) {
                                app.handler.post(new Runnable() {
                                    @Override
                                    public void run() {
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
                                                } catch (RemoteException e) {
                                                    // ignore
                                                }
                                            }
                                            callbacks.finishBroadcast();
                                        }
                                    }
                                });
                            }
                        }
                    }, 1000, 1000);
                    timer = t;
                }
                TrafficMonitor.updateRate();
                if (state == CONNECTED) {
                    try {
                        cb.trafficUpdated(profile.id, TrafficMonitor.txRate, TrafficMonitor.rxRate, TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
                    } catch (RemoteException e) {
                        // ignore
                    }
                }
            }
        }

        @Override
        public void stopListeningForBandwidth(IShadowsocksServiceCallback cb) {
            IBinder binder = cb.asBinder();
            if (bandwidthListeners.remove(binder) && bandwidthListeners.isEmpty()) {
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
        if (profile == null) {
            return;
        }
        Profile p = ProfileManager.getProfile(profile.id);
        if (p == null) {
            return;
        }
        p.tx += tx;
        p.rx += rx;
        ProfileManager.updateProfile(p);
        app.handler.post(new Runnable() {
            @Override
            public void run() {
                int n = callbacks.beginBroadcast();
                for (int i = 0; i < n; i++) {
                    try {
                        IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                        if (bandwidthListeners.contains(item.asBinder())) {
                            item.trafficPersisted(p.id);
                        }
                    } catch (RemoteException e) {
                        // ignore
                    }
                }
                callbacks.finishBroadcast();
            }
        });
    }

    public void buildShadowsocksConfig() {
        if (profile == null) {
            return;
        }
        Profile p = ProfileManager.getProfile(profile.id);
        if (p == null) {
            return;
        }
        JSONObject config = new JSONObject();
        try {
            config.put("server", p.host);
            config.put("server_port", p.remotePort);
            config.put("password", p.password);
            config.put("method", p.method);
            String pluginPath = pluginPath;
            if (pluginPath != null) {
                ArrayList<String> pluginCmd = new ArrayList<>();
                pluginCmd.add(pluginPath);
                if (TcpFastOpen.sendEnabled) {
                    pluginCmd.add("--fast-open");
                }
                String additionalArguments = service.buildAdditionalArguments(pluginCmd);
                config.put("plugin", Commandline.toString(additionalArguments));
                config.put("plugin_opts", plugin.toString());
            }
            File file = new File(app.filesDir, "shadowsocks.json");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(config.toString());
            writer.close();
        } catch (JSONException | IOException e) {
            // handle exception
        }
    }
}





public interface Interface {
    String tag = "ShadowsocksService";

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
        if (p == null) {
            return stopRunner(true, getString(R.string.profile_empty));
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
                Log.w(tag, "Illegal state when invoking use: " + s);
        }
    }

    default ArrayList<String> buildAdditionalArguments(List<String> cmd) {
        return new ArrayList<>(cmd);
    }

    default void startNativeProcesses() throws IOException {
        Data data = this.data;
        data.buildShadowsocksConfig();
        List<String> cmd = buildAdditionalArguments(Arrays.asList(
                new File(getApplicationInfo().nativeLibraryDir, Executable.SS_LOCAL).getAbsolutePath(),
                "-u",
                "-b", "127.0.0.1",
                "-l", Integer.toString(DataStore.portProxy),
                "-t", "600",
                "-c", "shadowsocks.json"));

        String route = data.profile.route;
        if (!route.equals(Acl.ALL)) {
            cmd.add("--acl");
            cmd.add(Acl.getFile(route == Acl.CUSTOM_RULES ? Acl.CUSTOM_RULES_FLATTENED : route).getAbsolutePath());
        }

        if (TcpFastOpen.sendEnabled) {
            cmd.add("--fast-open");
        }

        data.sslocalProcess = new GuardedProcess(cmd).start();
    }

    Notification createNotification();

    default void startRunner() {
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(new Intent(this, getClass()));
        } else {
            startService(new Intent(this, getClass()));
        }
    }

    default void killProcesses() {
        Data data = this.data;
        if (data.sslocalProcess != null) {
            data.sslocalProcess.destroy();
            data.sslocalProcess = null;
        }
    }

    default void stopRunner(boolean stopService, String msg) {
        // change the state
        changeState(STOPPING);

        app.track(tag, "stop");

        killProcesses();

        // clean up recevier
        Data data = this.data;
        if (data.closeReceiverRegistered) {
            unregisterReceiver(data.closeReceiver);
            data.closeReceiverRegistered = false;
        }

        data.notification.destroy();
        data.notification = null;

        // Make sure update total traffic when stopping the runner
        data.updateTrafficTotal(TrafficStats.getTotalTxBytes(), TrafficStats.getTotalRxBytes());

        TrafficMonitor.reset();
        data.trafficMonitorThread.stopThread();
        data.trafficMonitorThread = null;

        // change the state
        changeState(STOPPED, msg);

        // stop the service if nothing has bound to it
        if (stopService) {
            stopSelf();
        }

        data.profile = null;
    }

    default Data data() {
        return instances.get(this);
    }

    int onStartCommand(Intent intent, int flags, int startId);

    default void changeState(int s, String msg) {
        Data data = instances.get(this);
        if (data.state == s && msg == null) {
            return;
        }
        if (data.callbacks.getRegisteredCallbackCount() > 0) {
            app.handler.post(() -> {
                int n = data.callbacks.beginBroadcast();
                for (int i = 0; i < n; i++) {
                    try {
                        data.callbacks.getBroadcastItem(i).stateChanged(s, data.binder.getProfileName(), msg);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                data.callbacks.finishBroadcast();
            });
        }
        data.state = s;
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