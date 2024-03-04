package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.github.shadowsocks.preference.DataStore;
import java.io.File;

public class TransproxyService extends Service implements LocalDnsService.Interface {
    {
        BaseService.register(this);
    }

    @Override
    public String getTag() {
        return "ShadowsocksTransproxyService";
    }

    @Override
    public ServiceNotification createNotification() {
        return new ServiceNotification(this, data.getProfile().getFormattedName(), "service-transproxy", true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    private GuardedProcess sstunnelProcess;
    private GuardedProcess redsocksProcess;

    private void startDNSTunnel() {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(new File(applicationInfo.nativeLibraryDir, Executable.SS_TUNNEL).getAbsolutePath());
        cmd.add("-t");
        cmd.add("10");
        cmd.add("-b");
        cmd.add("127.0.0.1");
        cmd.add("-u");
        cmd.add("-l");
        cmd.add(Integer.toString(DataStore.getPortLocalDns()));
        cmd.add("-L");
        cmd.add(data.getProfile().getRemoteDns().split(",")[0].trim() + ":53");
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
                " local_port = " + DataStore.getPortTransproxy() + ";\n" +
                " ip = 127.0.0.1;\n" +
                " port = " + DataStore.getPortProxy() + ";\n" +
                " type = socks5;\n" +
                "}\n";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(getFilesDir(), "redsocks.conf")))) {
            writer.write(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(new File(applicationInfo.nativeLibraryDir, Executable.REDSOCKS).getAbsolutePath());
        cmd.add("-c");
        cmd.add("redsocks.conf");
        redsocksProcess = new GuardedProcess(cmd).start();
    }

    @Override
    public void startNativeProcesses() {
        startRedsocksDaemon();
        super.startNativeProcesses();
        if (data.getProfile().isUdpdns()) {
            startDNSTunnel();
        }
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