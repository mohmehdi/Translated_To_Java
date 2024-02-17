

package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.github.shadowsocks.preference.DataStore;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TransproxyService extends Service implements LocalDnsService.Interface {
    static {
        BaseService.register(new TransproxyService());
    }

    private TransproxyService() {}

    @NonNull
    @Override
    public String getTag() {
        return "ShadowsocksTransproxyService";
    }

    @Override
    public ServiceNotification createNotification() {
        return new ServiceNotification(this, data.getProfile().getFormattedName(), "service-transproxy", true);
    }

    @Nullable
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

    private void startDNSTunnel() {
        List<String> cmd = new ArrayList<>();
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.SS_TUNNEL).getAbsolutePath());
        cmd.add("-t");
        cmd.add("10");
        cmd.add("-b");
        cmd.add("127.0.0.1");
        cmd.add("-u");
        cmd.add("-l");
        cmd.add(Integer.toString(DataStore.portLocalDns));
        cmd.add("-L");
        cmd.add(DataStore.profile.remoteDns.split(",")[0].trim() + ":53");
        cmd.add("-c");
        cmd.add("shadowsocks.json");

        sstunnelProcess = new GuardedProcess(cmd).start();
    }

    private void startRedsocksDaemon() {
        String config = "base {\n" +
                " log_debug = off;\n" +
                " log_info = off;\n" +
                " log = stderr;\n" +
                " daemon = off;\n" +
                " redirector = iptables;\n" +
                "}\n" +
                "redsocks {\n" +
                " local_ip = 127.0.0.1;\n" +
                " local_port = " + DataStore.portTransproxy + ";\n" +
                " ip = 127.0.0.1;\n" +
                " port = " + DataStore.portProxy + ";\n" +
                " type = socks5;\n" +
                "}\n";

        File file = new File(getFilesDir(), "redsocks.conf");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(config);
        } catch (IOException e) {
            e.printStackTrace();
        }

        redsocksProcess = new GuardedProcess(new ArrayList<String>() {{
            add(new File(getApplicationInfo().nativeLibraryDir, Executable.REDSOCKS).getAbsolutePath());
            add("-c");
            add("redsocks.conf");
        }}).start();
    }

    @Override
    public void startNativeProcesses() {
        startRedsocksDaemon();
        super.startNativeProcesses();
        if (data.getProfile().udpdns) startDNSTunnel();
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
}