
package net.mamoe.mirai.utils;

import kotlinx.coroutines.CoroutineName;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.io.ByteWriteChannel;
import kotlinx.coroutines.io.jvm.nio.copyTo;
import kotlinx.coroutines.io.reader;
import kotlinx.coroutines.sync.Mutex;
import kotlinx.coroutines.sync.withLock;
import kotlinx.coroutines.withContext;
import kotlinx.io.core.IoBuffer;
import kotlinx.io.core.use;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.network.BotNetworkHandler;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.Lock;


@SuppressWarnings("unused")
public class BotConfigurationJvm {
    private static final Mutex loginSolverLock = new Mutex();

    private static final double IGNORE_RATE = 0.95;

    public static ByteWriteChannel writeChannel(File file, CoroutineContext coroutineContext) throws IOException {
        CompletableFuture<ByteWriteChannel> future = CompletableFuture.supplyAsync(() -> {
            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                Channel<Byte> channel = GlobalScope.writer(coroutineName("file-writer") + coroutineContext, autoFlush = true) {
                    try {
                        channel.write(randomAccessFile.getChannel().toReadChannel().toByteArray())
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            randomAccessFile.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                return new ByteWriteChannel(channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return future.join();
    }

    public static String createCharImg(BufferedImage image, int outputWidth, double ignoreRate) {
        int newHeight = (int) (image.getHeight() * ((double) outputWidth / image.getWidth()));
        Image tmp = image.getScaledInstance(outputWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledImage = new BufferedImage(outputWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        int background = gray(scaledImage.getRGB(0, 0));

        StringBuilder result = new StringBuilder();

        int minXPos = outputWidth;
        int maxXPos = 0;

        List<StringBuilder> lines = new ArrayList<>();

        for (int y = 0; y < scaledImage.getHeight(); y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < scaledImage.getWidth(); x++) {
                int gray = gray(scaledImage.getRGB(x, y));
                if (grayCompare(gray, background)) {
                    line.append(" ");
                } else {
                    line.append("#");
                    if (x < minXPos) {
                        minXPos = x;
                    }
                    if (x > maxXPos) {
                        maxXPos = x;
                    }
                }
            }
            if (!line.toString().isBlank()) {
                lines.add(line);
            }
        }

        for (StringBuilder line : lines) {
            result.append(line.substring(minXPos, maxXPos)).append("\n");
        }

        return result.toString();
    }

    private static int gray(int rgb) {
        int r = (rgb & 0xff0000) >> 16;
        int g = (rgb & 0x00ff00) >> 8;
        int b = rgb & 0x0000ff;
        return (r * 30 + g * 59 + b * 11 + 50) / 100;
    }

    private static boolean grayCompare(int g1, int g2) {
        return Math.min(g1, g2) / (double) Math.max(g1, g2) >= IGNORE_RATE;
    }
}

public interface LoginSolverInputReader {
    CompletableFuture<String> read(String question);

    default CompletableFuture<String> invoke(String question) {
        return read(question);
    }
}
@SuppressWarnings("unused")
class DefaultLoginSolver extends LoginSolver {
    private final LoginSolverInputReader reader;
    private final MiraiLogger overrideLogger;
    private final Lock loginSolverLock = new Mutex();

    public DefaultLoginSolver(LoginSolverInputReader reader, MiraiLogger overrideLogger) {
        this.reader = reader != null ? reader : new DefaultLoginSolverInputReader();
        this.overrideLogger = overrideLogger;
    }

    public DefaultLoginSolver() {
        this(null, null);
    }

    public MiraiLogger getLogger(Bot bot) {
        if (overrideLogger != null) {
            return overrideLogger;
        }
        return bot.getLogger();
    }

    @Override
    public String onSolvePicCaptcha(Bot bot, IoBuffer data) {
        return withLock(loginSolverLock, () -> {
            MiraiLogger logger = getLogger(bot);
            File tempFile;
            try {
                tempFile = File.createTempFile("", ".png");
                tempFile.deleteOnExit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                withContext(Dispatchers.IO, () -> {
                    tempFile.createNewFile();
                    logger.info("需要图片验证码登录, 验证码为 4 字母");
                    try {
                        tempFile.write(data);
                        logger.info("将会显示字符图片. 若看不清字符图片, 请查看文件 " + tempFile.getAbsolutePath());
                    } catch (Exception e) {
                        logger.info("无法写出验证码文件(" + e.getMessage() + "), 请尝试查看以上字符图片");
                    }

                    try {
                        ImageIO.read(tempFile).createCharImg();
                    } catch (IOException e) {
                        logger.info("无法创建字符图片. 请查看文件");
                    }
                });
            } catch (Exception e) {
                logger.info("Error occurred: " + e.getMessage());
            }

            logger.info("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
            String result = reader.read("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
            if (result != null && result.length() == 4) {
                logger.info("正在提交[" + result + "]中...");
                return result;
            }
            return null;
        });
    }

    @Override
    public String onSolveSliderCaptcha(Bot bot, String url) {
        return withLock(loginSolverLock, () -> {
            MiraiLogger logger = getLogger(bot);
            logger.info("需要滑动验证码");
            logger.info("请在任意浏览器中打开以下链接并完成验证码. ");
            logger.info("完成后请输入任意字符 ");
            logger.info(url);
            reader.read("完成后请输入任意字符");
            logger.info("正在提交中...");
            return null;
        });
    }

    @Override
    public String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        return withLock(loginSolverLock, () -> {
            MiraiLogger logger = getLogger(bot);
            logger.info("需要进行账户安全认证");
            logger.info("该账户有[设备锁]/[不常用登陆地点]/[不常用设备登陆]的问题");
            logger.info("完成以下账号认证即可成功登陆|理论本认证在mirai每个账户中最多出现1次");
            logger.info("请将该链接在QQ浏览器中打开并完成认证, 成功后输入任意字符");
            logger.info("这步操作将在后续的版本中优化");
            logger.info(url);
            reader.read("完成后请输入任意字符");
            logger.info("正在提交中...");
            return null;
        });
    }

}

class DefaultLoginSolverInputReader implements LoginSolverInputReader {
    @Override
    public String read(String question) {
        return System.console().readLine();
    }
}

@SuppressWarnings({"ClassName", "PropertyName", "unused"})
public class BotConfiguration {
    private MiraiLogger botLoggerSupplier = bot -> new DefaultLogger("Bot(" + bot.getUin() + ")");
    private MiraiLogger networkLoggerSupplier = handler -> new DefaultLogger("Network(" + handler.getBot().getUin() + ")");
    private DeviceInfoProvider deviceInfo = null;

    private CoroutineContext parentCoroutineContext = EmptyCoroutineContext.INSTANCE;

    private long heartbeatPeriodMillis = 60 * 1000; // assuming secondsToMillis is a constant
    private long heartbeatTimeoutMillis = 2 * 1000; // assuming secondsToMillis is a constant
    private long firstReconnectDelayMillis = 5 * 1000; // assuming secondsToMillis is a constant
    private long reconnectPeriodMillis = 60 * 1000; // assuming secondsToMillis is a constant
    private int reconnectionRetryTimes = 3;

    private LoginSolver loginSolver = defaultLoginSolver;

    public static final BotConfiguration Default = new BotConfiguration();

    public void unaryPlus(FileBasedDeviceInfo deviceInfo) {
        this.deviceInfo = context -> deviceInfo.loadAsDeviceInfo(new File(context.getFilesDir(), deviceInfo.filepath));
    }

    public void unaryPlus(FileBasedDeviceInfo.ByDeviceDotJson deviceInfo) {
        this.deviceInfo = context -> deviceInfo.loadAsDeviceInfo(new File(context.getFilesDir(), "device.json"));
    }

    public void unaryPlus(_NoNetworkLog noNetworkLog) {
        this.networkLoggerSupplier = noNetworkLog.supplier;
    }

    public static final _NoNetworkLog NoNetworkLog = _NoNetworkLog.INSTANCE;


}


class FileBasedDeviceInfo {
    private final String filepath;

    public FileBasedDeviceInfo(String filepath) {
        this.filepath = filepath;
    }

    public static class ByDeviceDotJson {
    }
}