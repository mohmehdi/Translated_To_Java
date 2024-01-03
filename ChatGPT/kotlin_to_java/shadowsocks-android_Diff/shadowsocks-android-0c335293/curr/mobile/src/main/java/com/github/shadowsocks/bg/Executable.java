package com.github.shadowsocks.bg;

import android.text.TextUtils;
import android.util.Log;
import com.github.shadowsocks.App;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class Executable {
    public static final String REDSOCKS = "libredsocks.so";
    public static final String SS_LOCAL = "libss-local.so";
    public static final String SS_TUNNEL = "libss-tunnel.so";
    public static final String TUN2SOCKS = "libtun2socks.so";
    public static final String OVERTURE = "liboverture.so";

    public static final Set<String> EXECUTABLES = new HashSet<>();

    static {
        EXECUTABLES.add(SS_LOCAL);
        EXECUTABLES.add(SS_TUNNEL);
        EXECUTABLES.add(REDSOCKS);
        EXECUTABLES.add(TUN2SOCKS);
        EXECUTABLES.add(OVERTURE);
    }

    public static void killAll() {
        File[] processes = new File("/proc").listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
        for (File process : processes) {
            File exe = new File(process, "cmdline");
            String[] cmdline = FileUtils.readFileToString(exe).split(Character.MIN_VALUE, 2);
            if (cmdline.length > 0) {
                String exeName = cmdline[0];
                if (exe.getParent().equals(App.Companion.getApp().getApplicationInfo().nativeLibraryDir) && EXECUTABLES.contains(exeName)) {
                    int errno = JniHelper.sigkill(Integer.parseInt(process.getName()));
                    if (errno != 0) {
                        Log.w("kill", "SIGKILL " + exe.getAbsolutePath() + " (" + process.getName() + ") failed with " + errno);
                    }
                }
            }
        }
    }
}