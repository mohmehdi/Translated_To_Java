
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
import java.awt.Font;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("UNCHECKED")
public class MiraiConsoleUI {
    public static final int cacheLogSize = 50;

    public static final List<String> log = new LinkedList<>();
    public static final List<Long> botAdminCount = new LinkedList<>();

    private static final List<Long> screens = new LinkedList<>();
    private static int currentScreenId = 0;

    public static void addBotScreen(long uin) {
        screens.add(uin);
        log.add(new LimitLinkedQueue<>(cacheLogSize));
        botAdminCount.add(0L);
    }

    private static Terminal terminal;
    private static TextGraphics textGraphics;

    private static boolean hasStart = false;
    private static PrintStream internalPrinter;

    public static void start() {
        if (hasStart) {
            return;
        }

        internalPrinter = System.out;

        hasStart = true;
        DefaultTerminalFactory defaultTerminalFactory = new DefaultTerminalFactory(internalPrinter, System.in, Charset.defaultCharset());

        int fontSize = 12;
        defaultTerminalFactory
            .setInitialTerminalSize(
                new TerminalSize(101, 60)
            )
            .setTerminalEmulatorFontConfiguration(
                SwingTerminalFontConfiguration.newInstance(
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
            } catch (Exception ex) {
                error("can not create terminal");
            }
        }
        textGraphics = terminal.newTextGraphics();

        Job lastJob = null;
        terminal.addResizeListener(new TerminalResizeListener() {

        });

        if (!(terminal instanceof SwingTerminalFrame)) {
            System.setOut(new PrintStream(new OutputStream() {
                StringBuilder builder = new StringBuilder();

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

        List<Character> charList = List.of(',', '.', '/', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '=', '+', '!', ' ');
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
                            if (keyStroke.getCharacter().toInt() == 8) {
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
        return switch (screens.get(id)) {
            case 0L -> "Console Screen";
            default -> "Bot: " + screens.get(id);
        };
    }

    public static void clearRows(int row) {
        textGraphics.putString(0, row, " ".repeat(terminal.getTerminalSize().getColumns()));
    }

    public static void drawFrame(String title) {
        int width = terminal.getTerminalSize().getColumns();
        int height = terminal.getTerminalSize().getRows();
        terminal.setBackgroundColor(TextColor.ANSI.DEFAULT);

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
        textGraphics.putString((width - title.length()) / 2 - (leftName.length() + 4), 2, leftName + " << ");
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
        int width = terminal.getTerminalSize().getColumns();
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        clearRows(4);
        textGraphics.putString(2, 4, "|Online Bots: " + onlineBotCount);
        textGraphics.putString(width - 2 - "Powered By Mamoe Technologies|".length(), 4, "Powered By Mamoe Technologies|");
    }

    public static void drawBotFrame(long qq, Number adminCount) {
        drawFrame("Bot: " + qq);
        int width = terminal.getTerminalSize().getColumns();
        textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        clearRows(4);
        textGraphics.putString(2, 4, "|Admins: " + adminCount);
        textGraphics.putString(width - 2 - "Add admins via commands|".length(), 4, "Add admins via commands|");
    }

    public static void pushLog(long uin, String str) {
        log.add(uin, str);
        if (uin == screens.get(currentScreenId)) {
            drawLog(str);
        }
    }

    public static void update() {
        switch (screens.get(currentScreenId)) {
            case 0L -> drawMainFrame(screens.size() - 1);
            default -> drawBotFrame(screens.get(currentScreenId), 0);
        }
        redrawLogs(log.get(screens.get(currentScreenId)));
    }

    public static void drawLog(String string, boolean flush) {
        int maxHeight = terminal.getTerminalSize().getRows() - 4;
        int heightNeed = (string.length() / (terminal.getTerminalSize().getColumns() - 6)) + 1;
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

    public static void redrawLogs(List<String> toDraw) {
        currentHeight = 6;
        int logsToDraw = 0;
        int vara = 0;
        for (String s : toDraw) {
            int heightNeed = (s.length() / (terminal.getTerminalSize().getColumns() - 6)) + 1;
            vara += heightNeed;
            if (currentHeight + vara < terminal.getTerminalSize().getRows() - 4) {
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

    public static void redrawCommand() {
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

    private static void addCommandChar(char c) {
        if (commandBuilder.length() == 0 && c != '/') {
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

    private static void deleteCommandChar() {
        if (!commandBuilder.isEmpty()) {
            commandBuilder = new StringBuilder(commandBuilder.substring(0, commandBuilder.length() - 1));
        }
        int height = terminal.getTerminalSize().getRows();
        if (terminal instanceof SwingTerminalFrame) {
            redrawCommand();
        } else {
            textGraphics.putString(7 + commandBuilder.length(), height - 3, " ");
        }
    }

    private static Job lastEmpty = null;

    private static void emptyCommand() {
        commandBuilder = new StringBuilder();
        if (terminal instanceof SwingTerminal) {
            redrawCommand();
            terminal.flush();
        } else {
            lastEmpty = GlobalScope.launch(() -> {
                delay(100);
                if (lastEmpty == coroutineContext[Job]) {
                    terminal.clearScreen();
                    update();
                    redrawCommand();
                    redrawLogs(log.get(screens.get(currentScreenId)));
                }
            });
        }
    }
}

class LimitLinkedQueue<T> extends LinkedList<T> implements List<T> {
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