package com.github.shadowsocks.utils;

import eu.chainfire.libsuperuser.Shell;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TcpFastOpen {
    
    private static boolean supported = false;
    
    static {
        String osVersion = System.getProperty("os.version");
        String[] versionParts = osVersion.split("\\.");
        if (versionParts.length >= 3) {
            int majorVersion = Integer.parseInt(versionParts[0]);
            int minorVersion = Integer.parseInt(versionParts[1]);
            int patchVersion = Integer.parseInt(versionParts[2]);
            
            if (majorVersion >= 3) {
                if (minorVersion >= 7 || (minorVersion == 6 && patchVersion >= 1)) {
                    supported = true;
                }
            }
        }
    }

    public static boolean isSupported() {
        return supported;
    }

    public static boolean isSendEnabled() {
        File file = new File("/proc/sys/net/ipv4/tcp_fastopen");
        
        if (file.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                int value = Integer.parseInt(line.trim());
                return (value & 1) > 0;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return false;
    }

    public static String setEnabled(boolean value) {
        if (isSendEnabled() == value) {
            return null;
        } else {
            String command = "if echo " + (value ? 3 : 0) + " > /proc/sys/net/ipv4/tcp_fastopen; then\n" +
                             "  echo Success.\n" +
                             "else\n" +
                             "  echo Failed.\n" +
                             "fi";
            return Shell.run("su", new String[]{command}, null, true).join("\n");
        }
    }
}