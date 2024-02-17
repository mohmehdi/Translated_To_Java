

package com.github.shadowsocks.bg;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Dns;
import android.net.UnknownHostException;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
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
import okhttp3.RequestBody;
import org.json.JSONObject;
import java.io.File;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BaseService {
    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static final String CONFIG_FILE = "shadowsocks.conf";

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
        private HashSet<IBinder> bandwidthListeners = new HashSet<>(); 

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
        private boolean closeReceiverRegistered = false;

        private IBinder binder = new IShadowsocksService.Stub() {
            @Override
            public int getState() {
                return state;
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
                                    App.handler.post(() -> {
                                        if (!bandwidthListeners.isEmpty()) {
                                            int n = callbacks.beginBroadcast();
                                            for (int i = 0; i < n; i++) {
                                                try {
                                                    IShadowsocksServiceCallback item = callbacks.getBroadcastItem(i);
                                                    if (bandwidthListeners.contains(item.asBinder())) {
                                                        item.trafficUpdated(profile.id, TrafficMonitor.txRate, TrafficMonitor.rxRate, TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
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
                callbacks.unregister(cb);
            }
        };

        private File shadowsocksConfigFile;

        private void updateTrafficTotal(long tx, long rx) {
            Profile p = profile != null ? ProfileManager.getProfile(profile.id) : null;
            if (p != null) {
                p.tx += tx;
                p.rx += rx;
                ProfileManager.updateProfile(p);
                App.handler.post(() -> {
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
        }

        private File buildShadowsocksConfig() {
            Profile profile = this.profile;
            JSONObject config = new JSONObject()
                    .put("server", profile.host)
                    .put("server_port", profile.remotePort)
                    .put("password", profile.password)
                    .put("method", profile.method);
            String pluginPath = pluginPath;
            if (pluginPath != null) {
                List<String> pluginCmd = new ArrayList<>();
                pluginCmd.add(pluginPath);
                if (TcpFastOpen.sendEnabled) pluginCmd.add("--fast-open");
                config
                        .put("plugin", Commandline.toString(service.buildAdditionalArguments(pluginCmd)))
                        .put("plugin_opts", plugin.toString());
            }

            File file = new File(App.getAppContext().getFilesDir(), CONFIG_FILE);
            shadowsocksConfigFile = file;
            try {
                file.writeText(config.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return file;
        }

    

        private void changeState(int s, String msg) {
            if (state == s && msg == null) return;
            if (callbacks.getRegisteredCallbackCount() > 0) App.handler.post(() -> {
                int n = callbacks.beginBroadcast();
                for (int i = 0; i < n; i++) {
                    try {
                        callbacks.getBroadcastItem(i).stateChanged(s, binder.getProfileName(), msg);
                    } catch (Exception e) {
                    }
                }
                callbacks.finishBroadcast();
            });
            state = s;
        }
    }

    public interface Interface {
        String tag = "BaseService";

        IBinder onBind(Intent intent);

        boolean checkProfile(Profile profile);

        void forceLoad();

        ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd);

        void startNativeProcesses();

        ServiceNotification createNotification(String profileName);

        void startRunner();

        void killProcesses();

        void stopRunner(boolean stopService, String msg);

        Data getData();

        int onStartCommand(Intent intent, int flags, int startId);
    }

    private static final Map<Interface, Data> instances = new WeakHashMap<>();

    public static void register(Interface instance) {
        instances.put(instance, new Data(instance));
    }


    public static Class<? extends Service> serviceClass() {
        switch (DataStore.serviceMode) {
            case Key.modeProxy:
                return ProxyService.class;
            case Key.modeVpn:
                return VpnService.class;
            case Key.modeTransproxy:
                return TransproxyService.class;
            default:
                throw new IllegalStateException("Unexpected value: " + DataStore.serviceMode);
        }
    }
}