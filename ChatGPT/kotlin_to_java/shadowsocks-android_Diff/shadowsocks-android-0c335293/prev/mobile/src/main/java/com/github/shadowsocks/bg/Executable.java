package com.github.shadowsocks.bg;

import android.text.TextUtils;
import android.util.Log;
import com.github.shadowsocks.App;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Executable {
    public static final String REDSOCKS = "libredsocks.so";
    public static final String SS_LOCAL = "libss-local.so";
    public static final String SS_TUNNEL = "libss-tunnel.so";
    public static final String TUN2SOCKS = "libtun2socks.so";
    public static final String OVERTURE = "liboverture.so";

    public static final Set<String> EXECUTABLES = new HashSet<String>() {{
        add(SS_LOCAL);
        add(SS_TUNNEL);
        add(REDSOCKS);
        add(TUN2SOCKS);
        add(OVERTURE);
    }};

    public static void killAll() {
        File[] processes = new File("/proc").listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
        if (processes != null) {
            for (File process : processes) {
                File exe = new File(process, "cmdline");
                try (BufferedReader reader = new BufferedReader(new FileReader(exe))) {
                    String cmdline = reader.readLine();
                    String[] parts = cmdline.split(Character.toString((char) 0), 2);
                    String executable = parts[0];
                    if (executable.equals(App.Companion.getApp().getApplicationInfo().nativeLibraryDir) && EXECUTABLES.contains(new File(executable).getName())) {
                        int errno = JniHelper.sigkill(Integer.parseInt(process.getName()));
                        if (errno != 0) {
                            Log.w("kill", "SIGKILL " + new File(executable).getAbsolutePath() + " (" + process.getName() + ") failed with " + errno);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}