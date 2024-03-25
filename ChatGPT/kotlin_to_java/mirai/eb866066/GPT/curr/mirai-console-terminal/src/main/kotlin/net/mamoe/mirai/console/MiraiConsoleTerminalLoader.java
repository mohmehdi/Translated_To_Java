
package net.mamoe.mirai.console;

public class MiraiConsoleTerminalLoader {
    public static void main(String[] args) {
        MiraiConsoleTerminalUI.start();
        new Thread(() -> {
            MiraiConsole.start(MiraiConsoleTerminalUI.INSTANCE);
        }).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MiraiConsole.stop();
        }));
    }
}