
package net.mamoe.mirai.console;

import kotlinx.coroutines.Runnable;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedBlockingQueue;
import java.util.List;
import java.util.Properties;

public class MiraiConsole {
    public static List<Bot> bots() {
        return Bot.instances();
    }

    public static Bot getBotByUIN(long uin) {
        for (Bot bot : bots()) {
            if (bot.get() != null && bot.get().getUin() == uin) {
                return bot.get();
            }
        }
        return null;
    }

    public static PluginManager pluginManager() {
        return PluginManager.INSTANCE;
    }

    public static SimpleLogger logger = UIPushLogger;

    public static String path = System.getProperty("user.dir");

    public static final String version = "0.01";
    public static final String coreVersion = "0.15";
    public static final String build = "Beta";

    public static void start() {
        logger.info("Mirai-console [v" + version + " " + build + " | core version v" + coreVersion + "] is still in testing stage, majority feature is available");
        logger.info("Mirai-console now running under " + System.getProperty("user.dir"));
        logger.info("Get news in github: https://github.com/mamoe/mirai");
        logger.info("Mirai is an open source project, please follow the open source project agreement");
        logger.info("Powered by Mamoe Technologies and contributors");

        DefaultCommands.invoke();
        HTTPAPIAdaptar.invoke();
        pluginManager().loadPlugins();
        CommandListener.start();

        logger.info("Mirai-console started");
        logger.info("/login qqnumber qqpassword to login a bot");
        logger.info("/login qq号 qq密码 来登陆一个BOT");
    }

    public static void stop() {
        pluginManager().disableAllPlugins();
    }

    public static class HTTPAPIAdaptar {
        public static void invoke() {
            if (MiraiProperties.HTTP_API_ENABLE) {
                if (MiraiProperties.HTTP_API_AUTH_KEY.startsWith("InitKey")) {
                    logger.warning("Please change the initial generated HTTP API AUTHKEY as soon as possible");
                }
                logger.info("Starting HTTPAPI; Port=" + MiraiProperties.HTTP_API_PORT);
                MiraiHttpAPIServer.logger = new SimpleLogger("HTTP API") {

                };
                MiraiHttpAPIServer.start(MiraiProperties.HTTP_API_PORT, MiraiProperties.HTTP_API_AUTH_KEY);
                logger.info("HTTPAPI started; Port= " + MiraiProperties.HTTP_API_PORT);
            }
        }
    }

    public static class DefaultCommands {
        public static void invoke() {
            CommandManager.buildCommand("login", "Mirai-Console default bot login command", (context) -> {
                if (context.getArgString().split(" ").length < 2) {
                    logger.info("/login qqnumber qqpassword to login a bot");
                    logger.info("/login qq号 qq密码 来登陆一个BOT");
                    return false;
                }
                long qqNumber;
                try {
                    qqNumber = Long.parseLong(context.getArgString().split(" ")[0]);
                } catch (NumberFormatException e) {
                    logger.info("Invalid QQ number format");
                    return false;
                }
                String qqPassword = context.getArgString().split(" ")[1];
                logger.info("[Bot Login]", 0, "login...");
                try {
                    new Bot(qqNumber, qqPassword).alsoLogin();
                    logger.info("[Bot Login]", 0, qqNumber + " login successes");
                } catch (Exception e) {
                    logger.error("[Bot Login]", 0, qqNumber + " login failed -> " + e.getMessage());
                    e.printStackTrace();
                }
                return true;
            });

            CommandManager.buildCommand("status", "Mirai-Console default status command", (context) -> {
                switch (context.getArgString().split(" ").length) {
                    case 0:
                        logger.info("Currently " + bots().size() + " bots are online");
                        break;
                    case 1:
                        long bot;
                        try {
                            bot = Long.parseLong(context.getArgString().split(" ")[0]);
                        } catch (NumberFormatException e) {
                            logger.info("Invalid bot number format");
                            return false;
                        }
                        boolean found = false;
                        for (Bot b : bots()) {
                            if (b.get() != null && b.get().getUin() == bot) {
                                found = true;
                                logger.info(bot + ": online; friend count: " + b.get().getFriends().size() + "; group count: " + b.get().getGroups().size());
                            }
                        }
                        if (!found) {
                            logger.info("Bot not found: " + bot);
                        }
                        break;
                    default:
                        logger.info("Invalid arguments");
                        return false;
                }
                return true;
            });

            CommandManager.buildCommand("say", "Mirai-Console default say command", (context) -> {
                if (context.getArgString().split(" ").length < 3) {
                    logger.info("say [friend_qq_number or group_number] [message]");
                    logger.info("say [bot_number] [friend_qq_number or group_number] [message]");
                    return false;
                }
                Bot bot;
                if (context.getArgString().split(" ").length == 3) {
                    bot = getBotByUIN(Long.parseLong(context.getArgString().split(" ")[0]));
                } else {
                    bot = bots().size() > 0 ? bots().get(0).get() : null;
                }
                if (bot == null) {
                    logger.info("Bot not found");
                    return false;
                }
                long target;
                try {
                    target = Long.parseLong(context.getArgString().split(" ")[context.getArgString().split(" ").length - 2]);
                } catch (NumberFormatException e) {
                    logger.info("Invalid target number format");
                    return false;
                }
                String message = context.getArgString().split(" ")[context.getArgString().split(" ").length - 1];
                try {
                    bot.getFriend(target) != null ? bot.getFriend(target) : bot.getGroup(target);
                    bot.sendMessage(message);
                    logger.info("Message sent");
                } catch (Exception e) {
                    logger.error("Error sending message", e);
                    return false;
                }
                return true;
            });

            CommandManager.buildCommand("plugins", "show all plugins", (context) -> {
                List<PluginDescription> plugins = pluginManager().getAllPluginDescriptions();
                logger.info("Loaded " + plugins.size() + " plugins");
                for (PluginDescription plugin : plugins) {
                    logger.info("\t" + plugin.getName() + " v" + plugin.getVersion() + " by " + plugin.getAuthor() + " " + plugin.getInfo());
                }
                return true;
            });

            CommandManager.buildCommand("command", "show all commands", (context) -> {
                List<Command> commands = CommandManager.getCommands();
                logger.info("Currently have " + commands.size() + " commands");
                for (Command command : commands) {
                    logger.info("\t" + command.getName() + " :" + command.getDescription());
                }
                return true;
            });

            CommandManager.buildCommand("about", "About Mirai-Console", (context) -> {
                logger.info("v" + version + " " + build + " is still in testing stage, majority feature is available");
                logger.info("now running under " + System.getProperty("user.dir"));
                logger.info("Get news in github: https://github.com/mamoe/mirai");
                logger.info("Mirai is an open source project, please follow the open source project agreement");
                logger.info("Powered by Mamoe Technologies and contributors");
                return true;
            });
        }
    }

    public static class CommandListener {
        public static LinkedBlockingQueue<String> commandChannel = new LinkedBlockingQueue<>();

        public static void start() {
            new Thread(() -> {
                processNextCommandLine();
            }).start();
        }

        public static void processNextCommandLine() {
            String fullCommand = commandChannel.poll();
            if (fullCommand != null) {
                if (!fullCommand.startsWith("/")) {
                    fullCommand = "/" + fullCommand;
                }
                if (!CommandManager.runCommand(fullCommand)) {
                    logger.warning("Unknown command " + fullCommand);
                }
            }
            processNextCommandLine();
        }
    }

public class UIPushLogger {

    public static void invoke(@Nullable Object any) {
        invoke("[Mirai" + version + " " + build + "]", 0L, any);
    }

    public static void invoke(String identityStr, long identity, @Nullable Object any) {
        if (any != null) {
            MiraiConsoleUI.pushLog(identity, identityStr + ": " + any.toString());
        }
    }
}

    public static class MiraiProperties {
        public static Properties config = new Properties();

        static {
            try {
                config.load(new File("" + path + "/mirai.properties").inputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static boolean HTTP_API_ENABLE = Boolean.parseBoolean(config.getProperty("HTTP_API_ENABLE", "true"));
        public static int HTTP_API_PORT = Integer.parseInt(config.getProperty("HTTP_API_PORT", "8080"));
        public static String HTTP_API_AUTH_KEY = config.getProperty("HTTP_API_AUTH_KEY", "InitKey" + generateSessionKey());

    }
}

class MiraiConsoleLoader {
    public static void main(String[] args) {
        MiraiConsoleUI.start();
        MiraiConsole.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MiraiConsole.stop();
        }, "Shutdown-thread"));
    }
}