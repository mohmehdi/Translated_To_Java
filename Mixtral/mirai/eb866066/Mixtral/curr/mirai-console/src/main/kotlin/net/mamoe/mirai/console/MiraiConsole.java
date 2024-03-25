package net.mamoe.mirai.console;

import kotlinx.coroutines.Runnable;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.api.http.MiraiHttpAPIServer;
import net.mamoe.mirai.api.http.generateSessionKey;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.console.commands.CommandManager;
import net.mamoe.mirai.console.commands.CommandManagerKt;
import net.mamoe.mirai.console.commands.CommandManagerOwnerKt;
import net.mamoe.mirai.console.command.CommandOwner;
import net.mamoe.mirai.console.command.CommandSender;
import net.mamoe.mirai.console.plugins.PluginManager;
import net.mamoe.mirai.console.plugins.PluginManagerKt;
import net.mamoe.mirai.console.plugins.PluginManagerOwnerKt;
import net.mamoe.mirai.console.util.ConsoleInternal;
import net.mamoe.mirai.contact.NameColor;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.contact.PermissionDeniedException;
import net.mamoe.mirai.contact.contact;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.MiraiLoggerKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MiraiConsole {
    public static List < Bot > bots;

    public static Bot getBotByUIN(long uin) {
        for (Bot bot: bots) {
            if (bot.get() != null && bot.get().getUin() == uin) {
                return bot.get();
            }
        }
        return null;
    }

    public static PluginManager pluginManager;

    public static MiraiLogger logger;

    public static String path;

    public static String version = "v0.01";
    public static String coreVersion = "v0.15.1";
    public static String build = "Beta";

    public static MiraiConsoleUI frontEnd;

    public static void start(MiraiConsoleUI frontEnd) {
        MiraiConsole.frontEnd = frontEnd;
        frontEnd.pushVersion(version, build, coreVersion);
        logger("Mirai-console [$version $build | core version $coreVersion] is still in testing stage, majority feature is available");
        logger("Mirai-console now running under " + System.getProperty("user.dir"));
        logger("Get news in github: <https://github.com/mamoe/mirai-console>");
        logger("Mirai is an open-source project, please follow the open-source project agreement");
        logger("Powered by Mamoe Technologies and contributors");

        DefaultCommands();
        HTTPAPIAdaptar();
        pluginManager.loadPlugins();
        CommandListener.start();

        logger("Mirai-console started");
        logger("Type '/login qqnumber qqpassword' to login a bot");
        logger("输入 '/login qq号 qq密码' 来登陆一个BOT");
    }

    public static void stop() {
        PluginManager.disableAllPlugins();
    }

    public static void DefaultCommands() {
        CommandManagerOwnerKt.buildCommand(
            "login",
            "Mirai-Console default bot login command",
            (CommandSender sender, CommandOwner owner, String[] args) -> {
                if (args.length < 2) {
                    frontEnd.pushLog(0, "\"/login qqnumber qqpassword \" to login a bot");
                    frontEnd.pushLog(0, "\"/login qq号 qq密码 \" 来登陆一个BOT");
                    return false;
                }
                long qqNumber = Long.parseLong(args[0]);
                String qqPassword = args[1];
                frontEnd.pushLog(0, "[Bot Login] logging in...");
                try {
                    Bot bot = Bot.getInstance(qqNumber, qqPassword);
                    bot.login();
                    frontEnd.pushBot(bot);
                    frontEnd.pushLog(0, "[Bot Login] " + qqNumber + " login successes");
                } catch (Exception e) {
                    frontEnd.pushLog(0, "[Bot Login] " + qqNumber + " login failed -> " + e.getMessage());
                }
                return true;
            }
        );

        CommandManagerOwnerKt.buildCommand(
            "status",
            "Mirai-Console default status command",
            (CommandSender sender, CommandOwner owner, String[] args) -> {
                if (args.length == 0) {
                    frontEnd.pushLog(0, "Currently " + bots.size() + " bots are online");
                } else {
                    long bot;
                    try {
                        bot = Long.parseLong(args[0]);
                    } catch (NumberFormatException e) {
                        frontEnd.pushLog(0, "Invalid bot number: " + args[0]);
                        return true;
                    }

                    Bot botInstance = getBotByUIN(bot);
                    if (botInstance == null) {
                        frontEnd.pushLog(0, "Bot not found: " + bot);
                        return true;
                    }

                    int friendCount = botInstance.getFriends().size();
                    int groupCount = botInstance.getGroups().size();

                    frontEnd.pushLog(0, bot + " is online; friend count: " + friendCount + "; group count: " + groupCount);
                }
                return true;
            }
        );

        CommandManagerOwnerKt.buildCommand(
            "say",
            "Mirai-Console default say command",
            (CommandSender sender, CommandOwner owner, String[] args) -> {
                if (args.length < 3) {
                    frontEnd.pushLog(0, "say [friendId or groupId] [message]");
                    frontEnd.pushLog(0, "say [botId] [friendId or groupId] [message]");
                    return false;
                }

                long botId;
                if (args.length == 3) {
                    if (bots.size() == 0) {
                        frontEnd.pushLog(0, "No bots logged in");
                        return false;
                    }
                    botId = bots.get(0).get().getUin();
                } else {
                    botId = Long.parseLong(args[0]);
                }

                long targetId;
                try {
                    targetId = Long.parseLong(args[args.length - 2]);
                } catch (NumberFormatException e) {
                    frontEnd.pushLog(0, "Invalid target id: " + args[args.length - 2]);
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, args.length - 2, args.length));

                Bot bot = getBotByUIN(botId);
                if (bot == null) {
                    frontEnd.pushLog(0, "Bot not found: " + botId);
                    return false;
                }

                Contact target;
                try {
                    target = bot.getContact(targetId);
                } catch (PermissionDeniedException e) {
                    frontEnd.pushLog(0, "No permission to access the contact: " + targetId);
                    return true;
                } catch (NoSuchElementException e) {
                    frontEnd.pushLog(0, "Contact not found: " + targetId);
                    return true;
                }

                if (target instanceof Friend) {
                    ((Friend) target).sendMessage(message);
                } else if (target instanceof Group) {
                    ((Group) target).sendMessage(message);
                } else {
                    frontEnd.pushLog(0, "Invalid target type: " + target.getClass().getSimpleName());
                    return true;
                }

                frontEnd.pushLog(0, "Message sent");
                return true;
            }
        );

        CommandManagerOwnerKt.buildCommand(
            "plugins",
            "show all plugins",
            (CommandSender sender, CommandOwner owner, String[] args) -> {
                List < String > pluginDescriptions = PluginManager.getAllPluginDescriptions().stream()
                .map(pluginDescription -> "\t" + pluginDescription.getName() + " v" + pluginDescription.getVersion() + " by " + pluginDescription.getAuthor() + " " + pluginDescription.getInfo())
                .collect(Collectors.toList());
                frontEnd.pushLog(0, "Loaded " + pluginDescriptions.size() + " plugins");
                pluginDescriptions.forEach(frontEnd::pushLog);
                return true;
            }
        );

        CommandManagerOwnerKt.buildCommand(
            "command",
            "show all commands",
            (CommandSender sender, CommandOwner owner, String[] args) -> {
                List < String > commandDescriptions = CommandManager.getCommands().stream()
                .map(command -> "\t" + command.getName() + " :" + command.getDescription())
                .collect(Collectors.toList());
                frontEnd.pushLog(0, "Currently have " + commandDescriptions.size() + " commands");
                commandDescriptions.forEach(frontEnd::pushLog);
                return true;
            }
        );

        CommandManagerOwnerKt.buildCommand(
            "about",
            "About Mirai-Console",
            (CommandSender sender, CommandOwner owner, String[] args) -> {
                frontEnd.pushLog(0, "v" + version + " " + build + " is still in testing stage, majority feature is available");
                frontEnd.pushLog(0, "Now running under " + System.getProperty("user.dir"));
                frontEnd.pushLog(0, "Get news in github: <https://github.com/mamoe/mirai-console>");
                frontEnd.pushLog(0, "Mirai is an open-source project, please follow the open-source project agreement");
                frontEnd.pushLog(0, "Powered by Mamoe Technologies and contributors");
                return true;
            }
        );
    }

    public class HTTPAPIAdaptar {

        public static void invoke() {
            if (MiraiProperties.HTTP_API_ENABLE) {
                if (MiraiProperties.HTTP_API_AUTH_KEY.startsWith("InitKey")) {
                    logger("请尽快更改初始生成的HTTP API AUTHKEY");
                }

                logger("正在启动HTTPAPI; 端口=" + MiraiProperties.HTTP_API_PORT);
                MiraiHttpAPIServer.logger = new SimpleLogger("HTTP API") {
                    public void log(int level, String message, Throwable e) {
                        HTTPAPIAdaptar.logger("[Mirai HTTP API]", 0, message);
                    }
                };
                MiraiHttpAPIServer.start(MiraiProperties.HTTP_API_PORT, MiraiProperties.HTTP_API_AUTH_KEY);
                logger("HTTPAPI启动完成; 端口= " + MiraiProperties.HTTP_API_PORT);
            }

        }
    }

    public class DefaultCommands {

        public static void invoke() {
            buildCommand(
                command -> {
                    command.name = "login";
                    command.description = "Mirai-Console default bot login command";
                    command.onCommand = DefaultCommands::onLoginCommand;
                }
            );

            buildCommand(
                command -> {
                    command.name = "status";
                    command.description = "Mirai-Console default status command";
                    command.onCommand = DefaultCommands::onStatusCommand;
                }
            );

            buildCommand(
                command -> {
                    command.name = "say";
                    command.description = "Mirai-Console default say command";
                    command.onCommand = DefaultCommands::onSayCommand;
                }
            );

            buildCommand(
                command -> {
                    command.name = "plugins";
                    command.alias = listOf("plugin");
                    command.description = "show all plugins";
                    command.onCommand = DefaultCommands::onPluginsCommand;
                }
            );

            buildCommand(
                command -> {
                    command.name = "command";
                    command.alias = listOf("commands", "help", "helps");
                    command.description = "show all commands";
                    command.onCommand = DefaultCommands::onCommandCommand;
                }
            );

            buildCommand(
                command -> {
                    command.name = "about";
                    command.description = "About Mirai-Console";
                    command.onCommand = DefaultCommands::onAboutCommand;
                }
            );
        }

    }

    static class CommandListener {
        public static Queue < String > commandChannel;

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
                    logger("Unknown command: " + fullCommand);
                }
            }
            processNextCommandLine();
        }
    }

    static class UIPushLogger {
        public static void invoke(Object any) {
            invoke("[Mirai" + version + " " + build, 0, any);
        }

        public static void invoke(String identityStr, long identity, Object any) {
            if (any != null) {
                frontEnd.pushLog(identity, identityStr + ": " + any);
            }
        }
    }

    static class MiraiProperties {
        public static Config config;

        public static boolean HTTP_API_ENABLE;
        public static int HTTP_API_PORT;
        public static String HTTP_API_AUTH_KEY;

        static {
            path = System.getProperty("user.dir");
            File miraiPropertiesFile = new File(path, "mirai.properties");
            if (!miraiPropertiesFile.exists()) {
                try (OutputStream outputStream = new FileOutputStream(miraiPropertiesFile)) {
                    outputStream.write("HTTP_API_ENABLE=true\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.write("HTTP_API_PORT=8080\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.write("HTTP_API_AUTH_KEY=InitKey" + generateSessionKey() + "\n".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            config = Config.load(miraiPropertiesFile);

            HTTP_API_ENABLE = config.getBoolean("HTTP_API_ENABLE", true);
            HTTP_API_PORT = config.getInt("HTTP_API_PORT", 8080);
            HTTP_API_AUTH_KEY = config.getString("HTTP_API_AUTH_KEY", "InitKey" + generateSessionKey());
        }
    }
}