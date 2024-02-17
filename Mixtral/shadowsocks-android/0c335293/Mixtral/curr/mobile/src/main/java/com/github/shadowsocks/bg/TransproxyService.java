

package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import com.github.shadowsocks.App;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.core.NativeProcessManager;
import com.github.shadowsocks.core.ServiceNotification;
import com.github.shadowsocks.core.executable.Executable;
import com.github.shadowsocks.core.process.GuardedProcess;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TransproxyService extends Service implements LocalDnsService.Interface {
    static {
        BaseService.register(new TransproxyService());
    }

    private static final String TAG = "ShadowsocksTransproxyService";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public ServiceNotification createNotification(String profileName) {
        return new ServiceNotification(this, profileName, "service-transproxy", true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.<LocalDnsService.Interface>onStartCommand(intent, flags, startId);
    }

    private GuardedProcess sstunnelProcess;
    private GuardedProcess redsocksProcess;

    @Override
    public void startNativeProcesses() {
        startRedsocksDaemon();
        super.startNativeProcesses();
        if (DataStore.isUdpdnsEnabled()) startDNSTunnel();
    }

    @Override
    public void killProcesses() {
        super.killProcesses();
        if (sstunnelProcess != null) {
            sstunnelProcess.destroy();
            sstunnelProcess = null;
        }
        if (redsocksProcess != null) {
            redsocksProcess.destroy();
            redsocksProcess = null;
        }
    }

    private void startDNSTunnel() {
        List<String> cmd = new ArrayList<>();
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.SS_TUNNEL).getAbsolutePath());
        cmd.add("-t");
        cmd.add("10");
        cmd.add("-b");
        cmd.add("127.0.0.1");
        cmd.add("-u");
        cmd.add("-l");
        cmd.add(Integer.toString(DataStore.getPortLocalDns()));
        cmd.add("-L");
        cmd.add(DataStore.getProfile().getRemoteDns().split(",")[0].trim() + ":53");
        cmd.add("-c");
        cmd.add(DataStore.getShadowsocksConfigFile().getAbsolutePath());

        sstunnelProcess = new GuardedProcess(cmd).start();
    }

    private void startRedsocksDaemon() {
        File redsocksConfFile = new File(App.getDeviceContext().getFilesDir(), "redsocks.conf");
        try (FileWriter redsocksConfWriter = new FileWriter(redsocksConfFile)) {
            redsocksConfWriter.write("base {\n" +
                    "    log_debug = off;\n" +
                    "    log_info = off;\n" +
                    "    log = stderr;\n" +
                    "    daemon = off;\n" +
                    "    redirector = iptables;\n" +
                    "}\n" +
                    "redsocks {\n" +
                    "    local_ip = 127.0.0.1;\n" +
                    "    local_port = " + DataStore.getPortTransproxy() + ";\n" +
                    "    ip = 127.0.0.1;\n" +
                    "    port = " + DataStore.getPortProxy() + ";\n" +
                    "    type = socks5;\n" +
                    "}\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> redsocksCmd = new ArrayList<>();
        redsocksCmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.REDSOCKS).getAbsolutePath());
        redsocksCmd.add("-c");
        redsocksCmd.add("redsocks.conf");

        redsocksProcess = new GuardedProcess(redsocksCmd).start();
    }
}