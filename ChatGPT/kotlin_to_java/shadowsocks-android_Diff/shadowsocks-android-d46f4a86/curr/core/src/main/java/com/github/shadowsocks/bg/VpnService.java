package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Network;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.VpnRequestActivity;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.net.ConcurrentLocalSocketListener;
import com.github.shadowsocks.net.DefaultNetworkListener;
import com.github.shadowsocks.net.Subnet;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parseNumericAddress;
import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.delay;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import android.net.VpnService as BaseVpnService;

public class VpnService extends BaseVpnService implements LocalDnsService.Interface {
    private static final int VPN_MTU = 1500;
    private static final String PRIVATE_VLAN = "172.19.0.%s";
    private static final String PRIVATE_VLAN6 = "fdfe:dcba:9876::%s";

    private static final Method getInt = FileDescriptor.class.getDeclaredMethod("getInt$");

    public static class CloseableFd implements Closeable {
        private final FileDescriptor fd;

        public CloseableFd(FileDescriptor fd) {
            this.fd = fd;
        }

        @Override
        public void close() throws IOException {
            Os.close(fd);
        }
    }

    private class ProtectWorker extends ConcurrentLocalSocketListener {
        public ProtectWorker() {
            super("ShadowsocksVpnThread", new File(Core.deviceStorage.noBackupFilesDir, "protect_path"));
        }

        @Override
        protected void acceptInternal(LocalSocket socket) throws IOException {
            socket.getInputStream().read();
            FileDescriptor fd = socket.getAncillaryFileDescriptors().get(0);
            try (CloseableFd closeableFd = new CloseableFd(fd)) {
                if (underlyingNetwork != null && Build.VERSION.SDK_INT >= 23) {
                    try {
                        underlyingNetwork.bindSocket(fd);
                        socket.getOutputStream().write(0);
                    } catch (IOException e) {
                        if (((ErrnoException) e.getCause()).errno != 64) {
                            printLog(e);
                        }
                        socket.getOutputStream().write(1);
                    }
                } else {
                    socket.getOutputStream().write(protect((int) getInt.invoke(fd)));
                }
            }
        }
    }

    private class NullConnectionException extends NullPointerException {
        @Override
        public String getLocalizedMessage() {
            return getString(R.string.reboot_required);
        }
    }

    private BaseService.Data data = new BaseService.Data(this);
    private String tag = "ShadowsocksVpnService";
    private ParcelFileDescriptor conn = null;
    private ProtectWorker worker = null;
    private boolean active = false;
    private Network underlyingNetwork = null;
    private Network[] underlyingNetworks = underlyingNetwork != null ? new Network[]{underlyingNetwork} : null;

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
        stopRunner();
    }

    @Override
    public void killProcesses() {
        active = false;
        DefaultNetworkListener.stop(this);
        if (worker != null) {
            worker.shutdown();
        }
        worker = null;
        super.killProcesses();
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        conn = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DataStore.serviceMode == Key.modeVpn) {
            if (BaseVpnService.prepare(this) != null) {
                startActivity(new Intent(this, VpnRequestActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                return super.onStartCommand(intent, flags, startId);
            }
        }
        stopRunner();
        return Service.START_NOT_STICKY;
    }

    @Override
    public void preInit() {
        DefaultNetworkListener.start(this, network -> underlyingNetwork = network);
    }

    @Override
    public List<InetAddress> resolver(String host) {
        return DefaultNetworkListener.get().getAllByName(host);
    }

    @Override
    public void startProcesses() {
        worker = new ProtectWorker();
        worker.start();

        super.startProcesses();

        sendFd(startVpn());
    }

    @Override
    public ArrayList<String> buildAdditionalArguments(ArrayList<String> cmd) {
        cmd.add("-V");
        return cmd;
    }

    private FileDescriptor startVpn() throws NullConnectionException {
        Profile profile = data.proxy.profile;
        Builder builder = new Builder()
                .setConfigureIntent(Core.configureIntent(this))
                .setSession(profile.formattedName)
                .setMtu(VPN_MTU)
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
                if (!individual.equals(me)) {
                    try {
                        if (profile.bypass) {
                            builder.addDisallowedApplication(individual);
                        } else {
                            builder.addAllowedApplication(individual);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        printLog(e);
                    }
                }
            }
            if (!profile.bypass) {
                builder.addAllowedApplication(me);
            }
        }

        switch (profile.route) {
            case Acl.ALL:
            case Acl.BYPASS_CHN:
            case Acl.CUSTOM_RULES:
                builder.addRoute("0.0.0.0", 0);
                break;
            default:
                for (String subnetString : getResources().getStringArray(R.array.bypass_private_route)) {
                    Subnet subnet = Subnet.fromString(subnetString);
                    if (subnet != null) {
                        builder.addRoute(subnet.getAddress().getHostAddress(), subnet.getPrefixSize());
                    }
                }
                for (String dnsServer : profile.remoteDns.split(",")) {
                    InetAddress inetAddress = parseNumericAddress(dnsServer.trim());
                    if (inetAddress != null) {
                        builder.addRoute(inetAddress, inetAddress.getAddress().length * 8);
                    }
                }
                break;
        }

        active = true;
        if (Build.VERSION.SDK_INT >= 22) {
            builder.setUnderlyingNetworks(underlyingNetworks);
        }

        conn = builder.establish();
        if (conn == null) {
            throw new NullConnectionException();
        }
        FileDescriptor fd = conn.getFileDescriptor();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.TUN2SOCKS).getAbsolutePath());
        cmd.add("--netif-ipaddr");
        cmd.add(String.format(Locale.ENGLISH, PRIVATE_VLAN, "2"));
        cmd.add("--netif-netmask");
        cmd.add("255.255.255.0");
        cmd.add("--socks-server-addr");
        cmd.add(DataStore.listenAddress + ":" + DataStore.portProxy);
        cmd.add("--tunfd");
        cmd.add(fd.toString());
        cmd.add("--tunmtu");
        cmd.add(String.valueOf(VPN_MTU));
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
        data.processes.start(cmd, () -> {
            try {
                sendFd(conn.getFileDescriptor());
            } catch (ErrnoException e) {
                stopRunner(false, e.getMessage());
            }
        });
        return conn.getFileDescriptor();
    }

    private void sendFd(FileDescriptor fd) throws IOException {
        int tries = 0;
        String path = new File(Core.deviceStorage.noBackupFilesDir, "sock_path").getAbsolutePath();
        while (true) {
            try {
                delay(50L << tries);
                try (LocalSocket localSocket = new LocalSocket()) {
                    localSocket.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
                    localSocket.setFileDescriptorsForSend(new FileDescriptor[]{fd});
                    localSocket.getOutputStream().write(42);
                }
                return;
            } catch (IOException e) {
                if (tries > 5) {
                    throw e;
                }
                tries += 1;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        data.binder.close();
    }
}