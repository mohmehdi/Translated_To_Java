package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.util.Log;

import androidx.core.os.bundleOf;

import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.aidl.TrafficStats;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.GuardedProcessPool;
import com.github.shadowsocks.utils.ServiceNotification;
import com.github.shadowsocks.utils.printLog;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BaseService extends Service {

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static final String CONFIG_FILE = "shadowsocks.conf";
    public static final String CONFIG_FILE_UDP = "shadowsocks-udp.conf";

    public static class Data {

        private final Interface service;
        public int state = STOPPED;
        public GuardedProcessPool processes;
        public ProxyInstance proxy;
        public ProxyInstance udpFallback;
        public ServiceNotification notification;
        public final ServiceNotificationBinder binder;
        public boolean closeReceiverRegistered = false;
        public final IBinder closeReceiver = broadcastReceiver((context, intent) -> {
            switch (intent.getAction()) {
                case Action.RELOAD:
                    service.forceLoad();
                    break;
                default:
                    service.stopRunner();
                    break;
            }
        });

        public Job connectingJob;

        public Data(Interface service) {
            this.service = service;
            RemoteConfig.fetch();
            binder = new ServiceNotificationBinder(this);
        }

        public void changeState(int s, String msg) {
            if (state == s && msg == null) return;
            binder.stateChanged(s, msg);
            state = s;
        }
    }

    public static class ServiceNotificationBinder extends IShadowsocksService.Stub implements AutoCloseable {

        private Data data;
        private final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
        private final HashSet<IBinder> bandwidthListeners = new HashSet<>();
        private final Handler handler = new Handler();

        public ServiceNotificationBinder(Data data) {
            this.data = data;
        }

        @Override
        public int getState() {
            return data.state;
        }

        @Override
        public String getProfileName() {
            return data.proxy != null ? data.proxy.profile.name : "Idle";
        }

        @Override
        public void registerCallback(IShadowsocksServiceCallback cb) {
            callbacks.register(cb);
        }

        private void broadcast(Work work) {
            int count = callbacks.beginBroadcast();
            for (int i = 0; i < count; i++) {
                try {
                    work.execute(callbacks.getBroadcastItem(i));
                } catch (Exception e) {
                    printLog(e);
                }
            }
            callbacks.finishBroadcast();
        }

        private void registerTimeout() {
            handler.postDelayed(this::onTimeout, 1000);
        }

        private void onTimeout() {
            List<ProxyInstance> proxies = List.of(data.proxy, data.udpFallback);
            List<TrafficStats> statsList = new ArrayList<>();
            for (ProxyInstance proxy : proxies) {
                if (proxy != null) {
                    TrafficStats stats = proxy.trafficMonitor != null ? proxy.trafficMonitor.requestUpdate() : null;
                    if (stats != null) {
                        statsList.add(stats);
                    }
                }
            }
            if (!statsList.isEmpty() && data.state == CONNECTED && !bandwidthListeners.isEmpty()) {
                TrafficStats sum = new TrafficStats();
                for (TrafficStats stats : statsList) {
                    sum = sum.add(stats);
                }
                broadcast(item -> {
                    if (bandwidthListeners.contains(item.asBinder())) {
                        for (TrafficStats stats : statsList) {
                            item.trafficUpdated(stats.getId(), stats);
                        }
                        item.trafficUpdated(0, sum);
                    }
                });
            }
            registerTimeout();
        }

        @Override
        public void startListeningForBandwidth(IShadowsocksServiceCallback cb) {
            boolean wasEmpty = bandwidthListeners.isEmpty();
            if (bandwidthListeners.add(cb.asBinder())) {
                if (wasEmpty) {
                    registerTimeout();
                }
                if (data.state != CONNECTED) {
                    return;
                }
                TrafficStats sum = new TrafficStats();
                if (data.proxy != null) {
                    TrafficStats stats = data.proxy.trafficMonitor != null ? data.proxy.trafficMonitor.out : null;
                    if (stats != null) {
                        sum = sum.add(stats);
                        cb.trafficUpdated(data.proxy.profile.id, stats);
                    }
                }
                if (data.udpFallback != null) {
                    TrafficStats stats = data.udpFallback.trafficMonitor != null ? data.udpFallback.trafficMonitor.out : new TrafficStats();
                    sum = sum.add(stats);
                    cb.trafficUpdated(data.udpFallback.profile.id, stats);
                }
                cb.trafficUpdated(0, sum);
            }
        }

        @Override
        public void stopListeningForBandwidth(IShadowsocksServiceCallback cb) {
            if (bandwidthListeners.remove(cb.asBinder()) && bandwidthListeners.isEmpty()) {
                handler.removeCallbacksAndMessages(null);
            }
        }

        @Override
        public void unregisterCallback(IShadowsocksServiceCallback cb) {
            stopListeningForBandwidth(cb);
            callbacks.unregister(cb);
        }

        public void stateChanged(int s, String msg) {
            String profileName = getProfileName();
            broadcast(item -> item.stateChanged(s, profileName, msg));
        }

        public void trafficPersisted(List<Long> ids) {
            if (!bandwidthListeners.isEmpty() && !ids.isEmpty()) {
                broadcast(item -> {
                    if (bandwidthListeners.contains(item.asBinder())) {
                        for (Long id : ids) {
                            item.trafficPersisted(id);
                        }
                    }
                });
            }
        }

        @Override
        public void close() {
            callbacks.kill();
            handler.removeCallbacksAndMessages(null);
            data = null;
        }
    }
    public interface Interface {
        Data getData();
        String getTag();
        ServiceNotification createNotification(String profileName);

        IBinder onBind(Intent intent) {
            return intent.getAction().equals(Action.SERVICE) ? getData().binder : null;
        }

        void forceLoad() {
            Pair<Profile, Profile> profilePair = Core.currentProfile;
            if (profilePair == null) {
                getData().notification = createNotification("");
                stopRunner(false, getString(R.string.profile_empty));
                return;
            }
            Profile profile = profilePair.getFirst();
            Profile fallback = profilePair.getSecond();
            profile.name = profile.formattedName;

            if (profile.host.isEmpty() || profile.password.isEmpty() ||
                    (fallback != null && (fallback.host.isEmpty() || fallback.password.isEmpty()))) {
                stopRunner(false, getString(R.string.proxy_empty));
                return;
            }

            int state = getData().state;
            switch (state) {
                case STOPPED:
                    startRunner();
                    break;
                case CONNECTED:
                    stopRunner(true);
                    break;
                default:
                    Crashlytics.log(Log.WARN, getTag(), "Illegal state when invoking use: " + state);
            }
        }

        ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
            return cmd;
        }

        void startProcesses() {
            File configRoot = (Build.VERSION.SDK_INT < 24 || app.getSystemService(UserManager.class)
                    ?.isUserUnlocked() != false) ? app : Core.deviceStorage;
            File udpFallback = getData().udpFallback;
            ProxyInstance proxy = getData().proxy;

            proxy.start(this,
                    new File(Core.deviceStorage.noBackupFilesDir, "stat_main"),
                    new File(configRoot, CONFIG_FILE),
                    (udpFallback == null) ? "-u" : null);
            check(udpFallback == null || udpFallback.pluginPath == null);

            if (udpFallback != null) {
                udpFallback.start(this,
                        new File(Core.deviceStorage.noBackupFilesDir, "stat_udp"),
                        new File(configRoot, CONFIG_FILE_UDP),
                        "-U");
            }
        }

        void startRunner() {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(new Intent(this, getClass()));
            } else {
                startService(new Intent(this, getClass()));
            }
        }

        void killProcesses(CoroutineScope scope) {
            GuardedProcessPool processes = getData().processes;
            if (processes != null) {
                processes.close(scope);
                getData().processes = null;
            }
        }

        void stopRunner(boolean restart, String msg) {
            Data data = getData();
            if (data.state == STOPPING) return;

            data.changeState(STOPPING);

            GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, () -> {
                Core.analytics.logEvent("stop", bundleOf(new Pair<>(FirebaseAnalytics.Param.METHOD, getTag())));

                unregisterReceiver(data.closeReceiver);
                data.closeReceiverRegistered = false;

                data.notification.destroy();
                data.notification = null;

                List<ProxyInstance> proxies = List.of(data.proxy, data.udpFallback);
                List<Long> ids = new ArrayList<>();

                for (ProxyInstance proxy : proxies) {
                    if (proxy != null) {
                        proxy.shutdown(this);
                        ids.add(proxy.profile.id);
                    }
                }

                data.proxy = null;
                data.udpFallback = null;
                data.binder.trafficPersisted(ids);

                data.changeState(STOPPED, msg);

                if (restart) {
                    startRunner();
                } else {
                    stopSelf();
                }
            });
        }

        void preInit() throws Exception { }

        InetAddress[] resolver(String host) throws UnknownHostException {
            return InetAddress.getAllByName(host);
        }

        int onStartCommand(Intent intent, int flags, int startId) {
            Data data = getData();
            if (data.state != STOPPED) {
                return Service.START_NOT_STICKY;
            }

            Pair<Profile, Profile> profilePair = Core.currentProfile;
            if (profilePair == null) {
                data.notification = createNotification("");
                stopRunner(false, getString(R.string.profile_empty));
                return Service.START_NOT_STICKY;
            }

            Profile profile = profilePair.getFirst();
            Profile fallback = profilePair.getSecond();
            profile.name = profile.formattedName;

            ProxyInstance proxy = new ProxyInstance(profile);
            data.proxy = proxy;
            data.udpFallback = (fallback == null) ? null : new ProxyInstance(fallback, profile.route);

            if (!data.closeReceiverRegistered) {
                registerReceiver(data.closeReceiver, new IntentFilter(Action.RELOAD));
                registerReceiver(data.closeReceiver, new IntentFilter(Intent.ACTION_SHUTDOWN));
                registerReceiver(data.closeReceiver, new IntentFilter(Action.CLOSE));
                data.closeReceiverRegistered = true;
            }

            data.notification = createNotification(profile.formattedName);
            Core.analytics.logEvent("start", bundleOf(new Pair<>(FirebaseAnalytics.Param.METHOD, getTag())));

            data.changeState(CONNECTING);
            data.connectingJob = GlobalScope.launch(Dispatchers.Main, () -> {
                try {
                    Executable.killAll();
                    preInit();
                    proxy.init(this::resolver);
                    if (data.udpFallback != null) {
                        data.udpFallback.init(this::resolver);
                    }

                    data.processes = new GuardedProcessPool(it -> {
                        printLog(it);
                        data.connectingJob.cancelAndJoin();
                        stopRunner(false, it.getLocalizedMessage());
                    });
                    startProcesses();

                    proxy.scheduleUpdate();
                    if (data.udpFallback != null) {
                        data.udpFallback.scheduleUpdate();
                    }
                    RemoteConfig.fetch();

                    data.changeState(CONNECTED);
                } catch (CancellationException ignored) {
                } catch (UnknownHostException e) {
                    stopRunner(false, getString(R.string.invalid_server));
                } catch (Throwable exc) {
                    if (!(exc instanceof PluginManager.PluginNotFoundException) && !(exc instanceof VpnService.NullConnectionException)) {
                        printLog(exc);
                    }
                    stopRunner(false, getString(R.string.service_failed) + ": " + exc.getLocalizedMessage());
                } finally {
                    data.connectingJob = null;
                }
            });

            return Service.START_NOT_STICKY;
        }
    }
}