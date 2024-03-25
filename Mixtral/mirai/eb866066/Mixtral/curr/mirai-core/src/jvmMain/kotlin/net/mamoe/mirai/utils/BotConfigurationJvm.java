
package net.mamoe.mirai.utils;

import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Mutex;
import javax.imageio.ImageIO;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.channels.BufferOverflow;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.ProducerScope;
import kotlinx.coroutines.channels.SendChannel;
import kotlinx.coroutines.io.ByteWriteChannel;
import kotlinx.coroutines.io.jvm.nio.KotlinxChannelFactory;

public class BotConfigurationJvm {

    public static ByteChannel writeChannel(Path file, CoroutineContext coroutineContext) {
        try {
            return Files.newByteChannel(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final ConcurrentHashMap<String, Lock> loginSolverLockMap = new ConcurrentHashMap<>();


    public static String createCharImg(BufferedImage image, int outputWidth, double ignoreRate) {
        int newHeight = (int) (image.getHeight() * ((double) outputWidth / image.getWidth()));
        Image tmp = image.getScaledInstance(outputWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage newImage = new BufferedImage(outputWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);

        int[] rgb = new int[3];
        double gray;
        double minGray;
        double maxGray;

        List<StringBuilder> lines = Arrays.stream(new int[newHeight])
                .mapToObj(i -> new StringBuilder(outputWidth))
                .collect(Collectors.toList());

        int minXPos = outputWidth;
        int maxXPos = 0;

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < outputWidth; x++) {
                int pixel = newImage.getRGB(x, y);
                rgb[0] = (pixel & 0xff0000) >> 16;
                rgb[1] = (pixel & 0x00ff00) >> 8;
                rgb[2] = (pixel & 0x0000ff);
                gray = (rgb[0] * 0.3 + rgb[1] * 0.59 + rgb[2] * 0.11) / 255.0;

                if (x == 0 && y == 0) {
                    minGray = maxGray = gray;
                } else {
                    minGray = Math.min(minGray, gray);
                    maxGray = Math.max(maxGray, gray);
                }

                if (gray > ignoreRate * maxGray) {
                    lines.get(y).setCharAt(x, '#');
                    if (x < minXPos) {
                        minXPos = x;
                    }
                    if (x > maxXPos) {
                        maxXPos = x;
                    }
                } else {
                    lines.get(y).setCharAt(x, ' ');
                }
            }
        }

        StringBuilder result = new StringBuilder();
        for (StringBuilder line : lines) {
            if (!line.toString().isBlank()) {
                result.append(line.substring(minXPos, maxXPos)).append("\n");
            }
        }

        return result.toString();
    }
}
public interface LoginSolverInputReader {
    String read(String question) throws ExecutionException, InterruptedException;

    default String invoke(String question) throws ExecutionException, InterruptedException {
        return read(question);
    }
}
public class DefaultLoginSolverInputReader implements LoginSolverInputReader {
    @Override
    public @Nullable String read(String question) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}

public class DefaultLoginSolver implements LoginSolver {
    private final LoginSolverInputReader reader;
    private final MiraiLogger overrideLogger;

    public DefaultLoginSolver(LoginSolverInputReader reader, MiraiLogger overrideLogger) {
        this.reader = reader;
        this.overrideLogger = overrideLogger;
    }

    public DefaultLoginSolver() {
        this(new DefaultLoginSolverInputReader(), null);
    }

    private MiraiLogger getLogger(Bot bot) {
        if (overrideLogger != null) {
            return overrideLogger;
        }
        return bot.getLogger();
    }

    @Override
    public @Nullable String onSolvePicCaptcha(Bot bot, @NotNull IoBuffer data) {
        Mutex loginSolverLock = new Mutex();
        CoroutineContext Dispatchers.IO = new ExecutorCoroutineDispatcher(Executors.newSingleThreadExecutor());

        File tempFile = File.createTempFile("temp", ".png");
        tempFile.deleteOnExit();

        try (FileChannel fileChannel = new RandomAccessFile(tempFile, "rw").getChannel()) {
            WritableByteChannel writeChannel = Channels.newWritableByteChannel(fileChannel);
            ByteWriteChannel byteWriteChannel = KotlinxChannelFactory.newByteWriteChannel(writeChannel, 8192, BufferOverflow.SUSPEND);
            GlobalScope.launch(Dispatchers.IO) {
                data.copyTo(byteWriteChannel);
                byteWriteChannel.close();
            };
        } catch (IOException e) {
            e.printStackTrace();
        }

        getLogger(bot).info("需要图片验证码登录, 验证码为 4 字母");
        getLogger(bot).info("将会显示字符图片. 若看不清字符图片, 请查看文件 " + tempFile.getAbsolutePath());

        BufferedImage img = ImageIO.read(tempFile);
        if (img != null) {
            getLogger(bot).info(img.createCharImg());
        } else {
            getLogger(bot).info("无法创建字符图片. 请查看文件");
        }

        getLogger(bot).info("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
        String input = reader.read(getLogger(bot), "请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
        if (input == null || input.isEmpty() || input.length() != 4) {
            return null;
        }

        getLogger(bot).info("正在提交[" + input + "]中...");
        return input;
    }

    @Override
    public @Nullable String onSolveSliderCaptcha(Bot bot, String url) {
        Mutex loginSolverLock = new Mutex();

        getLogger(bot).info("需要滑动验证码");
        getLogger(bot).info("请在任意浏览器中打开以下链接并完成验证码. ");
        getLogger(bot).info("完成后请输入任意字符 ");
        getLogger(bot).info(url);

        getLogger(bot).info("正在提交中...");
        return reader.read(getLogger(bot), "完成后请输入任意字符");
    }

    @Override
    public @Nullable String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        Mutex loginSolverLock = new Mutex();

        getLogger(bot).info("需要进行账户安全认证");
        getLogger(bot).info("该账户有[设备锁]/[不常用登陆地点]/[不常用设备登陆]的问题");
        getLogger(bot).info("完成以下账号认证即可成功登陆|理论本认证在mirai每个账户中最多出现1次");
        getLogger(bot).info("请将该链接在QQ浏览器中打开并完成认证, 成功后输入任意字符");
        getLogger(bot).info("这步操作将在后续的版本中优化");
        getLogger(bot).info(url);

        getLogger(bot).info("正在提交中...");
        return reader.read(getLogger(bot), "完成后请输入任意字符");
    }
}


public class FileBasedDeviceInfo {
    private final String filepath;

    public FileBasedDeviceInfo(String filepath) {
        this.filepath = filepath;
    }

    public static class ByDeviceDotJson {
    }
}



public class BotConfiguration {
    private Function1<Bot, MiraiLogger> botLoggerSupplier;
    private Function1<BotNetworkHandler, MiraiLogger> networkLoggerSupplier;
    private Function2<Context, DeviceInfo> deviceInfo;
    private CoroutineContext parentCoroutineContext;
    private long heartbeatPeriodMillis;
    private long heartbeatTimeoutMillis;
    private long firstReconnectDelayMillis;
    private long reconnectPeriodMillis;
    private int reconnectionRetryTimes;
    private LoginSolver loginSolver;

    public BotConfiguration() {
        this.botLoggerSupplier = (bot) -> DefaultLogger.INSTANCE;
        this.networkLoggerSupplier = (botNetworkHandler) -> DefaultLogger.INSTANCE;
        this.parentCoroutineContext = EmptyCoroutineContext.INSTANCE;
        this.heartbeatPeriodMillis = Duration.ofSeconds(60).toMillis();
        this.heartbeatTimeoutMillis = Duration.ofSeconds(2).toMillis();
        this.firstReconnectDelayMillis = Duration.ofSeconds(5).toMillis();
        this.reconnectPeriodMillis = Duration.ofSeconds(60).toMillis();
        this.reconnectionRetryTimes = 3;
        this.loginSolver = DefaultLoginSolver.INSTANCE;
    }
    @SuppressWarnings("unused")
    public static void unaryPlus(FileBasedDeviceInfo fileBasedDeviceInfo) {
        fileBasedDeviceInfo.loadAsDeviceInfo(new File(fileBasedDeviceInfo.filepath));
    }

    @SuppressWarnings("unused")
    public static void unaryPlus(FileBasedDeviceInfo.ByDeviceDotJson fileBasedDeviceInfo) {
        fileBasedDeviceInfo.loadAsDeviceInfo(new File("device.json"));
    }

    @SuppressWarnings("unused")
    public static void unaryPlus(_NoNetworkLog noNetworkLog) {
        noNetworkLog.getNetworkLoggerSupplier().get();
    }

    }



    public static class FileBasedDeviceInfo {
        public static class ByDeviceDotJson {
        }
    }
