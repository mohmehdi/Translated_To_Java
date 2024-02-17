

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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.net.VpnService as BaseVpnService;

class VpnService extends BaseVpnService implements LocalDnsService.Interface {
    private static final String TAG = "ShadowsocksVpnService";
    private static final int VPN_MTU = 1500;
    private static final String PRIVATE_VLAN = "26.26.26.%s";
    private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";
    private static final Method getInt;
    static {
        try {
            getInt = FileDescriptor.class.getDeclaredMethod("getInt$", Integer.TYPE);
            getInt.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private class ProtectWorker extends LocalSocketListener {
        private File socketFile;

        ProtectWorker() {
            super("ShadowsocksVpnThread");
            this.socketFile = new File(App.app.getDeviceContext().getFilesDir(), "protect_path");
        }

        @Override
        public void accept(LocalSocket socket) {
            try {
                socket.getInputStream().read();
                List<FileDescriptor> fds = socket.getAncillaryFileDescriptors();
                if (fds.isEmpty()) return;
                int fd = getInt.invoke(fds.get(0), 0).intValue();
                boolean ret = protect(fd);
                JniHelper.close(fd);
                socket.getOutputStream().write(ret ? (byte) 0 : (byte) 1);
            } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, "Error when protect socket", e);
                App.app.track(e);
            }
        }
    }

    static class NullConnectionException extends NullPointerException {
    }

    private ParcelFileDescriptor conn;
    private ProtectWorker worker;
    private GuardedProcess tun2socksProcess;

    VpnService() {
        BaseService.register(this);
    }

 

    @Override
    public ServiceNotification createNotification(String profileName) {
        return new ServiceNotification(this, profileName, "service-vpn");
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (SERVICE_INTERFACE.equals(action)) {
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
            throw new RuntimeException("sendFd failed");
        }

    }

    public ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
        cmd.add("-V");
        return cmd;
    }

    private int startVpn() {
        Profile profile = data.getProfile();
        Builder builder = new Builder()
                .setSession(profile.getFormattedName())
                .setMtu(VPN_MTU)
                .addAddress(PRIVATE_VLAN.formatted(Locale.ENGLISH, "1"), 24);

        String[] remoteDnsList = profile.getRemoteDns().split(",");
        for (String remoteDns : remoteDnsList) {
            builder.addDnsServer(remoteDns.trim());
        }

        if (profile.isIpv6()) {
            builder.addAddress(PRIVATE_VLAN6.formatted(Locale.ENGLISH, "1"), 126);
            builder.addRoute("::", 0);
        }

        if (profile.isProxyApps()) {
            String me = packageName;
            String[] individualList = profile.getIndividual().split("\n");
            for (String individual : individualList) {
                if (!individual.equals(me)) {
                    try {
                        if (profile.isBypass()) {
                            builder.addDisallowedApplication(individual);
                        } else {
                            builder.addAllowedApplication(individual);
                        }
                    } catch (NameNotFoundException ex) {
                        Log.e(tag, "Invalid package name", ex);
                    }
                }
            }
            if (!profile.isBypass()) {
                builder.addAllowedApplication(me);
            }
        }

        int routeChoice = profile.getRoute();
        switch (routeChoice) {
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
                        builder.addRoute(subnet.getAddress().getHostAddress(), subnet.getPrefixSize());
                    }
                }
                List<String> remoteDnsListParsed = Arrays.stream(remoteDnsList)
                        .map(String::trim)
                        .filter(Predicate.not(String::isEmpty))
                        .map(s -> s.parseNumericAddress())
                        .filter(Objects::nonNull)
                        .map(InetAddress::getAddress)
                        .map(addr -> Integer.valueOf(addr.getHostAddress()))
                        .collect(Collectors.toList());
                remoteDnsListParsed.forEach(ip -> builder.addRoute(ip, ip << 3));
                break;
        }

        Connection conn = builder.establish();
        if (conn == null) {
            throw new NullConnectionException();
        }
        this.conn = conn;
        int fd = conn.getFd();

        List<String> cmd = new ArrayList<>();
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
        if (profile.isIpv6()) {
            cmd.add("--netif-ip6addr");
            cmd.add(PRIVATE_VLAN6.formatted(Locale.ENGLISH, "2"));
        }
        cmd.add("--enable-udprelay");
        if (!profile.isUdpdns()) {
            cmd.add("--dnsgw");
            cmd.add("127.0.0.1:" + DataStore.portLocalDns);
        }
        tun2socksProcess = new GuardedProcess(cmd).start();
        tun2socksProcess.sendFd(fd);
        return fd;
    }

    private boolean sendFd(int fd) {
        if (fd != -1) {
            int tries = 0;
            while (tries < 10) {
                try {
                    Thread.sleep(30L << tries);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int result = JniHelper.sendFd(fd, new File(app.deviceContext.getFilesDir(), "sock_path").getAbsolutePath());
                if (result != -1) {
                    return true;
                }
                tries++;
            }
        }
        return false;
    }
}