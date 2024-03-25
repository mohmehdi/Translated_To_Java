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
import net.mamoe.mirai.console.MiraiConsoleTerminalUI.LoggerDrawer;
import net.mamoe.mirai.console.MiraiConsoleTerminalUI.LoggerDrawer.cleanPage;
import net.mamoe.mirai.console.MiraiConsoleTerminalUI.LoggerDrawer.drawLog;
import net.mamoe.mirai.console.MiraiConsoleTerminalUI.LoggerDrawer.redrawLogs;
import java.awt.Font;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MiraiConsoleTerminalUI implements MiraiConsoleUI {
    static final int cacheLogSize = 50;
    static String mainTitle = "Mirai Console v0.01 Core v0.15";

    @Override
    public void pushVersion(String consoleVersion, String consoleBuild, String coreVersion) {
        mainTitle = "Mirai Console(Terminal) " + consoleVersion + " " + consoleBuild + " Core " + coreVersion;
    }

    static Queue<String> log0 = new ConcurrentLinkedDeque<>();
    static {
        log0.add(null);
    }
    static Map<Long, Queue<String>> log = new ConcurrentHashMap<>();
    static {
        log.put(0L, log0);
    }

    static Map<Long, Integer> botAdminCount = new ConcurrentHashMap<>();

    static List<Long> screens = new ArrayList<>();
    static {
        screens.add(0L);
    }
    static int currentScreenId = 0;

    static Terminal terminal;
    static TextGraphics textGraphics;

    static boolean hasStart = false;
    static PrintStream internalPrinter;

    @Override
    public void pushLog(long identity, String message) {
        Queue<String> logIdentity = log.get(identity);
        if (logIdentity == null) {
            logIdentity = new ConcurrentLinkedDeque<>();
            log.put(identity, logIdentity);
        }
        logIdentity.add(message);
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

    static boolean requesting = false;
    static String requestResult;

    @Override
    public String requestInput(String question) {
        requesting = true;
        while (requesting) {
            delay(100);
        }
        return requestResult;
    }

    public static void provideInput(String input) {
        if (requesting) {
            requestResult = input;
            requesting = false;
        } else {
            MiraiConsole.CommandListener.commandChannel.offer(
                    commandBuilder.toString()
            );
        }
    }

    static Map<Long, Integer> botAdminCount = new ConcurrentHashMap<>();

    @Override
    public void pushBotAdminStatus(long identity, List<Long> admins) {
        botAdminCount.put(identity, admins.size());
    }



    public class LoggerDrawer {
    private static int currentHeight = 6;

    public static void drawLog(String string, boolean flush = true) {
        TerminalSize terminalSize = terminal.terminalSize;
        int maxHeight = terminalSize.rows - 4;
        int heightNeed = (string.length() / (terminalSize.columns - 6)) + 1;
        if (heightNeed - 1 > maxHeight) {
            return;
        }
        if (currentHeight + heightNeed > maxHeight) {
            cleanPage();
        }
        int width = terminalSize.columns - 7;
        String x = string;
        while (true) {
            if (x.isEmpty()) break;
            String toWrite;
            if (x.length() > width) {
                toWrite = x.substring(0, width);
                x = x.substring(width);
            } else {
                toWrite = x;
                x = "";
            }
            try {
                TextGraphics textGraphics = terminal.textGraphics;
                SimpleAttributeSet attributeSet = new SimpleAttributeSet();
                StyleConstants.setForeground(attributeSet, TextColor.ANSI.GREEN.getColor());
                StyleConstants.setBackground(attributeSet, TextColor.ANSI.DEFAULT.getColor());
                StyleConstants.setItalic(attributeSet, true);
                StyledDocument document = textGraphics.getDocument();
                document.insertString(textGraphics.getLength(), toWrite, attributeSet);
            } catch (BadLocationException ignored) {
            }
            ++currentHeight;
        }
        if (flush && terminal instanceof SwingTerminalFrame) {
            ((SwingTerminalFrame) terminal).flush();
        }
    }

    public static void cleanPage() {
        for (int index = 6; index < terminal.terminalSize.rows - 4; index++) {
            clearRows(index);
        }
        currentHeight = 6;
    }

    public static void redrawLogs(Queue<String> toDraw) {
        currentHeight = 6;
        int logsToDraw = 0;
        int vara = 0;
        List<String> toPrint = new ArrayList<>();
        Iterator<String> iterator = toDraw.iterator();
        while (iterator.hasNext()) {
            String it = iterator.next();
            int heightNeed = (it.length() / (terminal.terminalSize.columns - 6)) + 1;
            vara += heightNeed;
            if (currentHeight + vara < terminal.terminalSize.rows - 4) {
                logsToDraw++;
                toPrint.add(it);
            } else {
                break;
            }
        }
        Iterator<String> reverseIterator = toPrint.iterator();
        while (reverseIterator.hasNext()) {
            String it = reverseIterator.next();
            drawLog(it, false);
        }
        if (terminal instanceof SwingTerminalFrame) {
            ((SwingTerminalFrame) terminal).flush();
        }
    }
}

    static StringBuilder commandBuilder = new StringBuilder();

    static void redrawCommand() {
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
    }

    private static void deleteCommandChar() {
        if (commandBuilder.length() > 0) {
            commandBuilder = new StringBuilder(commandBuilder.substring(0, commandBuilder.length() - 1));
        }
        int height = terminal.getTerminalSize().getRows();
        if (terminal instanceof SwingTerminalFrame) {
            redrawCommand();
            terminal.flush();
        } else {
            textGraphics.putString(7 + commandBuilder.length(), height - 3, " ");
        }
    }

    static void emptyCommand() {
        commandBuilder = new StringBuilder();
        if (terminal instanceof SwingTerminal) {
            redrawCommand();
            terminal.flush();
        } else {
            GlobalScope.launch(new Runnable() {

            });
        }
    }

    public static void start() {
        if (hasStart) {
            return;
        }

        internalPrinter = System.out;

        hasStart = true;

        TerminalFactory defaultTerminalFactory = new SwingTerminalFactory(internalPrinter, new NonBlockingInputStream(), Charset.defaultCharset());

        int fontSize = 12;
        DefaultTerminalFactory dtf = (DefaultTerminalFactory) defaultTerminalFactory;
        dtf.setInitialTerminalSize(new TerminalSize(101, 60));
        dtf.setTerminalEmulatorFontConfiguration(
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
            } catch (Exception e1) {
                error("can not create terminal");
            }
        }

        textGraphics = terminal.newTextGraphics();

        Future<?> lastJob = null;
        terminal.addResizeListener(new TerminalResizeListener() {

        });

        if (!(terminal instanceof SwingTerminalFrame)) {
            OutputStream outputStream = new ByteArrayOutputStream();
            System.setOut(new NonBlockingPrintStream(outputStream));

            terminal.setOutput(new Printer() {
                private StringBuilder builder = new StringBuilder();

                @Override
                public void print(String s) {
                    for (char c : s.toCharArray()) {
                        if (c == '\n') {
                            pushLog(0, builder.toString());
                            builder.setLength(0);
                        } else {
                            builder.append(c);
                        }
                    }
                }
            });
        }

        System.setErr(System.out);

        update();

        List<Character> charList = new ArrayList<>();
        charList.add(',');
        charList.add('.');
        charList.add('/');
        charList.add('@');
        charList.add('#');
        charList.add('$');
        charList.add('%');
        charList.add('^');
        charList.add('&');
        charList.add('*');
        charList.add('(');
        charList.add(')');
        charList.add('_');
        charList.add('=');
        charList.add('+');
        charList.add('!');
        charList.add(' ');

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> thread = executor.submit(() -> {
            while (true) {
                try {
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
                                if (keyStroke.getCharacter().equals('\b')) {
                                    deleteCommandChar();
                                } else if (Character.isLetterOrDigit(keyStroke.getCharacter()) || charList.contains(keyStroke.getCharacter())) {
                                    addCommandChar(keyStroke.getCharacter());
                                }
                            }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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
        int newId = currentScreenId + 1;
        if (newId >= screens.size()) {
            newId = 0;
        }
        return newId;
    }

private String getScreenName(int id) {
    if (id >= 0 && id < screens.length) {
        if (screens[id].equals("0")) {
            return "Console Screen";
        } else {
            return "Bot: " + screens[id];
        }
    } else {
        return "Invalid Screen ID";
    }
}

    void clearRows(int row) {
        textGraphics.putString(
                0, row,
                String.format("%" + terminal.terminalSize.columns + "s", " "));
    }

    public void update() {
        switch ((LongSummaryStatistics) screens[currentScreenId]) {
            case 0L:
                drawMainFrame(screens.length - 1);
                break;
            default:
                drawBotFrame(screens[currentScreenId], 0);
                break;
        }
        redrawLogs(log[(LongSummaryStatistics) screens[currentScreenId]]);
    }

       public void drawFrame(String title) {
        int width = terminal.getTerminalSize().getColumns();
        int height = terminal.getTerminalSize().getRows();

        terminal.setBackground(InfoCmp.Capability.default_background);

        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.GREEN));
        textGraphics.putString((width - mainTitle.length()) / 2, 1, mainTitle);
        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.DEFAULT));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.DEFAULT));
        textGraphics.putString(2, 3, "-".repeat(width - 4));
        textGraphics.putString(2, 5, "-".repeat(width - 4));
        textGraphics.putString(2, height - 4, "-".repeat(width - 4));
        textGraphics.putString(2, height - 3, "|>>>");
        textGraphics.putString(width - 3, height - 3, "|");
        textGraphics.putString(2, height - 2, "-".repeat(width - 4));

        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.DEFAULT));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.DEFAULT));
        String leftName = getScreenName(getLeftScreenId());

        textGraphics.putString((width - title.length()) / 2 - leftName.length() - 3, 2, leftName + " << ");
        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.YELLOW));
        textGraphics.putString((width - title.length()) / 2, 2, title);
        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.DEFAULT));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.DEFAULT));
        String rightName = getScreenName(getRightScreenId());
        textGraphics.putString((width + title.length()) / 2 + 1, 2, ">> " + rightName);
    }

    public void drawMainFrame(Number onlineBotCount) {
        drawFrame("Console Screen");
        int width = terminal.getTerminalSize().columns;
        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.DEFAULT));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.DEFAULT));
        clearRows(4);
        textGraphics.putString(2, 4, "|Online Bots: " + onlineBotCount);
        textGraphics.putString(
                width - 2 - "Powered By Mamoe Technologies|".length(),
                4,
                "Powered By Mamoe Technologies|"
        );
    }

    public void drawBotFrame(Long qq, Number adminCount) {
        drawFrame("Bot: " + qq);
        int width = terminal.getTerminalSize().columns;
        textGraphics.foregroundColor(AttributedStyle.DEFAULT.foreground(AttributedStyle.DEFAULT));
        textGraphics.backgroundColor(AttributedStyle.DEFAULT.background(AttributedStyle.DEFAULT));
        clearRows(4);
        textGraphics.putString(2, 4, "|Admins: " + adminCount);
        textGraphics.putString(
                width - 2 - "Add admins via commands|".length(),
                4,
                "Add admins via commands|"
        );
    }
    
    public class LimitLinkedQueue<T> extends ConcurrentLinkedDeque<T> {
        private final int limit = 50;


        public LimitLinkedQueue(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean push(T e) {
            if (size() >= limit) {
                pollLast();
            }
            return super.push(e);
        }
    }

}