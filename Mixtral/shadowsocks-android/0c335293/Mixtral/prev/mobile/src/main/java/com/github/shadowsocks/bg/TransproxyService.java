package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.std.FileUtil;
import com.github.shadowsocks.std.GuardedProcess;
import com.github.shadowsocks.std.ParcelableUtil;
import com.github.shadowsocks.std.executable.Executable;
import com.github.shadowsocks.std.util.BaseService;
import com.github.shadowsocks.std.util.ServiceNotification;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TransproxyService extends Service implements LocalDnsService.Interface {
    static {
        BaseService.register(TransproxyService.class);
    }

    private static final String TAG = "ShadowsocksTransproxyService";

    private GuardedProcess sstunnelProcess;
    private GuardedProcess redsocksProcess;

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public ServiceNotification createNotification() {
        return new ServiceNotification(this, data.getProfile().formattedName, "service-transproxy", true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.<LocalDnsService.Interface>onStartCommand(intent, flags, startId);
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
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(getFilesDir(), "redsocks.conf")))) {
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
        if (DataStore.profile.udpdns) startDNSTunnel();
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