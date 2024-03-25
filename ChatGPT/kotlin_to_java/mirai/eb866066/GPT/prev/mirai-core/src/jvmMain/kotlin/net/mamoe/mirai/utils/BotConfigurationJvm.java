package net.mamoe.mirai.utils;

import kotlinx.coroutines.CoroutineName;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.channels.ByteWriteChannel;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.io.use;
import kotlinx.coroutines.sync.Mutex;
import kotlinx.coroutines.sync.MutexKt;
import kotlinx.coroutines.sync.withLock;
import kotlinx.coroutines.withContext;
import kotlinx.io.core.IoBuffer;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.network.BotNetworkHandler;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

@SuppressWarnings({"rawtypes", "unchecked", "unused"})
public class BotConfigurationJvm {

    private static final Mutex loginSolverLock = MutexKt.Mutex();

    private static final double IGNORE_RATE = 0.95;

    private static final LoginSolver defaultLoginSolver = new DefaultLoginSolver();

    private static final Function<Bot, MiraiLogger> defaultBotLoggerSupplier = bot -> new DefaultLogger("Bot(" + bot.getUin() + ")");

    private static final Function<BotNetworkHandler, MiraiLogger> defaultNetworkLoggerSupplier = handler -> new DefaultLogger("Network(" + handler.getBot().getUin() + ")");

    private static final Function<BotNetworkHandler, MiraiLogger> noNetworkLoggerSupplier = handler -> new SilentLogger();


    public static ByteWriteChannel writeChannel(File file, CoroutineContext coroutineContext) throws IOException {
        CompletableFuture<ByteWriteChannel> future = CompletableFuture.supplyAsync(() -> {
            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                Channel<Byte> channel = GlobalScope.writer(CoroutineName.Companion.getCoroutineName("file-writer") + coroutineContext, true, 0, null, null, (Function) null) {
                    try {
                        getChannel().write(randomAccessFile.getChannel().toReadChannel().toByteArray());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            randomAccessFile.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
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
                result.append(line.substring(minXPos, maxXPos)).append("\n");
            }
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

    @SuppressWarnings("rawtypes")
    public static class DefaultLoginSolver extends LoginSolver {

        @Override
        public String onSolvePicCaptcha(Bot bot, IoBuffer data) {
            return withLock(loginSolverLock, () -> {
                File tempFile;
                try {
                    tempFile = File.createTempFile("", ".png");
                    tempFile.deleteOnExit();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    withContext(Dispatchers.IO, () -> {
                        try {
                            tempFile.createNewFile();
                            bot.getLogger().info("需要图片验证码登录, 验证码为 4 字母");
                            tempFile.writeChannel().apply { writeFully(data); close() }
                            bot.getLogger().info("将会显示字符图片. 若看不清字符图片, 请查看文件 " + tempFile.getAbsolutePath());
                        } catch (Exception e) {
                            bot.getLogger().info("无法写出验证码文件(" + e.getMessage() + "), 请尝试查看以上字符图片");
                        }

                        try (FileInputStream inputStream = new FileInputStream(tempFile)) {
                            BufferedImage img = ImageIO.read(inputStream);
                            if (img == null) {
                                bot.getLogger().info("无法创建字符图片. 请查看文件");
                            } else {
                                bot.getLogger().info(createCharImg(img));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    bot.getLogger().info("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
                    String input = System.console().readLine();
                    if (input != null && !input.isEmpty() && input.length() == 4) {
                        bot.getLogger().info("正在提交[" + input + "]中...");
                        return input;
                    } else {
                        return null;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public String onSolveSliderCaptcha(Bot bot, String url) {
            return withLock(loginSolverLock, () -> {
                bot.getLogger().info("需要滑动验证码");
                bot.getLogger().info("请在任意浏览器中打开以下链接并完成验证码. ");
                bot.getLogger().info("完成后请输入任意字符 ");
                bot.getLogger().info(url);
                System.console().readLine();
                bot.getLogger().info("正在提交中...");
                return null;
            });
        }

        @Override
        public String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
            return withLock(loginSolverLock, () -> {
                bot.getLogger().info("需要进行账户安全认证");
                bot.getLogger().info("该账户有[设备锁]/[不常用登陆地点]/[不常用设备登陆]的问题");
                bot.getLogger().info("完成以下账号认证即可成功登陆|理论本认证在mirai每个账户中最多出现1次");
                bot.getLogger().info("请将该链接在QQ浏览器中打开并完成认证, 成功后输入任意字符");
                bot.getLogger().info("这步操作将在后续的版本中优化");
                bot.getLogger().info(url);
                System.console().readLine();
                bot.getLogger().info("正在提交中...");
                return null;
            });
        }
    }

    @SuppressWarnings("rawtypes")
    public static class BotConfiguration {
        private Function<Bot, MiraiLogger> botLoggerSupplier = defaultBotLoggerSupplier;
        private Function<BotNetworkHandler, MiraiLogger> networkLoggerSupplier = defaultNetworkLoggerSupplier;
        private Function<Context, DeviceInfo> deviceInfo;
        private CoroutineContext parentCoroutineContext = EmptyCoroutineContext.INSTANCE;
        private long heartbeatPeriodMillis = 60 * 1000;
        private long heartbeatTimeoutMillis = 2 * 1000;
        private long firstReconnectDelayMillis = 5 * 1000;
        private long reconnectPeriodMillis = 60 * 1000;
        private int reconnectionRetryTimes = 3;
        private LoginSolver loginSolver = defaultLoginSolver;

        public BotConfiguration() {
        }

        public void unaryPlus(FileBasedDeviceInfo fileBasedDeviceInfo) {
            this.deviceInfo = (context) -> new File(fileBasedDeviceInfo.filepath).loadAsDeviceInfo(context);
        }

        public void unaryPlus(FileBasedDeviceInfo.ByDeviceDotJson fileBasedDeviceInfo) {
            this.deviceInfo = (context) -> new File("device.json").loadAsDeviceInfo(context);
        }

        public void unaryPlus(_NoNetworkLog noNetworkLog) {
            this.networkLoggerSupplier = noNetworkLoggerSupplier;
        }

        public static final BotConfigurationJvm Default = new BotConfigurationJvm();
    }

    public static final class FileBasedDeviceInfo {
        public final String filepath;

        public FileBasedDeviceInfo(String filepath) {
            this.filepath = filepath;
        }

        public static final class ByDeviceDotJson {
        }
    }


}
