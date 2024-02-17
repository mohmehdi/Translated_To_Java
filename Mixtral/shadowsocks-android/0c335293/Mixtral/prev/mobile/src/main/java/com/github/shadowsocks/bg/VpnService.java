

package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
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
import android.net.VpnService as BaseVpnService;

class VpnService extends BaseVpnService implements LocalDnsService.Interface {
    private static final String VPN_MTU = "1500";
    private static final String PRIVATE_VLAN = "26.26.26.%s";
    private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";

    private static final Method getInt;
    static {
        try {
            getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class ProtectWorker extends LocalSocketListener {
        private File socketFile;

        ProtectWorker() {
            super("ShadowsocksVpnThread");
            socketFile = new File(filesDir, "protect_path");
        }

        @Override
        public void accept(LocalSocket socket) {
            try {
                socket.getInputStream().read();
                FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                if (fds.length == 0) return;
                int fd = (Integer) getInt.invoke(fds[0]);
                boolean ret = protect(fd);
                JniHelper.close(fd);
                socket.getOutputStream().write(ret ? (byte) 0 : (byte) 1);
            } catch (Exception e) {
                Log.e(tag, "Error when protect socket", e);
                App.app.track(e);
            }
        }
    }

    static class NullConnectionException extends NullPointerException {
    }

    {
        BaseService.register(this);
    }

    private String tag = "ShadowsocksVpnService";

    @Override
    public ServiceNotification createNotification() {
        return new ServiceNotification(this, data.profile.formattedName, "service-vpn");
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
                startActivity(new Intent(this, VpnRequestActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
            throw new Exception("sendFd failed");
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
                .setMtu(Integer.parseInt(VPN_MTU))
                .addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), 24);

        String[] remoteDns = profile.remoteDns.split(",");
        for (String dns : remoteDns) {
            builder.addDnsServer(dns.trim());
        }

        if (profile.ipv6) {
            builder.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "1"), 126);
            builder.addRoute("::", 0);
        }

        if (Build.VERSION.SDK_INT >= 21 && profile.proxyApps) {
            String[] pkgList = profile.individual.split("\n");
            for (String pkg : pkgList) {
                try {
                    if (profile.bypass) {
                        builder.addDisallowedApplication(pkg);
                    } else {
                        builder.addAllowedApplication(pkg);
                    }
                } catch (PackageManager.NameNotFoundException ex) {
                    Log.e(tag, "Invalid package name", ex);
                }
            }
        }

        int route = profile.route;
        switch (route) {
            case Acl.ALL:
            case Acl.BYPASS_CHN:
            case Acl.CUSTOM_RULES:
                builder.addRoute("0.0.0.0", 0);
                break;
            default:
                String[] bypassPrivateRoute = getResources().getStringArray(R.array.bypass_private_route);
                for (String routeStr : bypassPrivateRoute) {
                    Subnet subnet = Subnet.fromString(routeStr);
                    builder.addRoute(subnet.address.getHostAddress(), subnet.prefixSize);
                }
                String[] remoteDnsList = profile.remoteDns.split(",");
                for (String dns : remoteDnsList) {
                    builder.addRoute(dns.trim().parseNumericAddress(), dns.address.size << 3);
                }
                break;
        }

        ParcelFileDescriptor conn = builder.establish();
        this.conn = conn;
        int fd = conn.getFd();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.TUN2SOCKS).getAbsolutePath());
        cmd.add("--netif-ipaddr");
        cmd.add(String.format(Locale.ENGLISH, PRIVATE_VLAN, "2"));
        cmd.add("--netif-netmask");
        cmd.add("255.255.255.0");
        cmd.add("--socks-server-addr");
        cmd.add("127.0.0.1:" + DataStore.portProxy);
        cmd.add("--tunfd");
        cmd.add(Integer.toString(fd));
        cmd.add("--tunmtu");
        cmd.add(Integer.toString(Integer.parseInt(VPN_MTU)));
        cmd.add("--sock-path");
        cmd.add("sock_path");
        cmd.add("--loglevel");
        cmd.add("3");
        if (profile.ipv6) {
            cmd.add("--netif-ip6addr");
            cmd.add(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "2"));
        }
        cmd.add("--enable-udprelay");
        if (!profile.udpdns) {
            cmd.add("--dnsgw");
            cmd.add("127.0.0.1:" + DataStore.portLocalDns);
        }
        tun2socksProcess = new GuardedProcess(cmd).start();
        return fd;
    }

    private boolean sendFd(int fd) {
        if (fd != -1) {
            for (int tries = 1; tries < 5; tries++) {
                try {
                    Thread.sleep(1000L * tries);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (JniHelper.sendFd(fd, new File(filesDir, "sock_path").getAbsolutePath()) != -1) {
                    return true;
                }
            }
        }
        return false;
    }
}