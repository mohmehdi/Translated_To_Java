
package net.mamoe.mirai.console;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.api.http.MiraiHttpAPIServer;
import net.mamoe.mirai.api.http.generateSessionKey;
import net.mamoe.mirai.console.plugins.PluginManager;
import net.mamoe.mirai.console.plugins.loadAsConfig;
import net.mamoe.mirai.console.plugins.withDefaultWrite;
import net.mamoe.mirai.console.plugins.withDefaultWriteSave;
import net.mamoe.mirai.contact.sendMessage;
import net.mamoe.mirai.utils.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class MiraiConsole {


    public static Bot getBotByUIN(long uin) {
        for (Bot bot : Bot.instances) {
            if (bot.get().getUin() == uin) {
                return bot.get();
            }
        }
        return null;
    }


    public static UIPushLogger logger = UIPushLogger;

    public static String path = System.getProperty("user.dir");

    public static String version = "v0.01";
    public static String coreVersion = "v0.15.1";
    public static String build = "Beta";

    public static MiraiConsoleUI frontEnd;
    
    public static void start(MiraiConsoleUI frontEnd) {
        MiraiConsole.frontEnd = frontEnd;
        frontEnd.pushVersion(version, build, coreVersion);
        logger("Mirai-console [" + version + " " + build + " | core version " + coreVersion + "] is still in testing stage, majority feature is available");
        logger("Mirai-console now running under " + System.getProperty("user.dir"));
        logger("Get news in github: https:");
        logger("Mirai为开源项目，请自觉遵守开源项目协议");
        logger("Powered by Mamoe Technologies and contributors");

        DefaultCommands();
        HTTPAPIAdaptar();
        pluginManager.loadPlugins();
        CommandListener.start();

        logger("Mirai-console 启动完成");
        logger("\"/login qqnumber qqpassword \" to login a bot");
        logger("\"/login qq号 qq密码 \" 来登陆一个BOT");
    }

    public static void stop() {
        PluginManager.disableAllPlugins();
    }

    public static class HTTPAPIAdaptar {
        public static void invoke() {
            if (MiraiProperties.HTTP_API_ENABLE) {
                if (MiraiProperties.HTTP_API_AUTH_KEY.startsWith("InitKey")) {
                    logger("请尽快更改初始生成的HTTP API AUTHKEY");
                }
                logger("正在启动HTTPAPI; 端口=" + MiraiProperties.HTTP_API_PORT);
                MiraiHttpAPIServer.logger = new SimpleLogger("HTTP API") {

                };
                MiraiHttpAPIServer.start(MiraiProperties.HTTP_API_PORT, MiraiProperties.HTTP_API_AUTH_KEY);
                logger("HTTPAPI启动完成; 端口= " + MiraiProperties.HTTP_API_PORT);
            }
        }
    }
    public class DefaultCommands {

        public static void invoke() {
            buildCommand(
                    "login",
                    "Mirai-Console default bot login command",
                    (commandContext, messageEvent) -> {
                        if (messageEvent.getMessage().size() < 2) {
                            logger("\"/login qqnumber qqpassword \" to login a bot");
                            logger("\"/login qq号 qq密码 \" 来登录一个BOT");
                            return false;
                        }
                        long qqNumber = Long.parseLong(messageEvent.getMessage().get(0));
                        String qqPassword = messageEvent.getMessage().get(1);
                        logger("[Bot Login]", 0, "login...");
                        try {
                            runBlocking(() -> {
                                ConsoleInternal.INSTANCE.getFrontEnd().prePushBot(qqNumber);
                                Bot bot = new Bot(qqNumber, qqPassword) {
                                    {
                                        this.loginSolver = new DefaultLoginSolver(new LoginSolverInputReader() {
                                        },
                                                new SimpleLogger<>("Login Helper") {

                                                });
                                        this.botLoggerSupplier = () -> new SimpleLogger<>("BOT $qqNumber]") {

                                        };
                                        this.networkLoggerSupplier = () -> new SimpleLogger<>("BOT $qqNumber") {

                                        };
                                    }
                                };
                                bot.login();
                                logger("[Bot Login]", 0, qqNumber + " login successes");
                                ConsoleInternal.INSTANCE.getFrontEnd().pushBot(bot);
                            });
                        } catch (Exception e) {
                            logger("[Bot Login]", 0, qqNumber + " login failed -> " + e.getMessage());
                        }
                        return true;
                    }
            );

            buildCommand(
                    "status",
                    "Mirai-Console default status command",
                    (commandContext, messageEvent) -> {
                        switch (messageEvent.getMessage().size()) {
                            case 0:
                                logger("当前有" + ConsoleInternal.INSTANCE.getBots().size() + "个BOT在线");
                                break;
                            case 1:
                                String bot = messageEvent.getMessage().get(0);
                                boolean find = false;
                                for (Map.Entry<Long, Bot> entry : ConsoleInternal.INSTANCE.getBots().entrySet()) {
                                    if (entry.getKey().toString().contains(bot)) {
                                        find = true;
                                        logger(entry.getKey() + ": 在线中; 好友数量:" + entry.getValue().getFriendList().size() + "; 群组数量:" + entry.getValue().getGroupList().size());
                                    }
                                }
                                if (!find) {
                                    logger("没有找到BOT" + bot);
                                }
                                break;
                        }
                        return true;
                    }
            );

            buildCommand(
                    "say",
                    "Mirai-Console default say command",
                    (commandContext, messageEvent) -> {
                        if (messageEvent.getMessage().size() < 2) {
                            logger("say [好友qq号或者群号] [文本消息]");
                            logger("say [bot号] [好友qq号或者群号] [文本消息]");
                            return false;
                        }
                        Bot bot = null;
                        if (messageEvent.getMessage().size() == 2) {
                            if (ConsoleInternal.INSTANCE.getBots().size() == 0) {
                                logger("还没有BOT登陆");
                                return false;
                            }
                            bot = ConsoleInternal.INSTANCE.getBots().get(0);
                        } else {
                            bot = getBotByUIN(Long.parseLong(messageEvent.getMessage().get(0)));
                        }
                        if (bot == null) {
                            logger("没有找到BOT");
                            return false;
                        }
                        long target = Long.parseLong(messageEvent.getMessage().get(messageEvent.getMessage().size() - 2));
                        String message = messageEvent.getMessage().get(messageEvent.getMessage().size() - 1);
                        try {
                            Contact contact = bot.getContact(target);
                            runBlocking(() -> contact.sendMessage(message));
                            logger("消息已推送");
                        } catch (NoSuchElementException e) {
                            logger("没有找到群或好友 号码为" + target);
                            return false;
                        }
                        return true;
                    }
            );

            buildCommand(
                    "plugins",
                    listOf("plugin"),
                    "show all plugins",
                    (commandContext, messageEvent) -> {
                        List<PluginDescription> pluginDescriptions = PluginManager.INSTANCE.getAllPluginDescriptions();
                        System.out.println("loaded " + pluginDescriptions.size() + " plugins");
                        pluginDescriptions.forEach(pluginDescription -> {
                            logger("\t" + pluginDescription.getName() + " v" + pluginDescription.getVersion() + " by" + pluginDescription.getAuthor() + " " + pluginDescription.getInfo());
                        });
                        return true;
                    }
            );

            buildCommand(
                    "command",
                    listOf("commands", "help", "helps"),
                    "show all commands",
                    (commandContext, messageEvent) -> {
                        List<Command> commands = CommandManager.INSTANCE.getCommands();
                        System.out.println("currently have " + commands.size() + " commands");
                        commands.forEach(command -> {
                            logger("\t" + command.getName() + " :" + command.getDescription());
                        });
                        return true;
                    }
            );

            buildCommand(
                    "about",
                    "About Mirai-Console",
                    (commandContext, messageEvent) -> {
                        logger("v" + version + " " + build + " is still in testing stage, majority feature is available");
                        logger("now running under " + System.getProperty("user.dir"));
                        logger("In Github, get the latest progress: <https://github.com/mamoe/mirai-console>");
                        logger("Mirai is an open-source project, please follow the open-source project agreement");
                        logger("Powered by Mamoe Technologies and contributors");
                        return true;
                    }
            );
        }

    }

    public static class CommandListener {
        public static Queue<String> commandChannel = new LinkedBlockingQueue<String>();

        public static void start() {
            new Thread(() -> processNextCommandLine()).start();
        }

        public static void processNextCommandLine() {
            String fullCommand = commandChannel.poll();
            if (fullCommand != null) {
                if (!fullCommand.startsWith("/")) {
                    fullCommand = "/" + fullCommand;
                }
                if (!CommandManager.runCommand(fullCommand)) {
                    logger("未知指令 " + fullCommand);
                }
            }
            processNextCommandLine();
        }
    }

    public class UIPushLogger {
        public static void invoke(Object any) {
            invoke("[Mirai" + version + " " + build + "]", 0L, any);
        }

        public static void invoke(String identityStr, long identity, Object any) {
            if (any != null) {
                frontEnd.pushLog(identity, identityStr + ": " + any.toString());
            }
        }
    }

    public static class MiraiProperties {
        public static File config = new File(path + "/mirai.properties").loadAsConfig();

        public static boolean HTTP_API_ENABLE = config.withDefaultWrite(true);
        public static int HTTP_API_PORT = config.withDefaultWrite(8080);
        public static String HTTP_API_AUTH_KEY = config.withDefaultWriteSave("InitKey" + generateSessionKey());
    }
}