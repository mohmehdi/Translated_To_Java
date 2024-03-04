package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.github.shadowsocks.App;

import java.io.File;

public class TransproxyService extends Service implements LocalDnsService.Interface {
    public TransproxyService() {
        BaseService.register(this);
    }

    @Override
    public String getTag() {
        return "ShadowsocksTransproxyService";
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
        return super.onStartCommand(intent, flags, startId);
    }

    private GuardedProcess sstunnelProcess;
    private GuardedProcess redsocksProcess;

    private void startDNSTunnel() {
        File executableFile = new File(getApplicationInfo().nativeLibraryDir, Executable.SS_TUNNEL);
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(executableFile.getAbsolutePath());
        cmd.add("-t");
        cmd.add("10");
        cmd.add("-b");
        cmd.add("127.0.0.1");
        cmd.add("-u");
        cmd.add("-l");
        cmd.add(String.valueOf(DataStore.portLocalDns));
        cmd.add("-L");
        cmd.add(data.profile.getRemoteDns().split(",")[0].trim() + ":53");
        cmd.add("-c");
        cmd.add(data.shadowsocksConfigFile.getAbsolutePath());
        sstunnelProcess = new GuardedProcess(cmd).start();
    }

    private void startRedsocksDaemon() {
        File configFile = new File(App.Companion.getDeviceContext().getFilesDir(), "redsocks.conf");
        configFile.writeText("base {\n" +
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
                "}\n");
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Executable.REDSOCKS).getAbsolutePath());
        cmd.add("-c");
        cmd.add("redsocks.conf");
        redsocksProcess = new GuardedProcess(cmd).start();
    }

    @Override
    public void startNativeProcesses() {
        startRedsocksDaemon();
        super.startNativeProcesses();
        if (data.profile.getUdpdns()) {
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