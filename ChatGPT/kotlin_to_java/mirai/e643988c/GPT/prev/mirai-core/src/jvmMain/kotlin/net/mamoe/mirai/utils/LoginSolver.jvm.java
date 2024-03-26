
package net.mamoe.mirai.utils;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.withContext;
import kotlinx.io.core.ByteReadChannel;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.logger.MiraiLogger;
import net.mamoe.mirai.network.NoStandardInputForCaptchaException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@MiraiExperimentalAPI
public class LoginSolver {

    
    public abstract String onSolvePicCaptcha(Bot bot, byte[] data);

    public abstract String onSolveSliderCaptcha(Bot bot, String url);

    public abstract String onSolveUnsafeDeviceLoginVerify(Bot bot, String url);

    public static final LoginSolver Default = new DefaultLoginSolver(() -> {
        try {
            return System.console().readLine();
        } catch (Exception e) {
            throw new NoStandardInputForCaptchaException(null);
        }
    });

        public static Function1<Context, DeviceInfo> getFileBasedDeviceInfoSupplier(String filename) {
        return context -> new File(filename).loadAsDeviceInfo(json, context);
    }

    private static WritableByteChannel writeChannel(File file, CoroutineContext coroutineContext) throws IOException {
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    randomAccessFile.setLength(0); // truncate file
    FileChannel fileChannel = randomAccessFile.getChannel();
    return Channels.newChannel(fileChannel);
}

    private static Mutex loginSolverLock = new Mutex();

    private static String createCharImg(BufferedImage image, int outputWidth, double ignoreRate) {
        int newHeight = (int) (image.getHeight() * ((double) outputWidth / image.getWidth()));
        Image tmp = image.getScaledInstance(outputWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledImage = new BufferedImage(outputWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        Function2<Integer, Integer, Boolean> grayCompare = (g1, g2) ->
                Math.min(g1, g2) / (double) Math.max(g1, g2) >= ignoreRate;

        int background = gray(scaledImage.getRGB(0, 0));

        StringBuilder result = new StringBuilder();
        CopyOnWriteArrayList<StringBuilder> lines = new CopyOnWriteArrayList<>();

        int minXPos = outputWidth;
        int maxXPos = 0;

        for (int y = 0; y < scaledImage.getHeight(); y++) {
            StringBuilder builderLine = new StringBuilder();
            for (int x = 0; x < scaledImage.getWidth(); x++) {
                int gray = gray(scaledImage.getRGB(x, y));
                if (grayCompare.invoke(gray, background)) {
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
            if (!builderLine.toString().isBlank()) {
                lines.add(builderLine);
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
        int b = (rgb & 0x0000ff);
        return (r * 30 + g * 59 + b * 11 + 50) / 100;
    }
    private static boolean grayCompare(int g1, int g2) {
        return ((double) Math.min(g1, g2)) / Math.max(g1, g2) >= ignoreRate;
    }

}
    public abstract static class DefaultLoginSolver extends LoginSolver {
        private final LoginSolver delegate;

        public DefaultLoginSolver(SuspendFunction0<String> input) {
            if (WindowHelperJvm.isDesktopSupported()) {
                delegate = new SwingSolver();
            } else {
                delegate = new StandardCharImageLoginSolver(input);
            }
        }

        @Override
        public String onSolvePicCaptcha(Bot bot, byte[] data) {
            return delegate.onSolvePicCaptcha(bot, data);
        }

        @Override
        public String onSolveSliderCaptcha(Bot bot, String url) {
            return delegate.onSolveSliderCaptcha(bot, url);
        }

        @Override
        public String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
            return delegate.onSolveUnsafeDeviceLoginVerify(bot, url);
        }
    }

    @MiraiExperimentalAPI
    public static class StandardCharImageLoginSolver extends LoginSolver {
        private final SuspendFunction0<String> input;
        private final MiraiLogger overrideLogger;
        private static final Lock loginSolverLock = new ReentrantLock();

        public StandardCharImageLoginSolver(SuspendFunction0<String> input, MiraiLogger overrideLogger) {
            this.input = input;
            this.overrideLogger = overrideLogger;
        }

        @Override
        public String onSolvePicCaptcha(Bot bot, byte[] data) {
            loginSolverLock.lock();
            try {
                MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
                File tempFile = null;
                try {
                    tempFile = File.createTempFile("temp", ".png");
                    tempFile.deleteOnExit();
                    logger.info("需要图片验证码登录, 验证码为 4 字母");
                    try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                        raf.write(data);
                    } catch (IOException e) {
                        logger.info("无法写出验证码文件(" + e.getMessage() + "), 请尝试查看以上字符图片");
                    }
                    BufferedImage img = ImageIO.read(tempFile);
                    if (img == null) {
                        logger.info("无法创建字符图片. 请查看文件");
                    } else {
                        logger.info(createCharImg(img));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (tempFile != null) {
                        tempFile.delete();
                    }
                }
                logger.info("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
                String inputString = input.invoke();
                if (inputString != null && !inputString.isEmpty() && inputString.length() == 4) {
                    logger.info("正在提交[" + inputString + "]中...");
                    return inputString;
                }
            } finally {
                loginSolverLock.unlock();
            }
            return null;
        }

        @Override
        public String onSolveSliderCaptcha(Bot bot, String url) {
            loginSolverLock.lock();
            try {
                MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
                logger.info("需要滑动验证码");
                logger.info("请在任意浏览器中打开以下链接并完成验证码. ");
                logger.info("完成后请输入任意字符 ");
                logger.info(url);
                String inputString = input.invoke();
                if (inputString != null && !inputString.isEmpty()) {
                    logger.info("正在提交中...");
                    return inputString;
                }
            } finally {
                loginSolverLock.unlock();
            }
            return null;
        }

        @Override
        public String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
            loginSolverLock.lock();
            try {
                MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
                logger.info("需要进行账户安全认证");
                logger.info("该账户有[设备锁]/[不常用登录地点]/[不常用设备登录]的问题");
                logger.info("完成以下账号认证即可成功登录|理论本认证在mirai每个账户中最多出现1次");
                logger.info("请将该链接在QQ浏览器中打开并完成认证, 成功后输入任意字符");
                logger.info("这步操作将在后续的版本中优化");
                logger.info(url);
                String inputString = input.invoke();
                if (inputString != null && !inputString.isEmpty()) {
                    logger.info("正在提交中...");
                    return inputString;
                }
            } finally {
                loginSolverLock.unlock();
            }
            return null;
        }

    }

