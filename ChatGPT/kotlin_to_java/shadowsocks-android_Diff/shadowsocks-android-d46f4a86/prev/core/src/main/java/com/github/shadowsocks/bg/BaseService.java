package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteCallbackList;
import androidx.core.content.ContextCompat;
import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.aidl.TrafficStats;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.plugin.PluginManager;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.broadcastReceiver;
import com.github.shadowsocks.utils.printLog;
import com.google.firebase.analytics.FirebaseAnalytics;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
    public final broadcastReceiver closeReceiver = new broadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
          case Action.RELOAD:
            service.forceLoad();
            break;
          default:
            service.stopRunner();
            break;
        }
      }
    };
    public boolean closeReceiverRegistered = false;
    public final Binder binder;
    public Job connectingJob;

    public Data(Interface service) {
      this.service = service;
      RemoteConfig.fetch();
    }
  }

  public class Binder
    extends IShadowsocksService.Stub
    implements AutoCloseable {

    public final RemoteCallbackList<IShadowsocksServiceCallback> callbacks = new RemoteCallbackList<>();
    private final HashSet<IBinder> bandwidthListeners = new HashSet<>();
    private final Handler handler = new Handler();

    public int getState() {
      return data.state;
    }

    public String getProfileName() {
      return data.proxy != null ? data.proxy.profile.name : "Idle";
    }

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
      List<ProxyInstance> proxies = List.of(data.proxy, data.udpFallback);
      List<TrafficStats> stats = proxies
        .stream()
        .map(proxy ->
          new AbstractMap.SimpleEntry<>(
            proxy.profile.id,
            proxy.trafficMonitor.requestUpdate()
          )
        )
        .filter(entry -> entry.getValue() != null)
        .map(entry ->
          new AbstractMap.SimpleEntry<>(
            entry.getKey(),
            entry.getValue().getKey()
          )
        )
        .filter(entry -> entry.getValue() != null)
        .map(entry ->
          new TrafficStats(entry.getValue().first, entry.getValue().second)
        )
        .collect(Collectors.toList());
      if (
        stats.stream().anyMatch(stat -> stat != null) &&
        data.state == CONNECTED &&
        !bandwidthListeners.isEmpty()
      ) {
        TrafficStats sum = stats
          .stream()
          .reduce(new TrafficStats(), TrafficStats::add);
        broadcast(item -> {
          if (bandwidthListeners.contains(item.asBinder())) {
            stats.forEach(entry ->
              item.trafficUpdated(entry.getKey(), entry.getValue())
            );
            item.trafficUpdated(0, sum);
          }
        });
      }
      registerTimeout();
    }

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
        if (proxy != null) {
          TrafficStats stats = proxy.trafficMonitor.out;
          cb.trafficUpdated(proxy.profile.id, stats != null ? stats : sum);
        }
        ProxyInstance udpFallback = data.udpFallback;
        if (udpFallback != null) {
          TrafficStats stats = udpFallback.trafficMonitor.out;
          cb.trafficUpdated(
            udpFallback.profile.id,
            stats != null ? stats : new TrafficStats()
          );
        }
        cb.trafficUpdated(0, sum);
      }
    }

    public void stopListeningForBandwidth(IShadowsocksServiceCallback cb) {
      if (
        bandwidthListeners.remove(cb.asBinder()) && bandwidthListeners.isEmpty()
      ) {
        handler.removeCallbacksAndMessages(null);
      }
    }

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

    public void close() {
      callbacks.kill();
      handler.removeCallbacksAndMessages(null);
      data = null;
    }
  }

  public interface Interface {
    Data data = new Data();
    String tag = "BaseService";

    ServiceNotification createNotification(String profileName);

    IBinder onBind(Intent intent) {
      return (intent.getAction().equals(Action.SERVICE)) ? data.binder : null;
    }

    default void forceLoad() {
      Pair<Profile, Profile> currentProfile = Core.currentProfile;
      if (currentProfile == null) {
        stopRunner(false, ((Context) this).getString(R.string.profile_empty));
        return;
      }
      Profile profile = currentProfile.first;
      Profile fallback = currentProfile.second;
      if (
        profile.host.isEmpty() ||
        profile.password.isEmpty() ||
        (
          fallback != null &&
          (fallback.host.isEmpty() || fallback.password.isEmpty())
        )
      ) {
        stopRunner(false, ((Context) this).getString(R.string.proxy_empty));
        return;
      }
      int state = data.state;
      if (state == STOPPED) {
        startRunner();
      } else if (state == CONNECTED) {
        stopRunner(true);
      } else {
        printLog(Log.WARN, tag, "Illegal state when invoking use: " + state);
      }
    }

    default ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
      return cmd;
    }

    default void startProcesses() {
      File configRoot = (
          Build.VERSION.SDK_INT < 24 ||
          Core.app.getSystemService(UserManager.class).isUserUnlocked()
        )
        ? Core.app
        : Core.deviceStorage;
      ProxyInstance udpFallback = data.udpFallback;
      data.proxy.start(
        this,
        new File(Core.deviceStorage.noBackupFilesDir, "stat_main"),
        new File(configRoot.noBackupFilesDir, CONFIG_FILE),
        (udpFallback == null) ? "-u" : null
      );
      assert udpFallback == null || udpFallback.pluginPath == null;
      if (udpFallback != null) {
        udpFallback.start(
          this,
          new File(Core.deviceStorage.noBackupFilesDir, "stat_udp"),
          new File(configRoot.noBackupFilesDir, CONFIG_FILE_UDP),
          "-U"
        );
      }
    }

    default void startRunner() {
      if (Build.VERSION.SDK_INT >= 26) {
        ((Context) this).startForegroundService(
            new Intent((Context) this, getClass())
          );
      } else {
        ((Context) this).startService(new Intent((Context) this, getClass()));
      }
    }

    default void killProcesses() {
      if (data.processes != null) {
        data.processes.close();
        data.processes = null;
      }
    }

    default void stopRunner(boolean restart, String msg) {
      if (data.state == STOPPING) {
        return;
      }
      data.changeState(STOPPING);
      GlobalScope.launch(
        Dispatchers.Main,
        CoroutineStart.UNDISPATCHED,
        () -> {
          Core.analytics.logEvent(
            "stop",
            bundleOf(Pair.create(FirebaseAnalytics.Param.METHOD, tag))
          );

          killProcesses();

          ((Service) this).data = null;
          ServiceNotification notification = data.notification;
          if (notification != null) {
            notification.destroy();
            data.notification = null;
          }

          List<Long> ids = List
            .of(data.proxy, data.udpFallback)
            .stream()
            .filter(Objects::nonNull)
            .peek(ProxyInstance::close)
            .map(ProxyInstance::getProfile)
            .map(Profile::getId)
            .collect(Collectors.toList());
          data.proxy = null;
          data.binder.trafficPersisted(ids);

          data.changeState(STOPPED, msg);

          if (restart) {
            startRunner();
          } else {
            ((Service) this).stopSelf();
          }
        }
      );
    }

    default void preInit() throws UnknownHostException {}

    default InetAddress resolver(String host) throws UnknownHostException {
      return InetAddress.getByName(host);
    }

    default int onStartCommand(Intent intent, int flags, int startId) {
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
      ((Context) this).data = data;
      if (fallback != null) {
        data.udpFallback = new ProxyInstance(fallback, profile.route);
      }

      if (!data.closeReceiverRegistered) {
        ((Service) this).registerReceiver(
            data.closeReceiver,
            new IntentFilter()
              .addAction(Action.RELOAD)
              .addAction(Intent.ACTION_SHUTDOWN)
              .addAction(Action.CLOSE)
          );
        data.closeReceiverRegistered = true;
      }

      data.notification = createNotification(profile.formattedName);
      Core.analytics.logEvent(
        "start",
        bundleOf(Pair.create(FirebaseAnalytics.Param.METHOD, tag))
      );

      data.changeState(CONNECTING);
      data.connectingJob =
        GlobalScope.launch(
          Dispatchers.Main,
          () -> {
            try {
              killProcesses();
              preInit();
              data.proxy.init(this::resolver);
              ProxyInstance udpFallback = data.udpFallback;
              if (udpFallback != null) {
                udpFallback.init(this::resolver);
              }

              data.processes =
                new GuardedProcessPool(it -> {
                  printLog(it);
                  Job connectingJob = data.connectingJob;
                  if (connectingJob != null) {
                    GlobalScope.launch(
                      Dispatchers.Main,
                      () -> {
                        try {
                          connectingJob.cancelAndJoin();
                        } finally {
                          stopRunner(false, it.getLocalizedMessage());
                        }
                      }
                    );
                  }
                });
              startProcesses();

              data.proxy.scheduleUpdate();
              if (udpFallback != null) {
                udpFallback.scheduleUpdate();
              }
              RemoteConfig.fetch();

              data.changeState(CONNECTED);
            } catch (UnknownHostException ignored) {
              stopRunner(
                false,
                ((Context) this).getString(R.string.invalid_server)
              );
            } catch (Throwable exc) {
              if (
                !(exc instanceof PluginManager.PluginNotFoundException) &&
                !(exc instanceof VpnService.NullConnectionException)
              ) {
                printLog(exc);
              }
              stopRunner(
                false,
                ((Context) this).getString(R.string.service_failed) +
                ": " +
                exc.getLocalizedMessage()
              );
            } finally {
              data.connectingJob = null;
            }
          }
        );
      return Service.START_NOT_STICKY;
    }
  }
}
