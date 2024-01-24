package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.InetAddress;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.Core.app;
import com.github.shadowsocks.R;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.core.ProxyInstance;
import com.github.shadowsocks.core.RemoteConfig;
import com.github.shadowsocks.core.TrafficStats;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.broadcastReceiver;
import com.github.shadowsocks.utils.printLog;
import com.google.firebase.analytics.FirebaseAnalytics;
import java.io.File;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BaseService {

  public static final int IDLE = 0;
  public static final int CONNECTING = 1;
  public static final int CONNECTED = 2;
  public static final int STOPPING = 3;
  public static final int STOPPED = 4;

  public static final String CONFIG_FILE = "shadowsocks.conf";
  public static final String CONFIG_FILE_UDP = "shadowsocks-udp.conf";

  public class Data {

    private final Interface service;
    private int state = STOPPED;
    private GuardedProcessPool processes;
    private ProxyInstance proxy;
    private ProxyInstance udpFallback;
    private ServiceNotification notification;
    private BroadcastReceiver closeReceiver;
    private boolean closeReceiverRegistered;
    private Binder binder;
    private Job connectingJob;

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

  class Binder implements IShadowsocksService.Stub, AutoCloseable {

    private Data data;
    private final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
    private final HashSet<IBinder> bandwidthListeners = new HashSet<>();
    private final Handler handler = new Handler();

    @Override
    public int getState() {
      return Objects.requireNonNull(data).state;
    }

    @Override
    public String getProfileName() {
      Profile profile = Objects.requireNonNull(data).proxy != null
        ? Objects.requireNonNull(data.proxy).profile
        : null;
      return profile != null ? profile.name : "Idle";
    }

    @Override
    public void registerCallback(IShadowsocksServiceCallback cb) {
      callbacks.register(cb);
    }

    private void broadcast(Work work) {
      final int N = callbacks.beginBroadcast();
      for (int i = 0; i < N; i++) {
        IShadowsocksServiceCallback cb = callbacks.getBroadcastItem(i);
        try {
          work.apply(cb);
        } catch (RemoteException e) {
          printLog(e);
        }
      }
      callbacks.finishBroadcast();
    }

    private void registerTimeout() {
      handler.postDelayed(this::onTimeout, 1000);
    }

    private void onTimeout() {
      Proxy[] proxies = new Proxy[] {
        Objects.requireNonNull(data).proxy,
        data.udpFallback,
      };
      List<Pair<Long, Pair<Long, Long>>> stats = Arrays
        .stream(proxies)
        .filter(proxy -> proxy != null)
        .map(proxy ->
          new Pair<>(
            proxy.profile.id,
            proxy.trafficMonitor != null
              ? proxy.trafficMonitor.requestUpdate()
              : null
          )
        )
        .filter(pair -> pair.second != null)
        .map(pair ->
          new Triple<>(pair.first, pair.second.first, pair.second.second)
        )
        .collect(Collectors.toList());

      if (
        !stats.isEmpty() &&
        getState() == CONNECTED &&
        !bandwidthListeners.isEmpty()
      ) {
        TrafficStats sum = stats
          .stream()
          .filter(triple -> triple.third)
          .map(Triple::second)
          .reduce(new TrafficStats(), TrafficStats::plus);
        broadcast(item -> {
          if (bandwidthListeners.contains(item.asBinder())) {
            stats.forEach(triple ->
              item.trafficUpdated(triple.first, triple.second)
            );
            item.trafficUpdated(0, sum);
          }
          return null;
        });
      }
      registerTimeout();
    }

    @Override
    public void startListeningForBandwidth(IShadowsocksServiceCallback cb) {
      boolean wasEmpty = bandwidthListeners.isEmpty();
      if (bandwidthListeners.add(cb.asBinder())) {
        if (wasEmpty) registerTimeout();
        int state = getState();
        if (state != CONNECTED) return;
        TrafficStats sum = new TrafficStats();
        Proxy proxy = Objects.requireNonNull(data).proxy;
        if (proxy != null) {
          sum =
            proxy.trafficMonitor.out != null
              ? sum.plus(proxy.trafficMonitor.out)
              : sum;
          cb.trafficUpdated(proxy.profile.id, sum);
        }
        Proxy udpFallback = data.udpFallback;
        if (udpFallback != null) {
          sum =
            udpFallback.trafficMonitor.out != null
              ? sum.plus(udpFallback.trafficMonitor.out)
              : sum;
          cb.trafficUpdated(udpFallback.profile.id, sum);
        }
        cb.trafficUpdated(0, sum);
      }
    }

    @Override
    public void stopListeningForBandwidth(IShadowsocksServiceCallback cb) {
      if (
        bandwidthListeners.remove(cb.asBinder()) && bandwidthListeners.isEmpty()
      ) {
        handler.removeCallbacksAndMessages(null);
      }
    }

    @Override
    public void unregisterCallback(IShadowsocksServiceCallback cb) {
      stopListeningForBandwidth(cb);
      callbacks.unregister(cb);
    }

    void stateChanged(int s, String msg) {
      String profileName = getProfileName();
      broadcast(item -> item.stateChanged(s, profileName, msg));
    }

    void trafficPersisted(List<Long> ids) {
      if (!bandwidthListeners.isEmpty() && !ids.isEmpty()) {
        broadcast(item -> {
          if (bandwidthListeners.contains(item.asBinder())) {
            ids.forEach(id -> item.trafficPersisted(id));
          }
          return null;
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
    public Data data;
    public String tag;

    public ServiceNotification createNotification(String profileName) {
        // Implementation here
    }

    public IBinder onBind(Intent intent) {
        if (intent.getAction().equals(Action.SERVICE)) {
            return data.binder;
        }
        return null;
    }

    public void forceLoad() {
        Core.Profile profile = Core.currentProfile.orElse(null);
        if (profile == null) {
            stopRunner(false, (Context) this, R.string.profile_empty);
            return;
        }

        String host = profile.host;
        String password = profile.password;
        Core.Fallback fallback = profile.fallback;

        if (host.isEmpty() || password.isEmpty() ||
                (fallback != null && (fallback.host.isEmpty() || fallback.password.isEmpty()))) {
            stopRunner(false, (Context) this, R.string.proxy_empty);
            return;
        }

        int state = data.state;
        switch (state) {
            case STOPPED:
                startRunner();
                break;
            case CONNECTED:
                stopRunner(true);
                break;
            default:
                Crashlytics.log(Log.WARN, tag, "Illegal state when invoking use: " + state);
        }
    }

    public ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
        return cmd;
    }

    public void startProcesses(CoroutineScope scope) {
        File configRoot = (Build.VERSION.SDK_INT < 24 || UserManagerCompat.isUserUnlocked(
                (Context) this)) ? app.getNoBackupFilesDir() : Core.deviceStorage.getNoBackupFilesDir();
        Core.Fallback udpFallback = data.udpFallback;

        try {
            data.proxy.start(this,
                    new File(Core.deviceStorage.getNoBackupFilesDir(), "stat_main"),
                    new File(configRoot, CONFIG_FILE),
                    udpFallback == null ? "-u" : null);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        if (udpFallback != null) {
            try {
                udpFallback.start(this,
                        new File(Core.deviceStorage.getNoBackupFilesDir(), "stat_udp"),
                        new File(configRoot, CONFIG_FILE_UDP),
                        "-U");
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startRunner() {
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(new Intent(this, Interface.class));
        } else {
            startService(new Intent(this, Interface.class));
        }
    }

    public void killProcesses(CoroutineScope scope) {
        if (data.processes != null) {
            data.processes.close(scope);
            data.processes = null;
        }
    }

    public void stopRunner(boolean restart, String msg) {
        if (data.state == STOPPING) {
            return;
        }

        data.changeState(STOPPING);

        GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED) {
            Core.analytics.logEvent("stop", bundleOf(Pair.create(FirebaseAnalytics.Param.METHOD, tag)));
            Interface thisInterface = this;

            CoroutineScope coroutineScope = new CoroutineScope() {
                @Override
                public CoroutineContext getCoroutineContext() {
                    return super.getCoroutineContext();
                }
            };

            killProcesses(coroutineScope);

            if (data.closeReceiverRegistered) {
                unregisterReceiver(data.closeReceiver);
                data.closeReceiverRegistered = false;
            }

            data.notification.destroy();
            data.notification = null;

            List<Integer> ids = new ArrayList<>();
            if (data.proxy != null) {
                ids.add(data.proxy.shutdown(thisInterface));
                ids.add(data.proxy.profile.id);
                data.proxy = null;
            }

            if (data.udpFallback != null) {
                ids.add(data.udpFallback.shutdown(thisInterface));
                ids.add(data.udpFallback.profile.id);
                data.udpFallback = null;
            }

            data.binder.trafficPersisted(ids);

            data.changeState(STOPPED, msg);

            if (restart) {
                startRunner();
            } else {
                stopSelf();
            }
        };
    }

    suspend void preInit() {
    }

    @NonNull
    List<InetAddress> resolver(String host) throws UnknownHostException {
        return Collections.singletonList(InetAddress.getAllByName(host));
    }

    int onStartCommand(Intent intent, int flags, int startId) {
        Data data = this.data;
        if (data.state != STOPPED) {
            return Service.START_NOT_STICKY;
        }

        Core.Profile profilePair = Core.currentProfile.orElse(null);
        if (profilePair == null) {
            data.notification = createNotification("");
            stopRunner(false, (Context) this, R.string.profile_empty);
            return Service.START_NOT_STICKY;
        }

        Core.Profile profile = profilePair;
        Core.Fallback fallback = profilePair.fallback;
        profile.formattedName = profile.formattedName;

        ProxyInstance proxy = new ProxyInstance(profile);
        data.proxy = proxy;

        ProxyInstance udpFallback = fallback == null ? null : new ProxyInstance(fallback, profile.route);
        data.udpFallback = udpFallback;

        if (!data.closeReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Action.RELOAD);
            filter.addAction(Intent.ACTION_SHUTDOWN);
            filter.addAction(Action.CLOSE);
            registerReceiver(data.closeReceiver, filter);
            data.closeReceiverRegistered = true;
        }

        data.notification = createNotification(profile.formattedName);
        Core.analytics.logEvent("start", bundleOf(Pair.create(FirebaseAnalytics.Param.METHOD, tag)));

        data.changeState(CONNECTING);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Executable.killAll();
                preInit();
                proxy.init(thisInterface -> resolver(thisInterface.host));
                if (udpFallback != null) {
                    udpFallback.init(thisInterface -> resolver(thisInterface.host));
                }

                data.processes = new GuardedProcessPool(process -> {
                    printLog(process);
                    data.connectingJob.cancel();
                    stopRunner(false, process.localizedMessage);
                });

                startProcesses(thisInterface);

                proxy.scheduleUpdate();
                if (udpFallback != null) {
                    udpFallback.scheduleUpdate();
                }
                RemoteConfig.fetch();

                data.changeState(CONNECTED);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return Service.START_NOT_STICKY;
    }
}

}
