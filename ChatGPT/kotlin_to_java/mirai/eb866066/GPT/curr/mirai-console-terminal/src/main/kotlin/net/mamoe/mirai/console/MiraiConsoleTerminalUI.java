
package net.mamoe.mirai.console;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;
import com.googlecode.lanterna.terminal.swing.SwingTerminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.delay;
import kotlinx.coroutines.launch;
import net.mamoe.mirai.Bot;
import java.awt.Font;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import kotlin.concurrent.thread;
import kotlin.system.exitProcess;

public class MiraiConsoleTerminalUI implements MiraiConsoleUI {
    private static final int cacheLogSize = 50;
    private static String mainTitle = "Mirai Console v0.01 Core v0.15";

    @Override
    public void pushVersion(String consoleVersion, String consoleBuild, String coreVersion) {
        mainTitle = "Mirai Console(Terminal) " + consoleVersion + " " + consoleBuild + " Core " + coreVersion;
    }

    @Override
    public void pushLog(long identity, String message) {
        log.get(identity).push(message);
        if (identity == screens.get(currentScreenId)) {
            drawLog(message);
        }
    }

    @Override
    public void prePushBot(long identity) {
        log.put(identity, new LimitLinkedQueue<>(cacheLogSize));
    }

    @Override
    public void pushBot(Bot bot) {
        botAdminCount.put(bot.getUin(), 0);
        screens.add(bot.getUin());
        drawFrame(getScreenName(currentScreenId));
        if (terminal instanceof SwingTerminalFrame) {
            terminal.flush();
        }
    }

    private static boolean requesting = false;
    private static String requestResult = null;

    @Override
    public String requestInput(String question) {
        requesting = true;
        while (requesting) {
            delay(100);
        }
        return requestResult;
    }

    public void provideInput(String input) {
        if (requesting) {
            requestResult = input;
            requesting = false;
        } else {
            MiraiConsole.CommandListener.commandChannel.offer(commandBuilder.toString());
        }
    }

    @Override
    public void pushBotAdminStatus(long identity, List<Long> admins) {
        botAdminCount.put(identity, admins.size());
    }

    private static final ConcurrentHashMap<Long, LimitLinkedQueue<String>> log = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> botAdminCount = new ConcurrentHashMap<>();

    private static final List<Long> screens = new ArrayList<>(Collections.singletonList(0L));
    private static int currentScreenId = 0;

    private static Terminal terminal;
    private static TextGraphics textGraphics;

    private static boolean hasStart = false;
    private static PrintStream internalPrinter;

    public void start() {
        if (hasStart) {
            return;
        }

        internalPrinter = System.out;

        hasStart = true;
        DefaultTerminalFactory defaultTerminalFactory = new DefaultTerminalFactory(internalPrinter, System.in, Charset.defaultCharset());

        int fontSize = 12;
        defaultTerminalFactory
            .setInitialTerminalSize(new TerminalSize(101, 60))
            .setTerminalEmulatorFontConfiguration(SwingTerminalFontConfiguration.newInstance(new Font("Monospaced", Font.PLAIN, fontSize)));

        try {
            terminal = defaultTerminalFactory.createTerminal();
            terminal.enterPrivateMode();
            terminal.clearScreen();
            terminal.setCursorVisible(false);
        } catch (Exception e) {
            try {
                terminal = new SwingTerminalFrame("Mirai Console");
                terminal.enterPrivateMode();
                terminal.clearScreen();
                terminal.setCursorVisible(false);
            } catch (Exception ex) {
                error("can not create terminal");
            }
        }
        textGraphics = terminal.newTextGraphics();

        Job lastJob = null;
        terminal.addResizeListener((terminal1, newSize) -> {
            lastJob = GlobalScope.launch(() -> {
                delay(300);
                if (lastJob == coroutineContext.get(Job)) {
                    terminal.clearScreen();
                    update();
                    redrawCommand();
                    redrawLogs(log.get(screens.get(currentScreenId)));
                }
            });
        });

        if (!(terminal instanceof SwingTerminalFrame)) {
            System.setOut(new PrintStream(new OutputStream() {
                private StringBuilder builder = new StringBuilder();

                @Override
                public void write(int b) {
                    char c = (char) b;
                    if (c == '\n') {
                        pushLog(0, builder.toString());
                        builder = new StringBuilder();
                    } else {
                        builder.append(c);
                    }
                }
            }));
        }

        System.setErr(System.out);

        update();

        List<Character> charList = Arrays.asList(',', '.', '/', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '=', '+', '!', ' ');
        thread(() -> {
            while (true) {
                KeyStroke keyStroke = terminal.readInput();

                switch (keyStroke.getKeyType()) {
                    case ArrowLeft:
                        currentScreenId = getLeftScreenId();
                        clearRows(2);
                        cleanPage();
                        update();
                        break;
                    case ArrowRight:
                        currentScreenId = getRightScreenId();
                        clearRows(2);
                        cleanPage();
                        update();
                        break;
                    case Enter:
                        provideInput(commandBuilder.toString());
                        emptyCommand();
                        break;
                    case Escape:
                        exitProcess(0);
                        break;
                    default:
                        if (keyStroke.getCharacter() != null) {
                            if (keyStroke.getCharacter() == 8) {
                                deleteCommandChar();
                            }
                            if (Character.isLetterOrDigit(keyStroke.getCharacter()) || charList.contains(keyStroke.getCharacter())) {
                                addCommandChar(keyStroke.getCharacter());
                            }
                        }
                        break;
                }
            }
        });
    }

    private int getLeftScreenId() {
        int newId = currentScreenId - 1;
        if (newId < 0) {
            newId = screens.size() - 1;
        }
        return newId;
    }

    private int getRightScreenId() {
        int newId = 1 + currentScreenId;
        if (newId >= screens.size()) {
            newId = 0;
        }
        return newId;
    }

    private String getScreenName(int id) {
        return screens.get(id) == 0L ? "Console Screen" : "Bot: " + screens.get(id);
    }

    public void clearRows(int row) {
        textGraphics.putString(0, row, " ".repeat(terminal.getTerminalSize().getColumns()));
    }

    public void drawFrame(String title) {
        int width = terminal.getTerminalSize().getColumns();
        int height = terminal.getTerminalSize().getRows();
        terminal.setBackgroundColor(TextColor.ANSI.DEFAULT);

        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString((width - mainTitle.length()) / 2, 1, mainTitle, SGR.BOLD);
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.putString(2, 3, "-".repeat(width - 4));
        textGraphics.putString(2, 5, "-".repeat(width - 4));
        textGraphics.putString(2, height - 4, "-".repeat(width - 4));
        textGraphics.putString(2, height - 3, "|>>>");
        textGraphics.putString(width - 3, height - 3, "|");
        textGraphics.putString(2, height - 2, "-".repeat(width - 4));

        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        String leftName = getScreenName(getLeftScreenId());
        textGraphics.putString((width - title.length()) / 2 - (leftName.length() + 4), 2, leftName + " << ");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.YELLOW);
        textGraphics.putString((width - title.length()) / 2, 2, title, SGR.BOLD);
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        String rightName = getScreenName(getRightScreenId());
        textGraphics.putString((width + title.length()) / 2 + 1, 2, ">> " + rightName);
    }

    public void drawMainFrame(Number onlineBotCount) {
        drawFrame("Console Screen");
        int width = terminal.getTerminalSize().getColumns();
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        clearRows(4);
        textGraphics.putString(2, 4, "|Online Bots: " + onlineBotCount);
        textGraphics.putString(width - 2 - "Powered By Mamoe Technologies|".length(), 4, "Powered By Mamoe Technologies|");
    }

    public void drawBotFrame(long qq, Number adminCount) {
        drawFrame("Bot: " + qq);
        int width = terminal.getTerminalSize().getColumns();
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        clearRows(4);
        textGraphics.putString(2, 4, "|Admins: " + adminCount);
        textGraphics.putString(width - 2 - "Add admins via commands|".length(), 4, "Add admins via commands|");
    }

    public static class LoggerDrawer {
        private static int currentHeight = 6;

        public static void drawLog(String string, boolean flush) {
            int maxHeight = terminal.getTerminalSize().getRows() - 4;
            int heightNeed = (string.length() / (terminal.getTerminalSize().getColumns() - 6)) + 1;
            if (heightNeed - 1 > maxHeight) {
                return;
            }
            if (currentHeight + heightNeed > maxHeight) {
                cleanPage();
            }
            int width = terminal.getTerminalSize().getColumns() - 7;
            String x = string;
            while (true) {
                if (x.equals("")) break;
                String toWrite = x.length() > width ? x.substring(0, width) : x;
                x = x.length() > width ? x.substring(width) : "";
                try {
                    textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
                    textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
                    textGraphics.putString(3, currentHeight, toWrite, SGR.ITALIC);
                } catch (Exception ignored) {
                }
                currentHeight++;
            }
            if (flush && terminal instanceof SwingTerminalFrame) {
                terminal.flush();
            }
        }

        public static void cleanPage() {
            for (int index = 6; index < terminal.getTerminalSize().getRows() - 4; index++) {
                clearRows(index);
            }
            currentHeight = 6;
        }

        public static void redrawLogs(Queue<String> toDraw) {
            currentHeight = 6;
            int logsToDraw = 0;
            int vara = 0;
            List<String> toPrint = new ArrayList<>();
            for (String s : toDraw) {
                int heightNeed = (s.length() / (terminal.getTerminalSize().getColumns() - 6)) + 1;
                vara += heightNeed;
                if (currentHeight + vara < terminal.getTerminalSize().getRows() - 4) {
                    logsToDraw++;
                    toPrint.add(s);
                } else {
                    return;
                }
            }
            for (String s : toPrint) {
                drawLog(s, false);
            }
            if (terminal instanceof SwingTerminalFrame) {
                terminal.flush();
            }
        }
    }

    private static StringBuilder commandBuilder = new StringBuilder();

    public void redrawCommand() {
        int height = terminal.getTerminalSize().getRows();
        int width = terminal.getTerminalSize().getColumns();
        clearRows(height - 3);
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.putString(2, height - 3, "|>>>");
        textGraphics.putString(width - 3, height - 3, "|");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(7, height - 3, commandBuilder.toString());
        if (terminal instanceof SwingTerminalFrame) {
            terminal.flush();
        }
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private void addCommandChar(char c) {
        if (!requesting && commandBuilder.length() == 0 && c != '/') {
            addCommandChar('/');
        }
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        int height = terminal.getTerminalSize().getRows();
        commandBuilder.append(c);
        if (terminal instanceof SwingTerminalFrame) {
            redrawCommand();
        } else {
            textGraphics.putString(6 + commandBuilder.length(), height - 3, String.valueOf(c));
        }
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private void deleteCommandChar() {
        if (commandBuilder.length() > 0) {
            commandBuilder = new StringBuilder(commandBuilder.substring(0, commandBuilder.length() - 1));
        }
        int height = terminal.getTerminalSize().getRows();
        if (terminal instanceof SwingTerminalFrame) {
            redrawCommand();
        } else {
            textGraphics.putString(7 + commandBuilder.length(), height - 3, " ");
        }
    }

    private Job lastEmpty = null;

    private void emptyCommand() {
        commandBuilder = new StringBuilder();
        if (terminal instanceof SwingTerminal) {
            redrawCommand();
            terminal.flush();
        } else {
            lastEmpty = GlobalScope.launch(() -> {
                delay(100);
                if (lastEmpty == coroutineContext.get(Job)) {
                    terminal.clearScreen();
                    update();
                    redrawCommand();
                    redrawLogs(log.get(screens.get(currentScreenId)));
                }
            });
        }
    }

    public void update() {
        if (screens.get(currentScreenId) == 0L) {
            drawMainFrame(screens.size() - 1);
        } else {
            drawBotFrame(screens.get(currentScreenId), 0);
        }
        redrawLogs(log.get(screens.get(currentScreenId)));
    }
}

class LimitLinkedQueue<T> extends ConcurrentLinkedDeque<T> {
    private final int limit;

    public LimitLinkedQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public void push(T e) {
        if (size() >= limit) {
            this.pollLast();
        }
        super.push(e);
    }
}