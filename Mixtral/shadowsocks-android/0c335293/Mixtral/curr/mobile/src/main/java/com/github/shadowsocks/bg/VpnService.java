package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import com.github.shadowsocks.App;
import com.github.shadowsocks.JniHelper;
import com.github.shadowsocks.R;
import com.github.shadowsocks.VpnRequestActivity;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Subnet;
import com.github.shadowsocks.utils.parseNumericAddress;
import java.io.File;
import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

public class VpnService extends BaseVpnService implements LocalDnsService.Interface {
private static final String TAG = "ShadowsocksVpnService";
private static final int VPN_MTU = 1500;
private static final String PRIVATE_VLAN = "26.26.26.%s";
private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";
private static Method getInt;


static {
    try {
        getInt = FileDescriptor.class.getDeclaredMethod("getInt$", new Class[0]);
        getInt.setAccessible(true);
    } catch (NoSuchMethodException e) {
        e.printStackTrace();
    }
}

private class ProtectWorker extends LocalSocketListener {
    private File socketFile;

    public ProtectWorker() {
        super("ShadowsocksVpnThread");
        this.socketFile = new File(App.getApp().getDeviceContext().getFilesDir(), "protect_path");
    }

    @Override
    public void accept(LocalSocket socket) {
        try {
            socket.getInputStream().read();
            FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
            if (fds.length == 0) return;
            int fd = getInt.invoke(fds[0]).intValue();
            boolean ret = protect(fd);
            JniHelper.close(fd);
            socket.getOutputStream().write(ret ? (byte) 0 : (byte) 1);
        } catch (Exception e) {
            Log.e(TAG, "Error when protect socket", e);
            App.getApp().track(e);
        }
    }
}

public class NullConnectionException extends NullPointerException {
}

public VpnService() {
    BaseService.register(this);
}

@Override
public String getTag() {
    return TAG;
}

@Override
public ServiceNotification createNotification(String profileName) {
    return new ServiceNotification(this, profileName, "service-vpn");
}

private ParcelFileDescriptor conn;
private ProtectWorker worker;
private GuardedProcess tun2socksProcess;

@Override
public IBinder onBind(Intent intent) {
    if (intent.getAction().equals(SERVICE_INTERFACE)) {
        return super.onBind(intent);
    } else {
        return super.onBind(intent);
    }
}

@Override
public void onRevoke() {
    stopRunner(true);
}

@Override
public void killProcesses() {
    if (worker != null) {
        worker.stopThread();
    }
    worker = null;
    super.killProcesses();
    if (tun2socksProcess != null) {
        tun2socksProcess.destroy();
    }
    tun2socksProcess = null;
    if (conn != null) {
        conn.close();
    }
    conn = null;
}

@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    if (BaseService.usingVpnMode) {
        Intent prepareIntent = BaseVpnService.prepare(this);
        if (prepareIntent != null) {
            startActivity(new Intent(this, VpnRequestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return super.onStartCommand(intent, flags, startId);
        }
    }
    stopRunner(true);
    return Service.START_NOT_STICKY;
}

@Override
public void startNativeProcesses() {
    ProtectWorker worker = new ProtectWorker();
    worker.start();
    this.worker = worker;

    super.startNativeProcesses();

    int fd = startVpn();
    if (!sendFd(fd)) {
        throw new RuntimeException("sendFd failed");
    }
}

@Override
public ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
    cmd.add("-V");
    return cmd;
}

private int startVpn() {
    Profile profile = data.profile;
    Builder builder = new Builder()
            .setSession(profile.formattedName)
            .setMtu(VPN_MTU);

    String[] remoteDnsList = profile.remoteDns.split(",");
    for (String remoteDns : remoteDnsList) {
        builder.addDnsServer(remoteDns.trim());
    }

    if (profile.ipv6) {
        builder.addAddress(PRIVATE_VLAN6.formatted(Locale.ENGLISH, "1"), 126);
        builder.addRoute("::", 0);
    }

    if (profile.proxyApps) {
        String me = getPackageName();
        String[] individualList = profile.individual.split("\n");
        for (String individual : individualList) {
            try {
                if (profile.bypass) {
                    builder.addDisallowedApplication(individual);
                } else {
                    builder.addAllowedApplication(individual);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Invalid package name", e);
            }
        }
        if (!profile.bypass) {
            builder.addAllowedApplication(me);
        }
    }

    int routeType = profile.route.getValue();
    switch (routeType) {
        case Acl.ALL:
        case Acl.BYPASS_CHN:
        case Acl.CUSTOM_RULES:
            builder.addRoute("0.0.0.0", 0);
            break;
        default:
            String[] bypassPrivateRouteList = resources.getStringArray(R.array.bypass_private_route);
            for (String bypassPrivateRoute : bypassPrivateRouteList) {
                Subnet subnet = Subnet.fromString(bypassPrivateRoute);
                if (subnet != null) {
                    builder.addRoute(subnet.address.getHostAddress(), subnet.prefixSize);
                }
            }
            for (String remoteDns : remoteDnsList) {
                String trimmedRemoteDns = remoteDns.trim();
                if (!trimmedRemoteDns.isEmpty()) {
                    builder.addRoute(trimmedRemoteDns.parseNumericAddress().getAddress().getHostAddress(), trimmedRemoteDns.parseNumericAddress().getAddress().getSize() << 3);
                }
            }
            break;
    }

    ParcelFileDescriptor conn = builder.establish();
    if (conn == null) {
        throw new NullConnectionException();
    }
    this.conn = conn;
    int fd = conn.getFd();

    ArrayList<String> cmd = new ArrayList<>();
    cmd.add(new File(applicationInfo.nativeLibraryDir, Executable.TUN2SOCKS).getAbsolutePath());
    cmd.add("--netif-ipaddr");
    cmd.add(PRIVATE_VLAN.formatted(Locale.ENGLISH, "2"));
    cmd.add("--netif-netmask");
    cmd.add("255.255.255.0");
    cmd.add("--socks-server-addr");
    cmd.add("127.0.0.1:" + DataStore.portProxy);
    cmd.add("--tunfd");
    cmd.add(Integer.toString(fd));
    cmd.add("--tunmtu");
    cmd.add(Integer.toString(VPN_MTU));
    cmd.add("--sock-path");
    cmd.add("sock_path");
    cmd.add("--loglevel");
    cmd.add("3");
    if (profile.ipv6) {
        cmd.add("--netif-ip6addr");
        cmd.add(PRIVATE_VLAN6.formatted(Locale.ENGLISH, "2"));
    }
    cmd.add("--enable-udprelay");
    if (!profile.udpdns) {
        cmd.add("--dnsgw");
        cmd.add("127.0.0.1:" + DataStore.portLocalDns);
    }
    tun2socksProcess = new GuardedProcess(cmd).start(new Runnable() {
        @Override
        public void run() {
            sendFd(fd);
        }
    });
    return fd;
}

private boolean sendFd(int fd) {
    if (fd != -1) {
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(30L << i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                return JniHelper.sendFd(fd, new File(App.getApp().getDeviceContext().getFilesDir(), "sock_path").getAbsolutePath()) != -1;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    return false;
}
}