package net.mamoe.mirai.console;

import net.mamoe.mirai.Bot;
import java.util.List;

public interface MiraiConsoleUI {

    void pushLog(
            long identity,
            String message
    );

    void prePushBot(
            long identity
    );

    void pushBot(
            Bot bot
    );

    void pushVersion(
            String consoleVersion,
            String consoleBuild,
            String coreVersion
    );

    String requestInput(
            String question
    ) throws Exception;

    void pushBotAdminStatus(
            long identity,
            List<Long> admins
    );
}