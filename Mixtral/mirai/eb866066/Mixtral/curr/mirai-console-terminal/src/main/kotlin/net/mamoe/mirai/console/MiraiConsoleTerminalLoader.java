package net.mamoe.mirai.console;

import java.lang.management.Runtime;
import java.lang.Thread;

public class MiraiConsoleTerminalLoader {
        public static void main(String[] args) {
            MiraiConsoleTerminalUI.start();
            Thread thread = new Thread(() -> {
                MiraiConsole.start(
                        MiraiConsoleTerminalUI
                );
            });
            thread.start();
            Runtime runtime = Runtime.getRuntime();
            runtime.addShutdownHook(new Thread(() -> {
                MiraiConsole.stop();
            }, "MiraiConsoleShutdownHook"));
        }
}