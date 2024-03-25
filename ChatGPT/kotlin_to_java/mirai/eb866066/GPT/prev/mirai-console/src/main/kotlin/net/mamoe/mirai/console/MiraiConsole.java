
package net.mamoe.mirai.console;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.api.http.MiraiHttpAPIServer;
import net.mamoe.mirai.api.http.generateSessionKey;
import net.mamoe.mirai.console.plugins.PluginManager;
import net.mamoe.mirai.console.plugins.loadAsConfig;
import net.mamoe.mirai.console.plugins.withDefaultWrite;
import net.mamoe.mirai.console.plugins.withDefaultWriteSave;
import net.mamoe.mirai.contact.sendMessage;
import net.mamoe.mirai.utils.SimpleLogger;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static kotlin.concurrent.ThreadsKt.thread;
import static kotlinx.coroutines.runBlocking;

public class MiraiConsole {
    public static final Bot[] bots = Bot.instances;

    public static Bot getBotByUIN(long uin) {
        for (Bot bot : bots) {
            if (bot.get().uin == uin) {
                return bot.get();
            }
        }
        return null;
    }

    public static PluginManager pluginManager = PluginManager.INSTANCE;

    public static SimpleLogger logger = UIPushLogger.INSTANCE;

    public static String path = System.getProperty("user.dir");

    public static final String version = "0.01";
    public static String coreVersion = "0.15";
    public static final String build = "Beta";

    public static void start() {
        logger.invoke("Mirai-console [v" + version + " " + build + " | core version v" + coreVersion + "] is still in testing stage, majority feature is available");
        logger.invoke("Mirai-console now running under " + System.getProperty("user.dir"));
        logger.invoke("Get news in github: https:");
        logger.invoke("Mirai为开源项目，请自觉遵守开源项目协议");
        logger.invoke("Powered by Mamoe Technologies and contributors");

        DefaultCommands.INSTANCE.invoke();
        HTTPAPIAdaptar.INSTANCE.invoke();
        pluginManager.loadPlugins();
        CommandListener.start();

        logger.invoke("Mirai-console 启动完成");
        logger.invoke("\"/login qqnumber qqpassword \" to login a bot");
        logger.invoke("\"/login qq号 qq密码 \" 来登陆一个BOT");
    }

    public static void stop() {
        PluginManager.disableAllPlugins();
    }

    public static class HTTPAPIAdaptar {
        public static void invoke() {
            if (MiraiProperties.HTTP_API_ENABLE) {
                if (MiraiProperties.HTTP_API_AUTH_KEY.startsWith("InitKey")) {
                    logger.invoke("请尽快更改初始生成的HTTP API AUTHKEY");
                }
                logger.invoke("正在启动HTTPAPI; 端口=" + MiraiProperties.HTTP_API_PORT);
                MiraiHttpAPIServer.logger = new SimpleLogger("HTTP API") {

                };
                MiraiHttpAPIServer.start(MiraiProperties.HTTP_API_PORT, MiraiProperties.HTTP_API_AUTH_KEY);
                logger.invoke("HTTPAPI启动完成; 端口= " + MiraiProperties.HTTP_API_PORT);
            }
        }
    }

    public static class DefaultCommands {
        public static void invoke() {
            // Implementation not provided as it involves building commands
        }
    }

    public static class CommandListener {
        public static Queue<String> commandChannel = new LinkedBlockingQueue<>();

        public static void start() {
            thread(() -> processNextCommandLine());
        }

        public static void processNextCommandLine() {
            String fullCommand = commandChannel.poll();
            if (fullCommand != null) {
                if (!fullCommand.startsWith("/")) {
                    fullCommand = "/" + fullCommand;
                }
                if (!CommandManager.runCommand(fullCommand)) {
                    logger.invoke("未知指令 " + fullCommand);
                }
            }
            processNextCommandLine();
        }
    }

    public static class UIPushLogger {
        public static void invoke(Object any) {
            invoke("[Mirai" + version + " " + build + "]", 0L, any);
        }

        public static void invoke(String identityStr, long identity, Object any) {
            if (any != null) {
                MiraiConsoleUI.pushLog(identity, identityStr + ": " + any);
            }
        }
    }

    public static class MiraiProperties {
        public static File config = new File(path + "/mirai.properties").loadAsConfig();

        public static boolean HTTP_API_ENABLE = config.withDefaultWrite(() -> true);
        public static int HTTP_API_PORT = config.withDefaultWrite(() -> 8080);
        public static String HTTP_API_AUTH_KEY = config.withDefaultWriteSave(() -> "InitKey" + generateSessionKey());
    }
}

class MiraiConsoleLoader {
    public static void main(String[] args) {
        MiraiConsoleUI.start();
        MiraiConsole.start();
        Runtime.getRuntime().addShutdownHook(thread(false, () -> MiraiConsole.stop()));
    }
}