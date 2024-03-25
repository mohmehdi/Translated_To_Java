
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
import kotlin.Lazy;
import kotlin.LazyThreadSafetyMode;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.delay;
import kotlinx.coroutines.launch;
import java.awt.Font;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;

@SuppressWarnings("UNCHECKED")
public class MiraiConsoleUI {
    public static final int cacheLogSize = 50;

    public static final Map<Long, LimitLinkedQueue<String>> log = new HashMap<Long, LimitLinkedQueue<String>>() {{
        put(0L, new LimitLinkedQueue<String>(cacheLogSize));
        put(2821869985L, new LimitLinkedQueue<String>(cacheLogSize));
    }};
    public static final Map<Long, Long> botAdminCount = new HashMap<Long, Long>() {{
        put(0L, 0L);
        put(2821869985L, 0L);
    }};

    private static final List<Long> screens = new ArrayList<Long>() {{
        add(0L);
        add(2821869985L);
    }};
    private static int currentScreenId = 0;

    public static void addBotScreen(long uin) {
        screens.add(uin);
        log.put(uin, new LimitLinkedQueue<String>(cacheLogSize));
        botAdminCount.put(uin, 0L);
    }

    public static Terminal terminal;
    public static TextGraphics textGraphics;

    public static boolean hasStart = false;
    public static PrintStream internalPrinter;

    public static void start() {
        if (hasStart) {
            return;
        }

        internalPrinter = System.out;

        hasStart = true;
        DefaultTerminalFactory defaultTerminalFactory = new DefaultTerminalFactory(internalPrinter, System.`in`, Charset.defaultCharset());

        int fontSize = 12;
        defaultTerminalFactory
                .setInitialTerminalSize(
                        new TerminalSize(
                                101, 60
                        )
                )
                .setTerminalEmulatorFontConfiguration(
                        new SwingTerminalFontConfiguration(
                                new Font("Monospaced", Font.PLAIN, fontSize)
                        )
                );
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
            } catch (Exception e1) {
                error("can not create terminal");
            }
        }
        textGraphics = terminal.newTextGraphics();

        Job lastJob = null;
        terminal.addResizeListener(new TerminalResizeListener() {

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
        new Thread(() -> {
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
                        MiraiConsole.CommandListener.commandChannel.offer(commandBuilder.toString());
                        emptyCommand();
                        break;
                    default:
                        if (keyStroke.getCharacter() != null) {
                            if (keyStroke.getCharacter().equals('\b')) {
                                deleteCommandChar();
                            }
                            if (Character.isLetterOrDigit(keyStroke.getCharacter()) || charList.contains(keyStroke.getCharacter())) {
                                addCommandChar(keyStroke.getCharacter());
                            }
                        }
                        break;
                }
            }
        }).start();
    }

    private static int getLeftScreenId() {
        int newId = currentScreenId - 1;
        if (newId < 0) {
            newId = screens.size() - 1;
        }
        return newId;
    }

    private static int getRightScreenId() {
        int newId = 1 + currentScreenId;
        if (newId >= screens.size()) {
            newId = 0;
        }
        return newId;
    }

    private static String getScreenName(int id) {
        return screens.get(id).equals(0L) ? "Console Screen" : "Bot: " + screens.get(id);
    }

    public static void clearRows(int row) {
        textGraphics.putString(
                0, row, " ".repeat(
                        terminal.terminalSize.columns
                )
        );
    }

    public static void drawFrame(String title) {
        int width = terminal.terminalSize.columns;
        int height = terminal.terminalSize.rows;
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);

        String mainTitle = "Mirai Console v0.01 Core v0.15";
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

        textGraphics.putString((width - title.length()) / 2 - leftName.length() - 2, 2, leftName + " << ");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.YELLOW);
        textGraphics.putString((width - title.length()) / 2, 2, title, SGR.BOLD);
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        String rightName = getScreenName(getRightScreenId());
        textGraphics.putString((width + title.length()) / 2 + 1, 2, ">> " + rightName);
    }

    public static void drawMainFrame(Number onlineBotCount) {
        drawFrame("Console Screen");
        int width = terminal.terminalSize.columns;
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        clearRows(4);
        textGraphics.putString(2, 4, "|Online Bots: " + onlineBotCount);
        textGraphics.putString(width - 2 - "Powered By Mamoe Technologies|".length(), 4, "Powered By Mamoe Technologies|");
    }

    public static void drawBotFrame(long qq, Number adminCount) {
        drawFrame("Bot: " + qq);
        int width = terminal.terminalSize.columns;
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        clearRows(4);
        textGraphics.putString(2, 4, "|Admins: " + adminCount);
        textGraphics.putString(width - 2 - "Add admins via commands|".length(), 4, "Add admins via commands|");
    }

    public static class LoggerDrawer {
        public static int currentHeight = 6;

        public static void drawLog(String string, boolean flush) {
            int maxHeight = terminal.terminalSize.rows - 4;
            int heightNeed = (string.length() / (terminal.terminalSize.columns - 6)) + 1;
            if (currentHeight + heightNeed > maxHeight) {
                cleanPage();
            }
            int width = terminal.terminalSize.columns - 7;
            String x = string;
            while (true) {
                if (x.equals("")) break;
                String toWrite = x.length() > width ? x.substring(0, width) : x;
                x = x.substring(toWrite.length());
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
            for (int index = 6; index < terminal.terminalSize.rows - 4; index++) {
                clearRows(index);
            }
            currentHeight = 6;
        }

        public static void redrawLogs(List<String> toDraw) {
            currentHeight = 6;
            int logsToDraw = 0;
            int vara = 0;
            for (String s : toDraw) {
                int heightNeed = (s.length() / (terminal.terminalSize.columns - 6)) + 1;
                vara += heightNeed;
                if (currentHeight + vara < terminal.terminalSize.rows - 4) {
                    logsToDraw++;
                } else {
                    break;
                }
            }
            for (int index = 0; index < logsToDraw; index++) {
                drawLog(toDraw.get(logsToDraw - index - 1), false);
            }
            if (terminal instanceof SwingTerminalFrame) {
                terminal.flush();
            }
        }
    }

    public static void pushLog(long uin, String str) {
        LimitLinkedQueue<String> queue = log.get(uin);
        if (queue != null) {
            queue.push(str);
            if (uin == screens.get(currentScreenId)) {
                drawLog(str);
            }
        }
    }

    public static StringBuilder commandBuilder = new StringBuilder();

    public static void redrawCommand() {
        int height = terminal.terminalSize.rows;
        int width = terminal.terminalSize.columns;
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

    private static void addCommandChar(char c) {
        if (commandBuilder.isEmpty() && c != '/') {
            addCommandChar('/');
        }
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        int height = terminal.terminalSize.rows;
        commandBuilder.append(c);
        if (terminal instanceof SwingTerminalFrame) {
            redrawCommand();
        } else {
            textGraphics.putString(6 + commandBuilder.length(), height - 3, String.valueOf(c));
        }
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private static void deleteCommandChar() {
        if (!commandBuilder.isEmpty()) {
            commandBuilder = new StringBuilder(commandBuilder.toString().substring(0, commandBuilder.length() - 1));
        }
        int height = terminal.terminalSize.rows;
        if (terminal instanceof SwingTerminalFrame) {
            redrawCommand();
            terminal.flush();
        } else {
            textGraphics.putString(7 + commandBuilder.length(), height - 3, " ");
        }
    }

    public static Job lastEmpty;

    private static void emptyCommand() {
        commandBuilder = new StringBuilder();
        if (terminal instanceof SwingTerminal) {
            redrawCommand();
            terminal.flush();
        } else {
            lastEmpty = GlobalScope.launch(CoroutineName.REPLACE_CURRENT, ContinuationInterceptor.Key.create(Job.class, lastEmpty)) {
                delay(100);
                if (lastEmpty == coroutineContext[Job]) {
                    terminal.clearScreen();

                    update();
                    redrawCommand();
                    redrawLogs(log.get(screens.get(currentScreenId)));
                }
            };
        }
    }

    public static void update() {
        switch (screens.get(currentScreenId)) {
            case 0L:
                drawMainFrame(screens.size() - 1);
                break;
            default:
                drawBotFrame(
                        screens.get(currentScreenId),
                        0
                );
                break;
        }
        redrawLogs(log.get(screens.get(currentScreenId)));
    }
}

public class LimitLinkedQueue<T> extends LinkedList<T> implements List<T> {
    private final int limit;

    public LimitLinkedQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public void push(T e) {
        if (size() >= limit) {
            pollLast();
        }
        super.push(e);
    }
}