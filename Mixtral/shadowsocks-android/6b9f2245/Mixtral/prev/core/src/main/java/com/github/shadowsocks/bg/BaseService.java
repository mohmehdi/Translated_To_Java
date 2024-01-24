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

    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
          switch (action) {
            case Action.RELOAD:
              service.forceLoad();
              break;
            default:
              service.stopRunner();
          }
        }
      }
    };
    private boolean closeReceiverRegistered = false;
    private final Binder binder = new Binder(this);
    private Job connectingJob;

    public Data(Interface service) {
      this.service = service;
      RemoteConfig.fetch();
    }

    public void changeState(int s, String msg) {
      if (state == s && msg == null) {
        return;
      }
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
      return data.state;
    }

    @Override
    public String getProfileName() {
      return (data.proxy != null && data.proxy.profile != null)
        ? data.proxy.profile.name
        : "Idle";
    }

    @Override
    public void registerCallback(IShadowsocksServiceCallback cb) {
      callbacks.register(cb);
    }

    private void broadcast(Work work) {
      final int N = callbacks.beginBroadcast();
      for (int i = 0; i < N; i++) {
        try {
          work.apply(callbacks.getBroadcastItem(i));
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
      List<Object> proxies = List
        .ofNullable(data.proxy)
        .stream()
        .filter(Objects::nonNull)
        .toList();
      proxies.add(0, data.udpFallback);

      List<Triple<Long, Long, Long>> stats = proxies
        .stream()
        .filter(Objects::nonNull)
        .map(proxy ->
          new Pair<>(proxy.profile.id, proxy.trafficMonitor.requestUpdate())
        )
        .filter(pair -> pair.second != null)
        .map(pair ->
          new Triple<>(pair.first, pair.second.first, pair.second.second)
        )
        .toList();

      if (
        !stats.isEmpty() && state == CONNECTED && !bandwidthListeners.isEmpty()
      ) {
        TrafficStats sum = stats
          .stream()
          .filter(Triple::third)
          .reduce(new TrafficStats(), this::add);

        broadcast(item -> {
          if (bandwidthListeners.contains(item.asBinder())) {
            for (Triple<Long, Long, Long> stat : stats) {
              item.trafficUpdated(stat.getFirst(), stat.getSecond());
            }
            item.trafficUpdated(0, sum);
          }
          return null;
        });
      }

      registerTimeout();
    }

    @Override
    public void startListeningForBandwidth(IShadowsocksServiceCallback cb) {
      if (bandwidthListeners.add(cb.asBinder())) {
        if (bandwidthListeners.size() == 1) {
          registerTimeout();
        }
        if (state != CONNECTED) {
          return;
        }
        TrafficStats sum = new TrafficStats();
        Object proxy = data.proxy;
        if (proxy != null) {
          sum = add(sum, proxy.trafficMonitor.out);
        }

        Object udpFallback = data.udpFallback;
        if (udpFallback != null) {
          sum = add(sum, udpFallback.trafficMonitor.out);
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

    private void stateChanged(int s, String msg) {
      String profileName = getProfileName();
      broadcast(item -> item.stateChanged(s, profileName, msg));
    }

    private void trafficPersisted(List<Long> ids) {
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

    public Interface(Data data, String tag) {
      this.data = data;
      this.tag = tag;
    }

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
      Core.CurrentProfile currentProfile = Core.currentProfile;
      if (currentProfile == null) {
        stopRunner(false, ((Context) this).getString(R.string.profile_empty));
        return;
      }

      Profile profile = currentProfile.profile;
      Profile fallback = currentProfile.fallback;

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

      String s = data.state;
      switch (s) {
        case STOPPED:
          startRunner();
          break;
        case CONNECTED:
          stopRunner(true);
          break;
        default:
          Crashlytics.log(
            Log.WARN,
            tag,
            "Illegal state when invoking use: " + s
          );
      }
    }

    public ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
      return cmd;
    }

    @Nullable
    public File getConfigRoot() {
      if (
        Build.VERSION.SDK_INT < 24 ||
        ContextCompat.getSystemService(app, UserManager.class) != null &&
        !(
          (UserManager) ContextCompat.getSystemService(app, UserManager.class)
        ).isUserUnlocked()
      ) {
        return app.getNoBackupFilesDir();
      }
      return Core.deviceStorage.getNoBackupFilesDir();
    }

    public void startProcesses() {
      File configRoot = getConfigRoot();
      UdpFallback udpFallback = data.udpFallback;

      data.proxy.start(
        this,
        new File(Core.deviceStorage.getNoBackupFilesDir(), "stat_main"),
        new File(configRoot, CONFIG_FILE),
        udpFallback == null ? "-u" : null
      );

      if (udpFallback != null) {
        check(udpFallback.pluginPath == null);
        udpFallback.start(
          this,
          new File(Core.deviceStorage.getNoBackupFilesDir(), "stat_udp"),
          new File(configRoot, CONFIG_FILE_UDP),
          "-U"
        );
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
      CopyOnWriteArrayList<Process> processes = data.processes;
      if (processes != null) {
        processes.close();
        data.processes = null;
      }
    }

    public void stopRunner(boolean restart, String msg) {
      if (data.state == STOPPING) {
        return;
      }

      // change the state
      data.changeState(STOPPING);

      GlobalScope.launch(
        new CoroutineStart.UNDISPATCHED() {
          @Override
          public void invoke(
            @NonNull CoroutineContext context,
            @NonNull CoroutineStart initialization
          ) {
            Core.analytics.logEvent(
              "stop",
              new Bundle().putString(FirebaseAnalytics.Param.METHOD, tag)
            );

            Interface thisInterface = Interface.this;
            CoroutineScope coroutineScope = new CoroutineScope() {
              @Override
              public <R> Job launch(Continuation<R> block) {
                return super.launch(block);
              }

              @Override
              public <R> Job launch(
                CoroutineContext context,
                Continuation<R> block
              ) {
                return super.launch(context, block);
              }

              @Override
              public CoroutineContext getCoroutineContext() {
                return super.getCoroutineContext();
              }

              @Override
              public void cancelChildren() {
                super.cancelChildren();
              }

              @Override
              public void cancel(
                CoroutineContext context,
                boolean mayInterruptIfRunning
              ) {
                super.cancel(context, mayInterruptIfRunning);
              }

              @Override
              public void cancel(boolean mayInterruptIfRunning) {
                super.cancel(mayInterruptIfRunning);
              }

              @Override
              public void cancel() {
                super.cancel();
              }
            };

            killProcesses(coroutineScope);

            if (data.closeReceiverRegistered) {
              thisInterface.unregisterReceiver(data.closeReceiver);
              data.closeReceiverRegistered = false;
            }

            data.notification.destroy();
            data.notification = null;

            List<Integer> ids = new ArrayList<>();
            if (data.proxy != null) {
              ids.add(data.proxy.profile.id);
              data.proxy.close();
              data.proxy = null;
            }
            if (data.udpFallback != null) {
              ids.add(data.udpFallback.profile.id);
              data.udpFallback.close();
              data.udpFallback = null;
            }

            data.binder.trafficPersisted(ids);

            // change the state
            data.changeState(STOPPED, msg);

            // stop the service if nothing has bound to it
            if (restart) {
              startRunner();
            } else {
              stopSelf();
            }
          }
        }
      );
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStartCommand(Intent intent, int flags, int startId) {
      Data data = this.data;
      if (!data.state.equals(STOPPED)) {
        return;
      }

      Core.CurrentProfile currentProfile = Core.currentProfile;
      if (currentProfile == null) {
        data.notification = createNotification("");
        stopRunner(false, getString(R.string.profile_empty));
        return;
      }

      Profile profile = currentProfile.profile;
      Profile fallback = currentProfile.fallback;

      profile.name = profile.formattedName; // save name for later queries
      ProxyInstance proxy = new ProxyInstance(profile);
      data.proxy = proxy;

      if (fallback == null) {
        data.udpFallback = null;
      } else {
        data.udpFallback = new ProxyInstance(fallback, profile.route);
      }

      if (!data.closeReceiverRegistered) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Action.RELOAD);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Action.CLOSE);
        registerReceiver(data.closeReceiver, filter);
        data.closeReceiverRegistered = true;
      }

      data.notification = createNotification(profile.formattedName);
      Core.analytics.logEvent(
        "start",
        new Bundle().putString(FirebaseAnalytics.Param.METHOD, tag)
      );

      data.changeState(CONNECTING);
      GlobalScope.launch(
        Dispatchers.Main,
        new CoroutineStart.UNDISPATCHED() {
          @Override
          public void invoke(
            @NonNull CoroutineContext context,
            @NonNull CoroutineStart initialization
          ) {
            try {
              Executable.killAll(); // clean up old processes
              preInit();
              proxy.init(Interface.this::resolver);
              if (data.udpFallback != null) {
                data.udpFallback.init(Interface.this::resolver);
              }

              data.processes =
                new GuardedProcessPool() {
                  @Override
                  public void printLog(String it) {
                    super.printLog(it);
                    Interface.this.stopRunner(false, it);
                  }
                };
              startProcesses();

              proxy.scheduleUpdate();
              if (data.udpFallback != null) {
                data.udpFallback.scheduleUpdate();
              }
              RemoteConfig.fetch();

              data.changeState(CONNECTED);
            } catch (CancellationException e) {
              // if the job was cancelled, it is canceller's responsibility to call stopRunner
            } catch (UnknownHostException e) {
              stopRunner(false, getString(R.string.invalid_server));
            } catch (Throwable exc) {
              if (
                !(
                  exc instanceof PluginManager.PluginNotFoundException ||
                  exc instanceof VpnService.NullConnectionException
                )
              ) {
                printLog(exc);
              }
              stopRunner(false, "Service failed: " + exc.getLocalizedMessage());
            }
          }
        }
      );
    }
    // Other methods like preInit(), resolver(), etc. should be implemented here
  }
}
