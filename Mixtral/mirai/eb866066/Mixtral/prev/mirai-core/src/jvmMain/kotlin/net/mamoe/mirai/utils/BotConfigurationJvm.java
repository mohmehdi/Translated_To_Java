
package net.mamoe.mirai.utils;

import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Mutex;

import javax.imageio.ImageIO;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.ChannelResult;
import kotlinx.coroutines.channels.ProducerScope;
import kotlinx.coroutines.channels.SendChannel;
import kotlinx.coroutines.coroutineScope;
import kotlinx.coroutines.io.ByteWriteChannel;
import kotlinx.coroutines.io.jvm.nio.FileChannelBasedWriteChannel;
import kotlinx.io.core.IoBuffer;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.NormalMember;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.logger.BotLogger;
import net.mamoe.mirai.logger.MiraiLogger;
import net.mamoe.mirai.network.BotNetworkHandler;
import net.mamoe.mirai.network.LoginSolver;
import net.mamoe.mirai.network.LoginSolverContext;
import net.mamoe.mirai.network.LoginSolverResult;
import net.mamoe.mirai.network.NetworkHandler;
import net.mamoe.mirai.network.NetworkLoginInfo;
import net.mamoe.mirai.network.NetworkLoginReady;
import net.mamoe.mirai.network.NetworkSession;
import net.mamoe.mirai.network.session.LoginSessionContext;
import net.mamoe.mirai.network.session.NetworkSessionContextImpl;
import net.mamoe.mirai.utils.MiraiLogger.SilentLogger;
import net.mamoe.mirai.utils.MiraiLogger.DefaultLogger;


public class BotConfigurationJvm {

    private Mutex loginSolverLock = new ReentrantMutex();

    @NotNull
    private WritableByteChannel writeChannel(@NotNull File file, @NotNull CoroutineContext coroutineContext) throws IOException {
        FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
        WritableByteChannel byteChannel = Channels.newWritableByteChannel(channel);
        FileInputStream inputStream = new FileInputStream(file);
        long copied = inputStream.getChannel().transferTo(0, file.length(), byteChannel);
        channel.truncate(copied);
        return byteChannel;
    }

    @NotNull
    private String createCharImg(@NotNull BufferedImage image, int outputWidth, double ignoreRate) {
        int newHeight = (int) (outputWidth * 1.0 * image.getHeight() / image.getWidth());
        Image tmp = image.getScaledInstance(outputWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage newImage = new BufferedImage(outputWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);

        int background = gray(newImage.getRGB(0, 0));

        StringBuilder stringBuilder = new StringBuilder();
        int minXPos = outputWidth;
        int maxXPos = 0;

        for (int y = 0; y < newImage.getHeight(); y++) {
            StringBuilder builderLine = new StringBuilder();
            for (int x = 0; x < newImage.getWidth(); x++) {
                int gray = gray(newImage.getRGB(x, y));
                if (grayCompare(gray, background, ignoreRate)) {
                    builderLine.append(" ");
                } else {
                    builderLine.append("#");
                    if (x < minXPos) {
                        minXPos = x;
                    }
                    if (x > maxXPos) {
                        maxXPos = x;
                    }
                }
            }
            if (builderLine.toString().isBlank()) {
                continue;
            }
            stringBuilder.append(builderLine.substring(minXPos, maxXPos)).append("\n");
        }
        return stringBuilder.toString();
    }

    private int gray(int rgb) {
        int r = rgb & 0xff0000 >> 16;
        int g = rgb & 0x00ff00 >> 8;
        int b = rgb & 0x0000ff;
        return (r * 30 + g * 59 + b * 11 + 50) / 100;
    }

    private boolean grayCompare(int gray, int background, double ignoreRate) {
        return (double) Math.min(gray, background) / Math.max(gray, background) >= ignoreRate;
    }
}
public class DefaultLoginSolver implements LoginSolver {
    private final Mutex loginSolverLock = new Mutex();

    @Override
    public LoginSolverResult solve(LoginSolverContext ctx) {
        try {
            return loginSolverLock.withLock(ctx::start);
        } catch (Exception e) {
            return LoginSolverResult.failure(e);
        }
    }

    @Override
    public LoginSolverResult onSolvePicCaptcha(Bot bot, IoBuffer data) {
        File tempFile = createTempFile(".png");
        tempFile.deleteOnExit();

        try {
            tempFile.createNewFile();
            bot.getLogger().info("需要图片验证码登录, 验证码为 4 字母");
            try (FileChannel channel = new FileOutputStream(tempFile).getChannel()) {
                data.copyTo(channel);
            }
            bot.getLogger().info("将会显示字符图片. 若看不清字符图片, 请查看文件 " + tempFile.getAbsolutePath());
        } catch (Exception e) {
            bot.getLogger().info("无法写出验证码文件(" + e.getMessage() + "), 请尝试查看以上字符图片");
            return LoginSolverResult.failure(e);
        }

        try (InputStream inputStream = new FileInputStream(tempFile)) {
            Image img = ImageIO.read(inputStream);
            if (img == null) {
                bot.getLogger().info("无法创建字符图片. 请查看文件");
            } else {
                bot.getLogger().info(createCharImg(img));
            }
        } catch (Exception e) {
            bot.getLogger().info("无法创建字符图片(" + e.getMessage() + "), 请查看文件");
            return LoginSolverResult.failure(e);
        }

        bot.getLogger().info("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
        String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (input == null || input.isEmpty() || input.length() != 4) {
            bot.getLogger().info("验证码输入错误");
            return LoginSolverResult.failure(new IllegalArgumentException("验证码输入错误"));
        }

        bot.getLogger().info("正在提交[" + input + "]中...");
        return LoginSolverResult.success(input);
    }

    @Override
    public LoginSolverResult onSolveSliderCaptcha(Bot bot, String url) {
        bot.getLogger().info("需要滑动验证码");
        bot.getLogger().info("请在任意浏览器中打开以下链接并完成验证码. ");
        bot.getLogger().info("完成后请输入任意字符 ");
        bot.getLogger().info(url);

        String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (input == null) {
            bot.getLogger().info("验证码输入错误");
            return LoginSolverResult.failure(new IllegalArgumentException("验证码输入错误"));
        }

        bot.getLogger().info("正在提交中...");
        return LoginSolverResult.success(input);
    }

    @Override
    public LoginSolverResult onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        bot.getLogger().info("需要进行账户安全认证");
        bot.getLogger().info("该账户有[设备锁]/[不常用登陆地点]/[不常用设备登陆]的问题");
        bot.getLogger().info("完成以下账号认证即可成功登陆|理论本认证在mirai每个账户中最多出现1次");
        bot.getLogger().info("请将该链接在QQ浏览器中打开并完成认证, 成功后输入任意字符");
        bot.getLogger().info("这步操作将在后续的版本中优化");
        bot.getLogger().info(url);

        String input = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (input == null) {
            bot.getLogger().info("验证码输入错误");
            return LoginSolverResult.failure(new IllegalArgumentException("验证码输入错误"));
        }

        bot.getLogger().info("正在提交中...");
        return LoginSolverResult.success(input);
    }

  
    
    private int gray(int rgb) {
        int r = (rgb & 0xff0000) >> 16;
        int g = (rgb & 0x00ff00) >> 8;
        int b = (rgb & 0x0000ff);
        return (r * 30 + g * 59 + b * 11 + 50) / 100;
    }

    private boolean grayCompare(int g1, int g2) {
        return (double) Math.min(g1, g2) / Math.max(g1, g2) >= ignoreRate;
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
    private MiraiLogger.Supplier<MiraiLogger> botLoggerSupplier = __ -> new DefaultLogger("Bot(" + __.getUin() + ")");
    private MiraiLogger.Supplier<MiraiLogger> networkLoggerSupplier = __ -> new DefaultLogger("Network(" + __.getUin() + ")");
    private Function<Context, DeviceInfo> deviceInfo;
    private CoroutineContext parentCoroutineContext = EmptyCoroutineContext;
    private long heartbeatPeriodMillis = Duration.ofSeconds(60).toMillis();
    private long heartbeatTimeoutMillis = Duration.ofSeconds(2).toMillis();
    private long firstReconnectDelayMillis = Duration.ofSeconds(5).toMillis();
    private long reconnectPeriodMillis = Duration.ofSeconds(60).toMillis();
    private int reconnectionRetryTimes = 3;
    private LoginSolver loginSolver = new DefaultLoginSolver();

    public static final BotConfiguration Default = new BotConfiguration();

    @SuppressWarnings("unused")
    public void unaryPlus(FileBasedDeviceInfo fileBasedDeviceInfo) {
        this.deviceInfo = (filepath) -> FileBasedDeviceInfo.loadAsDeviceInfo(new File(filepath));
    }

    @SuppressWarnings("unused")
    public void unaryPlus(FileBasedDeviceInfo.ByDeviceDotJson byDeviceDotJson) {
        this.deviceInfo = (filepath) -> FileBasedDeviceInfo.loadAsDeviceInfo(new File("device.json"));
    }

    public void unaryPlus(_NoNetworkLog noNetworkLog) {
        this.networkLoggerSupplier = noNetworkLog::get;
    }

    public void _NoNetworkLog() {
        this.networkLoggerSupplier = _NoNetworkLog.supplier;
    }


    public static class _NoNetworkLog {
        public static final _NoNetworkLog INSTANCE = new _NoNetworkLog();
    }
}