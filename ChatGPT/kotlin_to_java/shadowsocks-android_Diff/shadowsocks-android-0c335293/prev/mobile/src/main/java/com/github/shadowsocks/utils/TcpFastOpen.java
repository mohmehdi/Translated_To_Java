package com.github.shadowsocks.utils;

import eu.chainfire.libsuperuser.Shell;
import java.io.File;

public class TcpFastOpen {

    public static boolean supported = lazySupported();

    private static boolean lazySupported() {
        String osVersion = System.getProperty("os.version");
        String regex = "^(\d+)\.(\d+)\.(\d+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher match = pattern.matcher(osVersion);
        if (!match.find()) {
            return false;
        } else {
            int group1 = Integer.parseInt(match.group(1));
            switch (group1) {
                case Integer.MIN_VALUE:
                case 0:
                case 1:
                case 2:
                    return false;
                case 3:
                    int group2 = Integer.parseInt(match.group(2));
                    switch (group2) {
                        case Integer.MIN_VALUE:
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                            return false;
                        case 7:
                            int group3 = Integer.parseInt(match.group(3));
                            return group3 >= 1;
                        default:
                            return true;
                    }
                default:
                    return true;
            }
        }
    }

    public static boolean isSendEnabled() {
        File file = new File("/proc/sys/net/ipv4/tcp_fastopen");
        if (file.canRead()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line = reader.readLine();
                int value = Integer.parseInt(line.trim());
                return (value & 1) > 0;
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static String enabled(boolean value) {
        if (isSendEnabled() == value) {
            return null;
        } else {
            String[] command = new String[]{
                    "su",
                    "if echo " + (value ? 3 : 0) + " > /proc/sys/net/ipv4/tcp_fastopen; then",
                    "  echo Success.",
                    "else",
                    "  echo Failed.",
                    "fi"
            };
            return Shell.run(command, null, true).join("\n");
        }
    }
}