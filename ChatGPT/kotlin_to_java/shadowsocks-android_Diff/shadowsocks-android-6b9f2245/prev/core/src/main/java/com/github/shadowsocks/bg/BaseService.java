package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.core.os.bundleOf;
import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.Core.app;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.aidl.TrafficStats;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.broadcastReceiver;
import com.github.shadowsocks.utils.printLog;
import com.google.firebase.analytics.FirebaseAnalytics;
import kotlinx.coroutines.*;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;

public class BaseService {

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int STOPPING = 3;
    public static final int STOPPED = 4;

    public static final String CONFIG_FILE = "shadowsocks.conf";
    public static final String CONFIG_FILE_UDP = "shadowsocks-udp.conf";

    public static class Data {
        private Interface service;
        public int state = STOPPED;
        public GuardedProcessPool processes;
        public ProxyInstance proxy;
        public ProxyInstance udpFallback;
        public ServiceNotification notification;
        public broadcastReceiver closeReceiver;
        public boolean closeReceiverRegistered;
        public Binder binder;
        public Job connectingJob;

        public Data(Interface service) {
            this.service = service;
            RemoteConfig.fetch();
        }

        public void changeState(int s, String msg) {
            if (state == s && msg == null) return;
            binder.stateChanged(s, msg);
            state = s;
        }
    }

    public static class Binder extends IShadowsocksService.Stub implements AutoCloseable {
        private Data data;
        public RemoteCallbackList<IShadowsocksServiceCallback> callbacks;
        private HashSet<IBinder> bandwidthListeners;
        private Handler handler;

        public Binder(Data data) {
            this.data = data;
            callbacks = new RemoteCallbackList<>();
            bandwidthListeners = new HashSet<>();
            handler = new Handler();
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

        private void broadcast(Consumer<IShadowsocksServiceCallback> work) {
            int count = callbacks.beginBroadcast();
            for (int i = 0; i < count; i++) {
                try {
                    work.accept(callbacks.getBroadcastItem(i));
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
            ProxyInstance[] proxies = {data.proxy, data.udpFallback};
            List<Triple<Long, TrafficStats, Boolean>> stats = new ArrayList<>();
            for (ProxyInstance proxy : proxies) {
                if (proxy != null) {
                    TrafficStats trafficStats = proxy.trafficMonitor != null ? proxy.trafficMonitor.requestUpdate() : null;
                    if (trafficStats != null) {
                        stats.add(Triple.of(proxy.profile.id, trafficStats, true));
                    }
                }
            }

            if (stats.stream().anyMatch(stat -> stat.third) && data.state == CONNECTED && !bandwidthListeners.isEmpty()) {
                TrafficStats sum = stats.stream().map(Triple::getSecond).reduce(TrafficStats::plus).orElse(new TrafficStats());
                broadcast(item -> {
                    if (bandwidthListeners.contains(item.asBinder())) {
                        stats.forEach(stat -> item.trafficUpdated(stat.getFirst(), stat.getSecond()));
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
                ProxyInstance proxy = data.proxy;
                if (proxy != null && proxy.trafficMonitor != null) {
                    TrafficStats stats = proxy.trafficMonitor.out;
                    if (stats != null) {
                        sum = sum.plus(stats);
                    }
                    cb.trafficUpdated(proxy.profile.id, stats != null ? stats : new TrafficStats());
                }

                ProxyInstance udpFallback = data.udpFallback;
                if (udpFallback != null && udpFallback.trafficMonitor != null) {
                    TrafficStats stats = udpFallback.trafficMonitor.out;
                    if (stats != null) {
                        sum = sum.plus(stats);
                    }
                    cb.trafficUpdated(udpFallback.profile.id, stats != null ? stats : new TrafficStats());
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
                        ids.forEach(item::trafficPersisted);
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

        default IBinder onBind(Intent intent) {
            return (intent.getAction() == Action.SERVICE) ? getData().binder : null;
        }

        default void forceLoad() {
            Pair<Profile, Profile> profilePair = Core.currentProfile;
            if (profilePair == null) {
                stopRunner(false, ((Context) this).getString(R.string.profile_empty));
                return;
            }

            Profile profile = profilePair.first;
            Profile fallback = profilePair.second;

            if (profile.host.isEmpty() || profile.password.isEmpty() ||
                    (fallback != null && (fallback.host.isEmpty() || fallback.password.isEmpty()))) {
                stopRunner(false, ((Context) this).getString(R.string.proxy_empty));
                return;
            }

            int currentState = getData().state;

            switch (currentState) {
                case STOPPED:
                    startRunner();
                    break;
                case CONNECTED:
                    stopRunner(true);
                    break;
                default:
                    Crashlytics.log(Log.WARN, getTag(), "Illegal state when invoking use: " + currentState);
            }
        }

        default ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
            return cmd;
        }

        default void startProcesses() {
            File configRoot = (Build.VERSION.SDK_INT < 24 || app.getSystemService(UserManager.class)
                            .isUserUnlocked() != false) ? app : Core.deviceStorage.noBackupFilesDir;

            ProxyInstance udpFallback = getData().udpFallback;
            getData().proxy.start(this,
                    new File(Core.deviceStorage.noBackupFilesDir, "stat_main"),
                    new File(configRoot, CONFIG_FILE),
                    (udpFallback == null) ? "-u" : null);
            assert udpFallback == null || udpFallback.pluginPath == null;
            if (udpFallback != null) {
                udpFallback.start(this,
                        new File(Core.deviceStorage.noBackupFilesDir, "stat_udp"),
                        new File(configRoot, CONFIG_FILE_UDP),
                        "-U");
            }
        }

        default void startRunner() {
            Context context = (Context) this;
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(new Intent(context, getClass()));
            } else {
                context.startService(new Intent(context, getClass()));
            }
        }

        default void killProcesses(CoroutineScope scope) {
            Data data = getData();
            if (data.processes != null) {
                data.processes.close(scope);
                data.processes = null;
            }
        }

        default void stopRunner(boolean restart, String msg) {
            Data data = getData();

            if (data.state == STOPPING) {
                return;
            }

            data.changeState(STOPPING);
            GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, () -> {
                Core.analytics.logEvent("stop", bundleOf(Pair.create(FirebaseAnalytics.Param.METHOD, getTag())));
                this.onStopRunner();

                data.notification.destroy();
                data.notification = null;

                List<Long> ids = Stream.of(data.proxy, data.udpFallback)
                        .filter(Objects::nonNull)
                        .peek(ProxyInstance::close)
                        .map(proxy -> {
                            data.proxy = null;
                            data.udpFallback = null;
                            return proxy.profile.id;
                        })
                        .collect(Collectors.toList());

                data.binder.trafficPersisted(ids);

                data.changeState(STOPPED, msg);

                if (restart) {
                    startRunner();
                } else {
                    stopSelf();
                }
            });
        }

        default void onStopRunner() {
            Data data = getData();
            if (data.closeReceiverRegistered) {
                ((Context) this).unregisterReceiver(data.closeReceiver);
                data.closeReceiverRegistered = false;
            }
        }

        default void preInit() throws Exception { }
        InetAddress[] resolver(String host) throws UnknownHostException;

        default int onStartCommand(Intent intent, int flags, int startId) {
            Data data = getData();

            if (data.state != STOPPED) {
                return Service.START_NOT_STICKY;
            }

            Pair<Profile, Profile> profilePair = Core.currentProfile;

            if (profilePair == null) {
                data.notification = createNotification("");
                stopRunner(false, ((Context) this).getString(R.string.profile_empty));
                return Service.START_NOT_STICKY;
            }

            Profile profile = profilePair.first;
            Profile fallback = profilePair.second;

            data.proxy = new ProxyInstance(profile);
            data.udpFallback = (fallback == null) ? null : new ProxyInstance(fallback, profile.route);

            if (!data.closeReceiverRegistered) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(Action.RELOAD);
                intentFilter.addAction(Intent.ACTION_SHUTDOWN);
                intentFilter.addAction(Action.CLOSE);
                ((Context) this).registerReceiver(data.closeReceiver, intentFilter);
                data.closeReceiverRegistered = true;
            }

            data.notification = createNotification(profile.formattedName);
            Core.analytics.logEvent("start", bundleOf(Pair.create(FirebaseAnalytics.Param.METHOD, getTag())));

            data.changeState(CONNECTING);
            data.connectingJob = GlobalScope.launch(Dispatchers.Main, () -> {
                try {
                    Executable.killAll();
                    preInit();
                    data.proxy.init(host -> resolver(host));
                    if (data.udpFallback != null) {
                        data.udpFallback.init(host -> resolver(host));
                    }

                    data.processes = new GuardedProcessPool((Exception e) -> {
                        printLog(e);
                        if (data.connectingJob != null) {
                            data.connectingJob.cancelAndJoin();
                        }
                        stopRunner(false, e.getLocalizedMessage());
                    });
                    startProcesses();

                    data.proxy.scheduleUpdate();
                    if (data.udpFallback != null) {
                        data.udpFallback.scheduleUpdate();
                    }
                    RemoteConfig.fetch();

                    data.changeState(CONNECTED);
                } catch (CancellationException ignored) {
                    // Ignored
                } catch (UnknownHostException e) {
                    stopRunner(false, ((Context) this).getString(R.string.invalid_server));
                } catch (Throwable exc) {
                    if (!(exc instanceof PluginManager.PluginNotFoundException) &&
                            !(exc instanceof VpnService.NullConnectionException)) {
                        printLog(exc);
                    }
                    stopRunner(false, ((Context) this).getString(R.string.service_failed) + ": " + exc.getLocalizedMessage());
                } finally {
                    data.connectingJob = null;
                }
            });
            return Service.START_NOT_STICKY;
        }
    }

}