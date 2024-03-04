package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
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

import java.io.File;
import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

public class VpnService extends android.net.VpnService implements LocalDnsService.Interface {

    private static final String VPN_MTU = "1500";
    private static final String PRIVATE_VLAN = "26.26.26.%s";
    private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";

    private final Method getInt;

    private class ProtectWorker extends LocalSocketListener {
        public ProtectWorker() {
            super("ShadowsocksVpnThread");
        }

        File socketFile = new File(App.Companion.getApp().getDeviceContext().getFilesDir(), "protect_path");

        @Override
        protected void accept(LocalSocket socket) {
            try {
                socket.getInputStream().read();
                FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                if (fds.length == 0) return;
                int fd = (int) getInt.invoke(fds[0]);
                boolean ret = protect(fd);
                JniHelper.close(fd);
                socket.getOutputStream().write(ret ? 0 : 1);
            } catch (Exception e) {
                Log.e(getTag(), "Error when protect socket", e);
                App.Companion.getApp().track(e);
            }
        }
    }

    public static class NullConnectionException extends NullPointerException {
    }

    public VpnService() throws NoSuchMethodException {
        getInt = FileDescriptor.class.getDeclaredMethod("getInt");
        getInt.setAccessible(true);
        BaseService.register(this);
    }

    @Override
    public String getTag() {
        return "ShadowsocksVpnService";
    }

    @Override
    public ServiceNotification createNotification(String profileName) {
        return new ServiceNotification(this, profileName, "service-vpn");
    }

    private ParcelFileDescriptor conn = null;
    private ProtectWorker worker = null;
    private GuardedProcess tun2socksProcess = null;

    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(SERVICE_INTERFACE)) {
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
        worker.stopThread();
        worker = null;
        super.killProcesses();
        tun2socksProcess.destroy();
        tun2socksProcess = null;
        try {
            conn.close();
        } catch (Exception e) {
            Log.e(getTag(), "Error closing ParcelFileDescriptor", e);
        }
        conn = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BaseService.usingVpnMode)
            if (BaseVpnService.prepare(this) != null)
                startActivity(new Intent(this, VpnRequestActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else
                return super.onStartCommand(intent, flags, startId);
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
        if (!sendFd(fd)) throw new Exception("sendFd failed");
    }

    @Override
    public ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
        cmd.add("-V");
        return cmd;
    }

    private int startVpn() {
        Profile profile = DataStore.INSTANCE.getProfile();
        Builder builder = new Builder()
                .setSession(profile.formattedName)
                .setMtu(Integer.parseInt(VPN_MTU))
                .addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), 24);

        for (String dnsServer : profile.remoteDns.split(",")) {
            builder.addDnsServer(dnsServer.trim());
        }

        if (profile.ipv6) {
            builder.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "1"), 126);
            builder.addRoute("::", 0);
        }

        if (profile.proxyApps) {
            String me = getPackageName();
            for (String individual : profile.individual.split("\n")) {
                try {
                    if (profile.bypass) builder.addDisallowedApplication(individual);
                    else builder.addAllowedApplication(individual);
                } catch (PackageManager.NameNotFoundException ex) {
                    Log.e(getTag(), "Invalid package name", ex);
                }
            }
            if (!profile.bypass) builder.addAllowedApplication(me);
        }

        switch (profile.route) {
            case ALL:
            case BYPASS_CHN:
            case CUSTOM_RULES:
                builder.addRoute("0.0.0.0", 0);
                break;
            default:
                for (String subnetStr : getResources().getStringArray(R.array.bypass_private_route)) {
                    Subnet subnet = Subnet.fromString(subnetStr);
                    if (subnet != null) {
                        builder.addRoute(subnet.address.getHostAddress(), subnet.prefixSize);
                    }
                }
                for (String dns : profile.remoteDns.split(",")) {
                    String numericAddress = dns.trim().parseNumericAddress();
                    if (numericAddress != null) {
                        builder.addRoute(numericAddress, numericAddress.length() << 3);
                    }
                }
                break;
        }

        ParcelFileDescriptor conn = builder.establish();
        if (conn == null) throw new NullConnectionException();
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
        cmd.add(VPN_MTU);
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
        tun2socksProcess = new GuardedProcess(cmd);
        tun2socksProcess.start(() -> sendFd(fd));
        return fd;
    }

    private boolean sendFd(int fd) {
        if (fd != -1) {
            int tries = 0;
            while (tries < 10) {
                try {
                    Thread.sleep(30L << tries);
                } catch (InterruptedException e) {
                    Log.e(getTag(), "Error sleeping", e);
                }
                if (JniHelper.INSTANCE.sendFd(fd, new File(App.Companion.getApp().getDeviceContext().getFilesDir(), "sock_path").getAbsolutePath()) != -1)
                    return true;
                tries += 1;
            }
        }
        return false;
    }
}
