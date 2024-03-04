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
import com.github.shadowsocks.utils.Utils;

import java.io.File;
import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

import androidx.annotation.Nullable;

public class VpnService extends android.net.VpnService implements LocalDnsService.Interface {
    private static final int VPN_MTU = 1500;
    private static final String PRIVATE_VLAN = "26.26.26.%s";
    private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";

    private static Method getInt;

    static {
        try {
            getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private class ProtectWorker extends LocalSocketListener {
        ProtectWorker(String name) {
            super(name);
        }

        private final File socketFile = new File(getFilesDir(), "protect_path");


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
                Log.e(tag, "Error when protect socket", e);
                App.Companion.getApp().track(e);
            }
        }
    }

    public static class NullConnectionException extends NullPointerException {
    }

    public VpnService() {
        BaseService.register(this);
    }

    @Override
    public String getTag() {
        return "ShadowsocksVpnService";
    }

    @Override
    public ServiceNotification createNotification() {
        return new ServiceNotification(this, data.getProfile().getFormattedName(), "service-vpn");
    }


    @Nullable
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
        if (worker != null) {
            worker.stopThread();
            worker = null;
        }
        super.killProcesses();
        if (tun2socksProcess != null) {
            tun2socksProcess.destroy();
            tun2socksProcess = null;
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            conn = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BaseService.usingVpnMode) {
            if (android.net.VpnService.prepare(this) != null) {
                startActivity(new Intent(this, VpnRequestActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                return super.onStartCommand(intent, flags, startId);
            }
        }
        stopRunner(true);
        return Service.START_NOT_STICKY;
    }

    @Override
    public void startNativeProcesses() {
        ProtectWorker worker = new ProtectWorker("ShadowsocksVpnThread");
        worker.start();
        this.worker = worker;

        super.startNativeProcesses();

        int fd = startVpn();
        if (!sendFd(fd)) {
            throw new Exception("sendFd failed");
        }
    }

    @Override
    protected ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
        cmd.add("-V");
        return cmd;
    }

    private int startVpn() {
        Profile profile = data.getProfile();
        Builder builder = new Builder()
                .setSession(profile.getFormattedName())
                .setMtu(VPN_MTU)
                .addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN, "1"), 24);

        for (String dns : profile.getRemoteDns().split(",")) {
            builder.addDnsServer(dns.trim());
        }

        if (profile.isIpv6()) {
            builder.addAddress(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "1"), 126);
            builder.addRoute("::", 0);
        }

        if (Build.VERSION.SDK_INT >= 21 && profile.isProxyApps()) {
            for (String pkg : profile.getIndividual().split("\n")) {
                try {
                    if (profile.isBypass()) {
                        builder.addDisallowedApplication(pkg);
                    } else {
                        builder.addAllowedApplication(pkg);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(tag, "Invalid package name", e);
                }
            }
        }

        switch (profile.getRoute()) {
            case Acl.ALL:
            case Acl.BYPASS_CHN:
            case Acl.CUSTOM_RULES:
                builder.addRoute("0.0.0.0", 0);
                break;
            default:
                for (String subnet : getResources().getStringArray(R.array.bypass_private_route)) {
                    Subnet s = Subnet.fromString(subnet);
                    builder.addRoute(s.getAddress().getHostAddress(), s.getPrefixSize());
                }
                for (String dns : profile.getRemoteDns().split(",")) {
                    builder.addRoute(Utils.parseNumericAddress(dns.trim()), Utils.parseNumericAddress(dns.trim()).getAddress().length * 8);
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
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.TUN2SOCKS).getAbsolutePath());
        cmd.add("--netif-ipaddr");
        cmd.add(String.format(Locale.ENGLISH, PRIVATE_VLAN, "2"));
        cmd.add("--netif-netmask");
        cmd.add("255.255.255.0");
        cmd.add("--socks-server-addr");
        cmd.add("127.0.0.1:" + DataStore.portProxy);
        cmd.add("--tunfd");
        cmd.add(String.valueOf(fd));
        cmd.add("--tunmtu");
        cmd.add(String.valueOf(VPN_MTU));
        cmd.add("--sock-path");
        cmd.add("sock_path");
        cmd.add("--loglevel");
        cmd.add("3");
        if (profile.isIpv6()) {
            cmd.add("--netif-ip6addr");
            cmd.add(String.format(Locale.ENGLISH, PRIVATE_VLAN6, "2"));
        }
        cmd.add("--enable-udprelay");
        if (!profile.isUdpdns()) {
            cmd.add("--dnsgw");
            cmd.add("127.0.0.1:" + DataStore.portLocalDns);
        }
        tun2socksProcess = new GuardedProcess(cmd);
        tun2socksProcess.start(sendFd(fd));
        return fd;
    }

    private boolean sendFd(int fd) {
        if (fd != -1) {
            int tries = 1;
            while (tries < 5) {
                try {
                    Thread.sleep(1000L * tries);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (JniHelper.sendFd(fd, new File(getFilesDir(), "sock_path").getAbsolutePath()) != -1) {
                    return true;
                }
                tries += 1;
            }
        }
        return false;
    }
}