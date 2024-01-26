package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
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
import java.util.*;

public class BaseService {

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
        public GuardedProcessPool processes = null;
        public ProxyInstance proxy = null;
        public ProxyInstance udpFallback = null;

        public ServiceNotification notification = null;
        public final broadcastReceiver closeReceiver = broadcastReceiver((context, intent) -> {
            switch (intent.getAction()) {
                case Action.RELOAD:
                    service.forceLoad();
                    break;
                default:
                    service.stopRunner();
                    break;
            }
        });
        public boolean closeReceiverRegistered = false;

        public final Binder binder = new Binder(this);
        public Job connectingJob = null;

        public Data(Interface service) {
            this.service = service;
            RemoteConfig.fetch();
        }
    }

    public static class Binder extends IShadowsocksService.Stub implements AutoCloseable {
        public final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
        private final Set<IBinder> bandwidthListeners = new HashSet<>();
        private final Handler handler = new Handler();

        private final Data data;

        public Binder(Data data) {
            this.data = data;
        }

        @Override
        public int getState() throws RemoteException {
            return data.state;
        }

        @Override
        public String getProfileName() throws RemoteException {
            return data.proxy != null ? data.proxy.profile.name : "Idle";
        }

        @Override
        public void registerCallback(IShadowsocksServiceCallback cb) throws RemoteException {
            callbacks.register(cb);
        }

        private void broadcast(Callback<IShadowsocksServiceCallback> work) {
            int count = callbacks.beginBroadcast();
            for (int i = 0; i < count; i++) {
                try {
                    work.apply(callbacks.getBroadcastItem(i));
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
            List<ProxyInstance> proxies = Arrays.asList(data.proxy, data.udpFallback);
            List<Triple<Long, TrafficStats, Boolean>> statsList = proxies.stream()
                    .map(proxy -> new Triple<>(proxy.profile.id, proxy.trafficMonitor.requestUpdate(), proxy.trafficMonitor != null))
                    .filter(triple -> triple.second != null)
                    .collect(Collectors.toList());
            if (statsList.stream().anyMatch(triple -> triple.third) && data.state == CONNECTED && !bandwidthListeners.isEmpty()) {
                TrafficStats sum = statsList.stream().map(Triple::getSecond).reduce(TrafficStats::plus).orElse(new TrafficStats());
                broadcast(item -> {
                    if (bandwidthListeners.contains(item.asBinder())) {
                        statsList.forEach(stats -> item.trafficUpdated(stats.getFirst(), stats.getSecond()));
                        item.trafficUpdated(0, sum);
                    }
                });
            }
            registerTimeout();
        }

        @Override
        public void startListeningForBandwidth(IShadowsocksServiceCallback cb) throws RemoteException {
            boolean wasEmpty = bandwidthListeners.isEmpty();
            if (bandwidthListeners.add(cb.asBinder())) {
                if (wasEmpty) registerTimeout();
                if (data.state != CONNECTED) return;
                TrafficStats sum = new TrafficStats();
                if (data.proxy != null) {
                    TrafficStats stats = data.proxy.trafficMonitor.out;
                    cb.trafficUpdated(data.proxy.profile.id, stats == null ? sum : sum.plus(stats));
                }
                if (data.udpFallback != null) {
                    TrafficStats stats = data.udpFallback.trafficMonitor.out;
                    cb.trafficUpdated(data.udpFallback.profile.id, stats == null ? new TrafficStats() : sum.plus(stats));
                }
                cb.trafficUpdated(0, sum);
            }
        }

        @Override
        public void stopListeningForBandwidth(IShadowsocksServiceCallback cb) throws RemoteException {
            if (bandwidthListeners.remove(cb.asBinder()) && bandwidthListeners.isEmpty()) {
                handler.removeCallbacksAndMessages(null);
            }
        }

        @Override
        public void unregisterCallback(IShadowsocksServiceCallback cb) throws RemoteException {
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
        }
    }

        interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? = if (intent.action == Action.SERVICE) data.binder else null

        fun forceLoad() {
            val (profile, fallback) = Core.currentProfile
                    ?: return stopRunner(false, (this as Context).getString(R.string.profile_empty))
            if (profile.host.isEmpty() || profile.password.isEmpty() ||
                    fallback != null && (fallback.host.isEmpty() || fallback.password.isEmpty())) {
                stopRunner(false, (this as Context).getString(R.string.proxy_empty))
                return
            }
            val s = data.state
            when (s) {
                STOPPED -> startRunner()
                CONNECTED -> stopRunner(true)
                else -> Crashlytics.log(Log.WARN, tag, "Illegal state when invoking use: $s")
            }
        }

        fun buildAdditionalArguments(cmd: ArrayList<String>): ArrayList<String> = cmd

        suspend fun startProcesses() {
            val configRoot = (if (Build.VERSION.SDK_INT < 24 || app.getSystemService<UserManager>()
                            ?.isUserUnlocked != false) app else Core.deviceStorage).noBackupFilesDir
            val udpFallback = data.udpFallback
            data.proxy!!.start(this,
                    File(Core.deviceStorage.noBackupFilesDir, "stat_main"),
                    File(configRoot, CONFIG_FILE),
                    if (udpFallback == null) "-u" else null)
            check(udpFallback?.pluginPath == null)
            udpFallback?.start(this,
                    File(Core.deviceStorage.noBackupFilesDir, "stat_udp"),
                    File(configRoot, CONFIG_FILE_UDP),
                    "-U")
        }

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, javaClass))
            else startService(Intent(this, javaClass))
        }

        suspend fun killProcesses() {
            data.processes?.run {
                close()
                data.processes = null
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            if (data.state == STOPPING) return
            
            data.changeState(STOPPING)
            GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED) {
                Core.analytics.logEvent("stop", bundleOf(Pair(FirebaseAnalytics.Param.METHOD, tag)))

                killProcesses()

                
                this@Interface as Service
                val data = data
                if (data.closeReceiverRegistered) {
                    unregisterReceiver(data.closeReceiver)
                    data.closeReceiverRegistered = false
                }

                data.notification?.destroy()
                data.notification = null

                val ids = listOfNotNull(data.proxy, data.udpFallback).map {
                    it.close()
                    it.profile.id
                }
                data.proxy = null
                data.binder.trafficPersisted(ids)

                
                data.changeState(STOPPED, msg)

                
                if (restart) startRunner() else stopSelf()
            }
        }

        suspend fun preInit() { }
        suspend fun resolver(host: String) = InetAddress.getAllByName(host)

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val data = data
            if (data.state != STOPPED) return Service.START_NOT_STICKY
            val profilePair = Core.currentProfile
            this as Context
            if (profilePair == null) {
                
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }
            val (profile, fallback) = profilePair
            profile.name = profile.formattedName    
            val proxy = ProxyInstance(profile)
            data.proxy = proxy
            if (fallback != null) data.udpFallback = ProxyInstance(fallback, profile.route)

            if (!data.closeReceiverRegistered) {
                registerReceiver(data.closeReceiver, IntentFilter().apply {
                    addAction(Action.RELOAD)
                    addAction(Intent.ACTION_SHUTDOWN)
                    addAction(Action.CLOSE)
                })
                data.closeReceiverRegistered = true
            }

            data.notification = createNotification(profile.formattedName)
            Core.analytics.logEvent("start", bundleOf(Pair(FirebaseAnalytics.Param.METHOD, tag)))

            data.changeState(CONNECTING)
            data.connectingJob = GlobalScope.launch(Dispatchers.Main) {
                try {
                    killProcesses()
                    preInit()
                    proxy.init(this@Interface::resolver)
                    data.udpFallback?.init(this@Interface::resolver)

                    data.processes = GuardedProcessPool {
                        printLog(it)
                        data.connectingJob?.apply { runBlocking { cancelAndJoin() } }
                        stopRunner(false, it.localizedMessage)
                    }
                    startProcesses()

                    proxy.scheduleUpdate()
                    data.udpFallback?.scheduleUpdate()
                    RemoteConfig.fetch()

                    data.changeState(CONNECTED)
                } catch (_: UnknownHostException) {
                    stopRunner(false, getString(R.string.invalid_server))
                } catch (exc: Throwable) {
                    if (exc !is PluginManager.PluginNotFoundException && exc !is VpnService.NullConnectionException) {
                        printLog(exc)
                    }
                    stopRunner(false, "${getString(R.string.service_failed)}: ${exc.localizedMessage}")
                } finally {
                    data.connectingJob = null
                }
            }
            return Service.START_NOT_STICKY
        }
    }
}